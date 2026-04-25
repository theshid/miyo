# Adding Sentry without breaking the rest of the app

Date: 2026-04-25

A side-project ships, real users start using it, and someone tells you in
passing: "I tapped the Anime tab and it was empty." You open the code, can't
reproduce it locally, and realize you have no record that anything went
wrong. The user has moved on. The bug hasn't.

This is the write-up of wiring Sentry into Miyo so those reports stop
disappearing. The interesting part wasn't the SDK — that's a five-minute
job — it was the *anti-patterns the SDK exposed* in the existing code, and
the design decisions about how loud to be once it was running.

---

## Part 1 — The silent `emptyList()` problem

Before Sentry: the `AnilistRepositoryImpl` was full of code shaped like
this.

```kotlin
override suspend fun getRecommendations(): List<Media> {
    val response = api.execute(AnilistQueries.recommendations()) ?: return emptyList()
    val recs = response["data"]?.jsonObject?.get("Page")?.jsonObject
        ?.get("recommendations")?.jsonArray ?: return emptyList()
    // …
}
```

And `api.execute()` itself swallows every exception:

```kotlin
} catch (e: Exception) {
    Log.e(TAG, "execute() failed: ${e.message}", e)
    null
}
```

So when AniList times out, returns 502, hits an OAuth-expired token, or
even just replies with something the JSON parser can't handle, the user
sees an empty section. The error becomes a logcat line that nobody reads.
Multiply that by every `?: return emptyList()` in the repo and you have a
codebase that *fails open* — failures look indistinguishable from
"there's just nothing here."

This pattern is fine for *defensive programming* in the small. As a
philosophy, it's a disaster, because:

- Users see blank screens, you see nothing.
- The error never propagates to a place where someone could decide to
  retry, log out, or show a banner.
- Crash dashboards stay clean. You feel good. You aren't.

Sentry's job, in this codebase, was less about catching crashes (those
are already terminal — the user notices) and more about making *handled*
failures visible. The 502 wasn't a crash. It was a 502 that turned into
an empty list that turned into a confused user.

## Part 2 — Wiring the SDK (the five minutes)

The Gradle plugin is genuinely one line in `build.gradle.kts`:

```kotlin
plugins {
    id("io.sentry.android.gradle") version "6.5.0"
}

dependencies {
    implementation("io.sentry:sentry-android:7.18.1")
}

sentry {
    org.set("shidji-inc")
    projectName.set("android")
    includeSourceContext.set(true)
}
```

The plugin handles ProGuard-mapping uploads at build time, which matters a
lot for a release build with `isMinifyEnabled = true` — without it, every
class in your stack trace is `a.b.c.d` and triage becomes archaeology.
`includeSourceContext = true` ships the source files alongside the
mappings so the dashboard shows the actual code lines around each frame.

DSN goes into the manifest, not into source code:

```xml
<meta-data android:name="io.sentry.dsn"
    android:value="https://…@…ingest.de.sentry.io/…" />
<meta-data android:name="io.sentry.traces.sample-rate" android:value="0" />
<meta-data android:name="io.sentry.send-default-pii" android:value="false" />
```

`traces.sample-rate=0` disables Sentry's performance/transaction tracking
because we don't want that signal yet — and every transaction is an event,
which is the thing the free tier rate-limits.

That's it. With those three changes, every uncaught exception in the app
becomes a Sentry event with a stack trace, device model, OS version, and
app version attached. Plus ANRs (5-second main-thread freezes) via the
SDK's built-in integration.

The wizard's "verify it works" snippet looks innocent:

```kotlin
findViewById<View>(android.R.id.content)
    .viewTreeObserver
    .addOnGlobalLayoutListener {
        try { throw Exception("This app uses Sentry! :)") }
        catch (e: Exception) { Sentry.captureException(e) }
    }
```

But `OnGlobalLayoutListener` fires on *every layout pass*. Scrolling a
list, opening a dialog, animating anything — they all trigger it.
Realistically that's hundreds of events per session. Delete it the moment
you've confirmed the dashboard works.

## Part 3 — Breadcrumbs, or "what was the app doing before this?"

A stack trace tells you where the crash happened. Breadcrumbs tell you
*how the user got there*. Sentry attaches the most recent ~100
breadcrumbs to every event automatically — but only if you give it
breadcrumbs to attach.

