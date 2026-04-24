# Making episode airing notifications fire while the app is closed

Date: 2026-04-23

A debugging session that started with a one-line user report — *"the
notification only shows when I open the app"* — and ended in a small but
load-bearing rewrite of the notification stack: per-airing alarms, a
default-disabled boot receiver toggled at runtime, and a list-change
subscription that re-schedules immediately. This is a write-up of how the
old polling approach failed, why a competing client (AL-chan) felt
instant by comparison, and how we landed on a hybrid that's better than
either.

---

## The user report

> "When watching anime full screen and rotating, when the anime ends and
> I press the button to get to the next episode, it works fine but then
> the status bar is visible when playing the next episode."

That was an immersive-mode race fix (a one-liner with `view.post`) — not
this article. The other report was the more interesting one:

> "When we add an anime to AniList and are supposed to get notification
> on the next episode airing, on the day of airing the notification only
> showed when I opened the app — while I have another AniList client
> that showed the notification for the same anime and I didn't need to
> open that app for the notification to show."

Two devices, same AniList account, same show, same airing time. One app
notifies on time without being opened. Ours doesn't notify until the user
brings it to the foreground. That's a "your background work is not
running" bug, not a "your code is wrong" bug.

---

## What we had before

A `WorkManager` periodic task plus a one-shot kick on app start:

```kotlin
// MiyoApplication.kt
val periodicRequest = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
    15, TimeUnit.MINUTES,
).setConstraints(constraints).build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "episode_check",
    ExistingPeriodicWorkPolicy.KEEP,
    periodicRequest,
)
```

The worker fetches the user's CURRENT list, compares each show's
`nextAiringEpisode` against a SharedPreferences-tracked baseline, and
posts a notification when AniList reports a higher episode number.

The architecture is correct in principle. The problem is that
`WorkManager` periodic work is a *suggestion* to the OS, not a contract.
On modern Android (8+), the platform aggressively defers background work
under three regimes:

- **Doze mode**. After ~30 min unplugged with the screen off, the device
  enters Doze. Periodic work is batched into "maintenance windows" that
  occur every 1–4 hours — not every 15 minutes.
- **App standby buckets** (Android 9+). If the user rarely opens the app,
  it gets bucketed as `WORKING_SET`, `FREQUENT`, `RARE`, or `RESTRICTED`,
  with progressively harsher background-work throttling. `RARE` apps get
  one job every ~24 hours. `RESTRICTED` essentially gets nothing.
- **OEM battery killers**. Xiaomi's MIUI, Samsung's One UI, Huawei's
  EMUI, Oppo's ColorOS, and OnePlus's OxygenOS all ship aggressive
  out-of-the-box behaviours that simply stop scheduled work for apps
  the user hasn't opened recently. There's a website,
  [dontkillmyapp.com](https://dontkillmyapp.com), entirely dedicated to
  documenting how each manufacturer breaks WorkManager.

Net result: the *only* time the periodic worker reliably runs is when
the app is in the foreground. When the user reopens the app, the
one-shot `episode_check_now` worker fires immediately and sees all the
new episodes that aired in the meantime — which produces exactly the
symptom they reported.

---

## How AL-chan does it

Before designing a fix, we read the source of
[AL-chan](https://github.com/zend10/AL-chan), the AniList client the
user mentioned.

```
hourly setInexactRepeating(RTC_WAKEUP, ..., INTERVAL_HOUR)
       ↓
PushNotificationBroadcastReceiver  (also listens for BOOT_COMPLETED)
       ↓
PushNotificationWorker (WorkManager, network constraint)
       ↓
hits AniList's GraphQL Notification feed → shows whatever's new
```

Three things stood out:

1. **They use `AlarmManager`, not `WorkManager`, as the trigger.**
   `AlarmManager.setInexactRepeating(RTC_WAKEUP, ...)` wakes the device
   even from Doze (with ±9 min jitter) — fundamentally different from
   `WorkManager` periodic, which respects Doze.

2. **They poll AniList's notification feed, not media airing times.**
   AniList itself decides what's an "airing notification" via its GraphQL
   `Notification` query. AL-chan never looks at `nextAiringEpisode` directly —
   they let the server compute it.

3. **The boot receiver is default-disabled and dynamically toggled** via
   `PackageManager.setComponentEnabledSetting(...)`. It only listens for
   `BOOT_COMPLETED` when the user actually has notifications turned on.

The first two are architectural choices. The third is operational
hygiene. We adopted #1 and #3 (with a twist), and deliberately diverged
from #2.

---

## The fix, in three pieces

### Piece 1 — `EpisodeAlarmScheduler`: per-airing alarms

We don't want hourly polls. AniList already tells us *exactly when* each
episode is supposed to air via `nextAiringEpisode.airingAt`. Why poll
once an hour when you can fire one alarm at the right time?

```kotlin
class EpisodeAlarmScheduler(private val context: Context) {

    fun rescheduleAll(airings: List<Airing>) {
        // 1. Cancel everything we scheduled last time.
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
        for (mediaId in previouslyScheduled) cancelAlarmFor(mediaId)

        // 2. Schedule fresh alarms for every future airing.
        val now = System.currentTimeMillis()
        val newlyScheduled = mutableSetOf<Int>()
        for (airing in airings) {
            val fireAt = airing.airingTimeMs + AIRING_BUFFER_MS  // +2 min slack
            if (fireAt <= now) continue
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    fireAt,
                    buildPendingIntent(airing),
                )
                newlyScheduled.add(airing.mediaId)
            } catch (e: SecurityException) { /* logged */ }
        }

        // 3. Persist the new ID set so next reschedule can clean up properly.
        prefs.edit { putStringSet(KEY_SCHEDULED_IDS, newlyScheduled.map { it.toString() }.toSet()) }

        // 4. Toggle the boot receiver to match.
        setBootReceiverEnabled(newlyScheduled.isNotEmpty())
    }
}
```

Three design decisions worth unpacking:

**Cancel-all-then-reschedule, not diff.** Computing what changed between
two airing lists (added shows, removed shows, shifted times) is a bug
factory. Brute-force cancellation is idempotent: feed it the same list
twice, you end up in the same state. Storage cost is one
`SharedPreferences.StringSet` keyed by media ID. Good trade.

**`setAndAllowWhileIdle`, not `setExactAndAllowWhileIdle`.** Exact
alarms require the `SCHEDULE_EXACT_ALARM` permission on Android 12+,
which on 12 is a runtime grant (annoying UX) and on 13+ requires the
`USE_EXACT_ALARM` declaration plus app-store category review (we're not
a calendar/alarm app). The inexact variant has ±9 min jitter in Doze —
which is *fine* for our use case, because AniList's stated airing times
themselves slip by 5–30 minutes (the AniList "airingAt" is when the
episode airs in Japan; streaming sites take a bit to publish). A
9-minute alignment window is invisible against that.

**`+2 min` buffer past the stated airing time.** When the alarm fires
we want to be confident the episode is actually available, not just
broadcast in Japan. Two minutes is enough slack to avoid "your
notification arrived before the episode is up" without being so much
that users feel the lag.

### Piece 2 — `EpisodeAiringReceiver`: the one-shot notifier

The `BroadcastReceiver` that the alarm targets needs to do exactly two
things: load the cover image and post the notification. No AniList round
trip — we trust the title/episode/cover that were stored in the
PendingIntent extras at schedule time.

```kotlin
class EpisodeAiringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ...read extras...
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val cover = loadCoverBitmap(context, coverUrl)
                EpisodeNotificationChannel.showEpisodeNotification(...)
            } finally {
                pending.finish()
            }
        }
    }
}
```

The `goAsync()` pattern is the right move here. By default, a
`BroadcastReceiver`'s `onReceive` must complete in ~10 seconds *and*
isn't allowed to do blocking work (the process can be killed when
`onReceive` returns). `goAsync()` returns a `PendingResult` that keeps
the receiver alive for up to 10 more seconds while you do IO on a
background thread. Don't forget to call `pending.finish()` — leaking
that token is a slow ANR.

We deliberately don't re-verify with AniList in the receiver. If
AniList's airing time was wrong, worst case we notify a few minutes
early. That's a much better failure mode than "the receiver tried to
make a network call that took 12 seconds because the user is on bad
wifi, and Android killed the broadcast before the notification posted."

### Piece 3 — `BootCompletedReceiver`: re-seeding after reboot

`AlarmManager` alarms are wiped on reboot. They're not persisted across
power cycles by the OS. So we declare a `BOOT_COMPLETED` receiver that
re-enqueues the worker, which in turn calls `rescheduleAll(...)`:

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "episode_check_boot",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build(),
        )
    }
}
```

We don't reschedule alarms inline in the receiver because that needs
network (to fetch the user's current list) and a `BroadcastReceiver` is
not the place for network calls. Defer to WorkManager, which will run
the worker as soon as the network constraint is met.

---

## The default-disabled boot receiver pattern

This is the trick we lifted from AL-chan. Manifest:

```xml
<receiver
    android:name=".notifications.BootCompletedReceiver"
    android:enabled="false"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

Default-disabled. It only ever activates when our code explicitly
enables it:

```kotlin
private fun setBootReceiverEnabled(enabled: Boolean) {
    val component = ComponentName(context, BootCompletedReceiver::class.java)
    val desired = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    val current = context.packageManager.getComponentEnabledSetting(component)
    if (current != desired) {
        context.packageManager.setComponentEnabledSetting(
            component,
            desired,
            PackageManager.DONT_KILL_APP,
        )
    }
}
```