Miyo already had a logging library — PrettyLog, extracted earlier in the
project — and crucially it exposes a small interface:

```kotlin
interface LoggingService {
    fun log(message: String, tag: String?, level: LogLevel, error: Throwable?)
}
```

Plus an `init` overload that accepts a custom `LoggingService`, which
overrides the built-in pretty/default services. That's the seam — wrap
the real service so each log call also lands as a Sentry breadcrumb:

```kotlin
private class SentryBreadcrumbLoggingService(
    private val delegate: LoggingService,
) : LoggingService {
    override fun log(message: String, tag: String?, level: LogLevel, error: Throwable?) {
        delegate.log(message, tag, level, error)

        // Skip Debug — too chatty for breadcrumbs.
        if (level == LogLevel.Debug) return

        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.level = when (level) {
                LogLevel.Debug    -> SentryLevel.DEBUG
                LogLevel.Info     -> SentryLevel.INFO
                LogLevel.Warning  -> SentryLevel.WARNING
                LogLevel.Error    -> SentryLevel.ERROR
                LogLevel.Critical -> SentryLevel.FATAL
            }
            this.category = tag ?: "log"
            this.message = message
            if (error != null) setData("throwable", error.toString())
        })
    }
}
```

Wired in `Application.onCreate`:

```kotlin
PrettyLog.init(
    isDebug = BuildConfig.DEBUG,
    defaultTag = "Miyo",
    custom = SentryBreadcrumbLoggingService(baseService),
)
```

The result: every existing `Log.i`/`Log.w`/`Log.e` call across the
codebase is now also a Sentry breadcrumb. Zero code changes outside the
adapter. When a crash arrives in the dashboard, it carries 100 lines of
context — "what HTTP calls fired, what the parser found, what error the
repo logged" — without anyone having to instrument a single feature.

This is the highest-leverage piece of Sentry integration in the whole
project. **A logging facade with an interface seam is a load-bearing
abstraction.** If you don't have one, install one before you reach for
breadcrumb-by-breadcrumb instrumentation.

## Part 4 — Capturing what doesn't crash

Crashes are easy. The hard cases in Miyo are *handled* errors:

- `MangaReaderViewModel` shows "Chapter not found on any source" — the
  user is staring at an error screen, but no exception was thrown.
- `VideoPlayerViewModel` shows "Episode not found" — same shape.
- `AnimeScreen` renders empty because every AniList call returned
  `emptyList()` after some upstream failure.

Each of these needed an explicit `Sentry.captureMessage` (or
`captureException`) call at the *exact terminal failure point* — the
moment the UI gives up and shows nothing useful.

The pattern that emerged is a tiny helper per ViewModel:

```kotlin
private fun reportReaderError(message: String, chapter: Int?) {
    Sentry.withScope { scope ->
        scope.level = SentryLevel.WARNING
        scope.setTag("area", "MangaReader")
        scope.setTag("mediaId", mediaId.toString())
        if (chapter != null) scope.setTag("chapter", chapter.toString())
        scope.setTag("activeParser", activeParser)
        scope.setExtra("title", _uiState.value.title)
        Sentry.captureMessage(message)
    }
}
```

Two things matter here.

**Tags vs extras.** Tags are indexed and filterable on the dashboard;
extras are free-form context shown on the event page. Use tags for things
you'll want to *filter and group* by — `area`, `mediaId`, `parser`. Use
extras for narrative — the manga title, the failed query, the user's
selected source. Get this wrong and your dashboard turns into a wall of
ungrouped events.

**Group at the right granularity.** Sentry groups events by fingerprint
(roughly: stack trace + message). If you `captureMessage("Chapter ${n}
not found")` with `n` interpolated, every chapter number becomes a new
issue. Strip the variable parts:

```kotlin
reportReaderError("Chapter not found on any source", chapter = chapterNum)
//              ^^^ stable message, chapter goes into a tag
```

Now the dashboard shows one issue with a count, not 200 issues with one
event each.

## Part 5 — The "tab is empty" heuristic

The user-reported bug — "anime tab was empty, no error shown" — is a
case where no single exception captures the failure. AniList's API
returned a 200 with an empty page (or threw a SocketTimeout that the
repo caught and turned into `emptyList()`), and the screen rendered
nothing.