We call it at the end of `rescheduleAll(...)` with
`newlyScheduled.isNotEmpty()`. Net effect:

- User is logged out → `rescheduleAll(emptyList())` → boot receiver
  off. Android won't launch the app on every reboot.
- User has zero upcoming airings → boot receiver off.
- User has at least one upcoming airing → boot receiver on.

**Why this matters.** A `BOOT_COMPLETED` receiver registered statically
in the manifest causes Android to launch the app's process on every
device boot, regardless of whether your code does anything useful. That
adds up across hundreds of installed apps and is one of the things the
OS actively tries to defend against. Toggling the component dynamically
is the polite version: we only ask to be woken on boot when we actually
have work to do.

The `current != desired` check is a small but important detail.
`setComponentEnabledSetting` writes to package-manager state and persists
across boots; calling it on every reschedule when nothing changed is
unnecessary IPC. Reading first is cheap and lets us no-op the common case.

---

## List events trigger an immediate reschedule

Without a way to react to the user adding a show, you'd have to wait
for the next 15-min `WorkManager` tick to pick up the new airing alarm.
We already have a `ListEventBus` that emits `ListEntryChanged` whenever
the user adds, removes, or changes the status of a list entry from
*any* screen — detail, lists, etc. So we just subscribe at app level:

```kotlin
class MiyoApplication : Application() {
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // ...existing init...
        observeListChangesForAlarmReschedule()
    }

    private fun observeListChangesForAlarmReschedule() {
        appScope.launch {
            ListEventBus.events.collect { event ->
                if (event is ListEvent.ListEntryChanged) {
                    val request = OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build()
                    WorkManager.getInstance(this@MiyoApplication).enqueueUniqueWork(
                        "episode_check_list_change",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
            }
        }
    }
}
```

A few notes on this:

- **App-scoped, not Activity-scoped.** This subscription needs to stay
  alive across all screen navigations and even across the app being
  backgrounded. An Application-scoped `CoroutineScope` with a
  `SupervisorJob` is the right home — it lives for the lifetime of the
  process.
- **`SupervisorJob` matters.** Without it, an exception in any child
  coroutine cancels the entire scope. We don't want a transient crash
  in the event collector to kill all our background coroutines.
- **`ExistingWorkPolicy.REPLACE`.** If the user adds three shows in
  quick succession, we don't want three workers queued behind each
  other. `REPLACE` cancels the in-flight one and starts a fresh one
  with the latest data. Idempotent, debounced for free.
- **Fires regardless of whether the change adds a future airing.**
  Removing a show fires `ListEntryChanged` too, which is exactly what
  we want — the worker re-runs and `rescheduleAll(...)` cancels the
  alarm for the removed show.

---

## Logout handling, the easy way

Originally the worker bailed out early when not logged in:

```kotlin
if (!repository.isLoggedIn()) {
    Log.d(tag = TAG, message = "Not logged in — skipping check")
    return@withContext Result.success()
}
```

That left orphan alarms scheduled from the previous session, and the
boot receiver enabled. The fix is one line — call
`rescheduleAll(emptyList())` before bailing:

```kotlin
if (!repository.isLoggedIn()) {
    EpisodeAlarmScheduler(applicationContext).rescheduleAll(emptyList())
    Log.d(tag = TAG, message = "Not logged in — cleared alarms, skipping check")
    return@withContext Result.success()
}
```

`rescheduleAll(emptyList())` cancels every previously scheduled alarm
*and* (because `newlyScheduled.isEmpty()`) flips the boot receiver off.
One method, two side effects — and they're both correct, because the
method's contract is "make the world reflect this list of airings", and
an empty list means "no airings, please".

This is the kind of API design that pays off: when your destructor and
your constructor are the same call with different arguments, callers
can't get them out of sync.

---

## What we did differently from AL-chan

| Dimension                | AL-chan                                              | Miyo                                                  |
|--------------------------|------------------------------------------------------|-------------------------------------------------------|
| Trigger                  | One repeating hourly alarm                           | One alarm per upcoming airing                         |
| Source of truth          | AniList's notification feed                          | Per-show `nextAiringEpisodeTime` cached locally       |
| Timing precision         | Up to 60 min late                                    | Within ~9 min of stated airing                        |
| Notification scope       | Airing + follows + replies + activity + forum        | Airing only                                           |
| Network traffic          | One GraphQL call/hour, always                        | Zero between airings                                  |
| Boot receiver lifecycle  | Toggled per user preference                          | Toggled per "do we have airings scheduled"            |
| Permission model         | Inexact, `setInexactRepeating`                       | Inexact, `setAndAllowWhileIdle`                       |

**Where AL-chan wins.** Their approach delegates timing to the AniList
server. If a show's airing time slips by an hour at the last minute,
AL-chan picks it up on the next hourly poll. We rely on a locally
cached `airingAt`; if that's stale, the alarm fires at the wrong time.
Their approach also covers richer notification types (forum mentions,
follower activity) for free.

**Where Miyo wins.** Way better timing precision when the airing time
is right (which it usually is). Way less network: no hourly poll, no
load on the AniList API for users who aren't watching anything new.
And — importantly — far better behaviour on devices with aggressive
battery savers, because the worst case for a per-airing alarm is "fires
within ~9 min of the right time" while the worst case for hourly polling
is "fires within ~60 min of the next hour boundary, which may or may
not align with the airing".

If we ever want broader notification coverage (follows, replies), the
clean addition is to *also* subscribe to AniList's notification feed on
a low-frequency schedule — but keep the per-airing alarms for episode
notifications specifically. The two systems compose.

---

## The OEM battery-killer caveat

Even with all this, on devices running MIUI / One UI / ColorOS / EMUI
with default settings, the broadcast receiver may still be suppressed
when the app hasn't been opened recently. There is no Android API
that defeats this. Every workaround is OEM-specific and changes between
versions. The accepted industry fix is to detect aggressive
manufacturers and prompt the user to whitelist the app from "battery
optimization" — same UX you'll see in Discord, Slack, WhatsApp, and
every other app where notifications matter.

We haven't built that prompt yet. If users continue to report missing
notifications on specific devices, that's the next move:

1. Detect manufacturer via `Build.MANUFACTURER`.
2. Show a one-time dialog explaining the manufacturer's battery saver
   will block notifications, with a button that opens the relevant
   settings screen via `Intent.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
3. Track dismissal in `OnboardingPrefs` so we don't nag.

Cheap to add when we need it.

---

## Patterns worth remembering

A handful of takeaways that generalise beyond this feature:

**Prefer `AlarmManager` over `WorkManager` for time-anchored work.**
WorkManager periodic intervals are advisory; the OS will defer them
under Doze and standby buckets. `setAndAllowWhileIdle` wakes the device
within ±9 min of the requested time and is the right tool whenever you
need "fire roughly at this moment". Use WorkManager for "do this when
the constraints are eventually met" (network, charging, etc.).

**Cancel-all-then-reschedule for idempotent state machines.** If you
need to keep a set of OS-side resources (alarms, notifications, system
shortcuts) in sync with an app-side list, don't try to compute the
diff. Track what you scheduled in `SharedPreferences`, cancel it,
schedule the new set. The bug surface shrinks dramatically.

**Default-disable manifest receivers; toggle at runtime.** A
statically-registered `BOOT_COMPLETED` receiver wakes your app on
every boot whether you need it or not. `setComponentEnabledSetting` is
the polite, OEM-friendly alternative — and it composes naturally with
the cancel-all-then-reschedule pattern (the receiver enables when
there's something to do on boot, disables otherwise).

**Use `goAsync()` correctly.** Anywhere a `BroadcastReceiver` needs to
do work that touches IO, network, or even disk, `goAsync()` is the
right pattern. It buys you ~10 more seconds without ANR risk. Always
pair it with a `try { ... } finally { pending.finish() }` so a thrown
exception doesn't leak the broadcast token.

**App-scoped `CoroutineScope(SupervisorJob())` for cross-screen
event subscriptions.** When something needs to react to events
regardless of which screen the user is on, an Application-scoped scope
is the correct home. The `SupervisorJob` ensures one bad subscriber
doesn't cancel the others.

**For OS-side hooks, design your destructor as the constructor with
empty input.** `rescheduleAll(emptyList())` doing the cleanup work is
better than a separate `cancelAll()` method, because callers can't
forget to also disable the boot receiver, can't forget to clear the
StringSet, etc. One method, one set of side effects, indexed by input.

---

## Files touched

- `app/src/main/java/ani/saikou/notifications/EpisodeAlarmScheduler.kt` (new)
- `app/src/main/java/ani/saikou/notifications/EpisodeAiringReceiver.kt` (new)
- `app/src/main/java/ani/saikou/notifications/BootCompletedReceiver.kt` (new)
- `app/src/main/java/ani/saikou/notifications/EpisodeCheckWorker.kt`
  (rescheduleAll on every run, including logout path)
- `app/src/main/java/ani/saikou/MiyoApplication.kt`
  (subscribed to `ListEventBus` for immediate reschedule)
- `app/src/main/AndroidManifest.xml`
  (`RECEIVE_BOOT_COMPLETED` permission, both receivers registered;
  boot receiver is `enabled="false"` by default)