The signal you can derive without changing every callsite is:

> AniList always has trending/popular/recently-updated anime. Three
> empty lists is a signal something went wrong, even if no exception
> escaped.

In `AnimeViewModel`:

```kotlin
val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()
if (allEmpty && networkFailure == null) {
    reportEmptyAnimeTab()
}
```

This is a heuristic. A user with a fresh AniList account and no list
entries could *legitimately* hit this. That's fine — the captured event
arrives in Sentry as a Warning, you look once, you tune the heuristic
or add an exception for new-user state. The cost of the false positive
is "one event you ignore"; the benefit of the true positive is "you
discovered a silent failure your users couldn't articulate."

For network-layer failures specifically, the heuristic graduated to a
real signal once `AnilistApi` started exposing a `lastFailure: StateFlow`
of its retry-exhausted errors. Then the VM can distinguish:

- All empty + `lastFailure != null` → real network error → show the user
  a banner *and* report.
- All empty + `lastFailure == null` → ambiguous → log a warning, no UI.

But that's the next article.

## Part 6 — Quota and the noise problem

Sentry's free tier is 5,000 events per month. That's plenty for a
hobby app — until you wire `Sentry.captureException(e)` into every
`AnilistApi.execute` failure and a user opens the app on the subway
with bad reception. Every API call fails. Every failure fires an event.
You burn through the month's quota in one session.

A few patterns help.

**Retry first, capture last.** With retry inside `execute()`, transient
flakes never reach the capture path. Only persistent failures (3
attempts in a row across ~2 seconds of backoff) become events. This
alone cuts noise dramatically.

**Distinguish events from issues.** Sentry's grouping means 1,000 of
the same socket timeout become *one* issue with a count of 1,000. The
issue page is fine. The 1,000 against your event quota is not. If a
specific failure dominates your dashboard, add explicit fingerprinting
or sampling at the call site.

**Don't capture from per-frame loops.** The wizard's
`OnGlobalLayoutListener` example is the canonical disaster. Anything
that runs on every layout pass, every recomposition, every scroll
delta, every `LaunchedEffect(key)` where `key` changes often — is a
quota fire.

**Default to Warning, escalate to Error.** A handled failure that the
app recovered from is `WARNING`. An unhandled exception is `ERROR`. A
crash is `FATAL`. Sentry's filters and notification rules use this
directly, so getting it right means your phone alerts on real
problems and stays quiet on the recoverable ones.

## What this all bought

After about four hours of work, the dashboard now shows:

- Every uncaught exception, with mapping and source context.
- Every handled failure at the five terminal-error points users actually
  see (manga/chapter/pages not found, anime/episode not found).
- Every AniList API failure that survived three retries, tagged with the
  GraphQL operation name.
- Every source parser failure (Gogo, MangaDex, MangaPill), tagged with
  the method (`search`, `getEpisodes`, `getPages`) and the input.
- A "tab rendered empty" warning for the case the user reported.

The next time a real user says "the anime tab was empty," I can search
the dashboard by the time they reported it and see exactly what failed.
That's the whole point.

The next-best thing would have been a complete refactor of the
repository to return `Result<T>` so failures propagate up to the
ViewModel cleanly, and the ViewModel then captures + shows the user a
banner. That's a multi-day refactor across 30+ callsites. The
heuristic-plus-side-channel approach above ships in an afternoon and
catches >90% of the same problems.

## Lessons

1. **Logging libraries with interface seams pay off.** Once you can
   adapt every log call into a breadcrumb without touching feature
   code, you've installed a permanent observability win.

2. **Anti-patterns hide in `?: return emptyList()`.** Every silent
   fallback is a missed signal. Sentry can't fix the pattern, but
   making the silence visible at *one* layer (the API client's
   `lastFailure`) is enough to surface most of them.

3. **Tag what you filter, extra what you read.** Get this right and
   your dashboard organizes itself.

4. **Quota is the real constraint.** Retries, grouping, and sampling
   are the levers. Don't treat capture calls as free.

5. **Ship the wizard's test crash, then delete it.** It's a layout
   listener. It fires constantly. It will eat your quota.

The next layer up — *what to do once you know things are failing* —
is the user-facing side of this work. Retries, fallback messaging, and
not leaving people staring at a blank screen. That's covered separately.
