# Building engagement on the Home screen

Date: 2026-04-19

A working session that started with a one-line preference bug and ended five
features deep — persistent reader settings, an event-driven activity calendar,
a buffering-state fix in the player, a click-to-expand airing schedule, and a
first-launch onboarding tour. This is a write-up of what we built, why each
change took the shape it did, and the gotchas worth remembering.

---

## 1. Reader settings that actually persist

### The bug

`MangaReaderScreen` was holding its `ReaderSettings` (reading mode,
background colour, "keep screen on", page-number toggle, double-page,
crop-borders) in plain Compose state:

```kotlin
var settings by remember { mutableStateOf(ReaderSettings()) }
```

Every time the user opened a chapter, the settings reverted to defaults. The
state was scoped to the composition, not to the user.

### The fix

We already had a precedent in `TokenStorage` — a thin SharedPreferences
wrapper. We added `ReaderSettingsStorage` in the same shape, then wired it
into the screen with one read on entry and one write per change:

```kotlin
val settingsStorage = remember { ReaderSettingsStorage(context) }
var settings by remember { mutableStateOf(settingsStorage.load()) }

LaunchedEffect(settings) {
    settingsStorage.save(settings)
}
```

Two things worth noting:

- `Color` doesn't serialize directly to SharedPreferences, so we round-trip
  through `Color.toArgb()` / `Color(intArgb)`. Cheap and correct.
- The `LaunchedEffect(settings)` save fires once on first composition (saving
  defaults) and then once per state change. No need for a separate "did the
  user touch this?" flag — `data class` equality means the effect is a no-op
  unless something actually changed.

### Lesson

Don't reach for DataStore until you've outgrown SharedPreferences. We had a
synchronous read path and a tiny payload — `prefs.edit { putInt(...) }` is
the right tool.

---

## 2. The activity heatmap

This was the bulk of the morning. The user wanted something like GitHub's
contribution graph but for reading and watching.

### Why the existing tables couldn't power it

Both `reading_history` and `watch_history` are keyed by media id with
`OnConflictStrategy.REPLACE`. They store **last state per item**, not events.
If you watch ten episodes of the same show in a day, you get one row with
today's timestamp. There's no way to derive "user did X actions on Tuesday"
from this schema.

So the first decision was: **add an event log**.

### `activity_events`: an append-only journal

```kotlin
@Entity(
    tableName = "activity_events",
    indices = [Index(value = ["timestampMs"])],
)
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val type: String, // "read" | "watch" | "session"
)
```

Three things to call out:

- **Index on `timestampMs`** — every read query is a range scan on the
  timestamp, so this is the one index that matters.
- **No FK to media id** — events are intentionally denormalised. Media can be
  deleted from history; activity should still count.
- **Type as a string** — keeps the schema open. We added `"session"` later
  without a migration.

### Migration v4 → v5

```kotlin
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS activity_events (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampMs INTEGER NOT NULL,
                type TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_events_timestampMs ON activity_events(timestampMs)")
    }
}
```

The schema names must match exactly what Room generates — `index_<table>_<col>`
is Room's convention. Get that wrong and Room throws on first read.

### When to log

Three log points, each chosen to match user intent rather than incidental
state changes:

| Source                 | When                                                            |
|------------------------|-----------------------------------------------------------------|
| `MangaReaderViewModel` | Once per chapter open, after pages successfully load            |
| `VideoPlayerViewModel` | Once per session, on the first `saveLocalProgress` call         |
| `MiyoApplication`      | Once per calendar day on cold start, guarded by a count query   |

The session log is the interesting one. We don't want every cold start to
spam the table, but we do want a "you opened the app today" signal so that
days where the user just browses still light up the heatmap. The guard:

```kotlin
if (dao.countByTypeSince("session", startOfDayMs) == 0) {
    dao.insert(ActivityEventEntity(
        timestampMs = System.currentTimeMillis(),
        type = "session",
    ))
}
```

One query, one optional insert, on a `Dispatchers.IO` scope from
`Application.onCreate`.

### Calendar view vs rolling 30 days

The first iteration was a rolling 30-day window, GitHub-style: rows = days
of the week, columns = weeks ending today on the right. The user pushed
back: "if today is the 18th of April, the colored cell should be around the
middle of the grid". Their mental model was a calendar, not a rolling window.

We rebuilt around the current month:

```kotlin
val firstOfMonth = today.withDayOfMonth(1)
val lastOfMonth = today.withDayOfMonth(today.lengthOfMonth())
val gridStart = firstOfMonth.with(DayOfWeek.MONDAY)
val gridEnd = lastOfMonth.with(DayOfWeek.SUNDAY)
val rows = (ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1) / 7
```

That gives a tidy 4–6 rows × 7 cols grid where each cell holds the day
number. Days from adjacent months render as transparent gaps; future days
in the current month render as a dim placeholder. Today gets a Primary-
coloured border. The user can read the grid as a calendar — exactly what
they wanted.

`HomeViewModel` was updated to fetch events from the start of the month
(rather than the rolling window) so the data range matches the view.

### Airing overlay

Each cell whose date matches a `nextAiringEpisodeTime` from the user's
CURRENT AniList list shows the show's cover **filling the cell**, with a
40 %-black scrim so the day number stays legible:

```kotlin
if (cover != null) {
    AsyncImage(
        model = cover,
        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop,
    )
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
}
```

Tapping a cell with airings opens a `DropdownMenu` anchored to the cell
listing every show airing that day, with cover thumbnail, title, episode
number, and airing time (with a `Schedule` icon next to it). Tap a row and
you're taken to the show's detail screen.

**Caveat**: AniList's GraphQL exposes only `nextAiringEpisode` per show — so
in any given month we can only mark *one* upcoming airing per anime. Full
weekly extrapolation would either need client-side projection (next + 7d, +
14d, …) or a separate query. We left it as a known limitation.

---

## 3. The player's "stuck on Play" icon

### The bug

When the user tapped the play button on a fresh episode, the icon would
sometimes stay on `PlayArrow` while the video buffered the first chunks.
`isBuffering` correctly swapped the icon for a spinner during active
buffering, but there was a window — playWhenReady=true, playbackState=READY,
isPlaying=false — where neither was active and the icon read "Play" even
though the user had already chosen to play.

### The fix

ExoPlayer separates **intent** from **state**:

- `playWhenReady` — the user wants playback (set by `play()` / `pause()`)
- `isPlaying` — the player is actually rendering frames
- `playbackState` — buffering, ready, ended, idle

The icon should reflect **intent**, not state. We added a `playWhenReady`
state mirror:

```kotlin
override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {
    playWhenReady = value
}
```

…and used it both for the icon and the toggle:

```kotlin
onClick = {
    if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
},
// …
Icon(
    if (playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
    contentDescription = if (playWhenReady) "Pause" else "Play",
    // …
)
```

The buffering spinner still wins (it shows whenever `isBuffering` is true),
so the visual hierarchy is: spinner → Pause → Play, in priority order.

### Lesson

For media-control UI, prefer the player's **intent flag** over its
**actual playback state**. State lags intent during buffering, seeks, and
track changes; intent is what the user clicked.

---

## 4. The first-launch tour

The user wanted a guided tour for the Home screen — a spotlight on each
section with a tooltip card explaining what it does.

### Storage

A second SharedPreferences wrapper, `OnboardingPrefs`, with three methods:
`hasSeenHomeTour()`, `markHomeTourSeen()`, `resetHomeTour()`. The reset
method is a hook for future "Show tutorial again" settings entry.

### Architecture

The tour is a **separate composable layer** rendered as a sibling of the
home content inside a wrapping `Box`:

```
Box(fillMaxSize) {
    Column(verticalScroll) {
        // sections, each annotated with .tourTarget(state, TARGET)
    }

    if (showTour) TourOverlay(steps, state, currentStep, …)
}
```

Each tour-able section gets a tiny `Modifier.tourTarget(state, target)`
that captures its bounds via `onGloballyPositioned`. The overlay reads
those bounds, dims everything outside the highlighted rectangle, draws a
Primary-coloured border around it, and shows a tooltip card at the top or
bottom of the screen depending on where the target sits.

### The coordinate-space gotcha

The first version used `boundsInRoot()` directly, and the spotlight rendered
**below** every target — covering the bottom half of one section and the top
of the next. Took a couple of rounds with the user to diagnose:

`boundsInRoot()` returns coordinates in the **composition root's** space,
which on Android sits inside the system insets / app-bar / Scaffold padding.
The Canvas inside the overlay was drawing in its own local space. The two
spaces were offset by however much chrome sat above the home screen — about
a status-bar's worth.

The fix was to capture the overlay's own root position and subtract before
drawing:

```kotlin
var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
// …
.onGloballyPositioned { overlayOrigin = it.positionInRoot() }
// …
val targetBounds = state.bounds[step.target]?.translate(-overlayOrigin.x, -overlayOrigin.y)
```

Now both ends of the calculation live in the same coordinate system and
the spotlight sits exactly on the section's pixels.

### The other gotcha: lazy and reactive bounds

A tempting first cut was to store `LayoutCoordinates` in `TourState` and
recompute bounds via `localBoundingBoxOf(...)` inside the overlay. That
fails subtly: the `LayoutCoordinates` *object reference* doesn't change as
the user scrolls — only its position does. `remember(coords)` would never
recompute, and the spotlight would freeze.

Storing the resolved `Rect` via `boundsInRoot()` works because **Compose
re-fires `onGloballyPositioned` on every layout pass**, including scroll-
triggered ones. The `SnapshotStateMap<TourTarget, Rect>` then drives
recomposition naturally.

The takeaway: prefer storing the **value** (Rect) over the **handle**
(LayoutCoordinates) when you want reactive UI updates.

### Auto-scroll between steps

When the user advances, we scroll the highlighted section into view:

```kotlin
LaunchedEffect(tourStep, showTour, activeTourSteps) {
    val target = activeTourSteps.getOrNull(tourStep)?.target ?: return@LaunchedEffect
    var attempts = 0
    while (tourState.bounds[target] == null && attempts < 20) {
        delay(50)
        attempts++
    }
    val rect = tourState.bounds[target] ?: return@LaunchedEffect
    val deltaPx = rect.top - with(density) { 120.dp.toPx() }
    if (abs(deltaPx) > 4f) scrollState.animateScrollBy(deltaPx)
}
```

The retry loop is there because `tourState.bounds[target]` may not exist
yet for a section that's still off-screen at tour start — the
`onGloballyPositioned` callback only fires for sections that have been
laid out.

### Filtering for first-time users

A first-time user has no airing schedule, no continue-watching list, no
reading history. Showing them a spotlight on a section that doesn't exist
would be worse than not having a tour at all. We filter the steps based on
the actual state:

```kotlin
val activeTourSteps = remember(
    state.airingSchedule.size,
    state.continueWatchingLocal.size,
    state.continueWatching.size,
    state.readingHistory.size,
    state.continueReading.size,
) {
    DefaultHomeTourSteps.filter { step ->
        when (step.target) {
            TourTarget.AIRING -> state.airingSchedule.isNotEmpty()
            TourTarget.CONTINUE_WATCHING ->
                state.continueWatchingLocal.isNotEmpty() ||
                    state.continueWatching.isNotEmpty() ||
                    state.readingHistory.isNotEmpty() ||
                    state.continueReading.isNotEmpty()
            else -> true
        }
    }
}
```

A brand-new user gets a 3-step tour (Stats → Activity → Quick Actions). A
returning user with library data gets the full 5-step tour. The `currentStep`
index is `coerceAtMost(activeTourSteps.lastIndex)` so we can't crash by
indexing past the filtered list.

### Spotlight-area sizing

For the Quick Actions section we initially gave each of the four action
rows its own `tourTarget(ACTIONS)`, which meant the spotlight only covered
the row that registered last. Wrapping all four in a single `Column` with
the modifier on the wrapper gave us bounds that span the entire block. This
is the same trick used for Airing Soon and Continue Watching: the modifier
goes on the parent `Column` that contains both the `SectionHeader` and the
`LazyRow`, so the spotlight includes the title.

---

## Patterns worth remembering

A few transferable lessons from this morning's work:

**Append-only event tables for activity tracking.** When the existing
"latest state per item" table can't answer "what happened on day X", add a
journal table. Cheap to write, indexed on time, easy to roll up.

**Reactive coordinate capture in Compose.** Store `Rect`, not
`LayoutCoordinates`. Compose re-emits bounds on every layout pass, and
`SnapshotStateMap<K, Rect>` drives recomposition out of the box.

**Window vs root vs local coordinates.** Anything you draw that's anchored
to another composable's bounds needs both ends in the same coordinate
space. The simplest correction is to capture the drawing surface's root
position and subtract.

**Intent vs state for media UI.** ExoPlayer's `playWhenReady` is what the
user wanted. `isPlaying` is what's currently happening. Bind UI to intent,
not state, unless you specifically want state semantics.

**Filter onboarding to what the user can actually see.** A spotlight on an
empty `if (data.isNotEmpty()) { ... }` section is a bug, not a feature.

---

## Files touched

- `app/src/main/java/ani/saikou/data/local/db/ActivityEventEntity.kt` (new)
- `app/src/main/java/ani/saikou/data/local/db/ActivityEventDao.kt` (new)
- `app/src/main/java/ani/saikou/data/local/db/SaikouDatabase.kt` (v4→v5 migration)
- `app/src/main/java/ani/saikou/data/local/OnboardingPrefs.kt` (new)
- `app/src/main/java/ani/saikou/screens/reader/ReaderSettingsStorage.kt` (new)
- `app/src/main/java/ani/saikou/screens/reader/MangaReaderScreen.kt`
- `app/src/main/java/ani/saikou/screens/reader/MangaReaderViewModel.kt`
- `app/src/main/java/ani/saikou/screens/player/VideoPlayerScreen.kt`
- `app/src/main/java/ani/saikou/screens/player/VideoPlayerViewModel.kt`
- `app/src/main/java/ani/saikou/components/ActivityHeatmap.kt` (new)
- `app/src/main/java/ani/saikou/components/HomeTour.kt` (new)
- `app/src/main/java/ani/saikou/screens/home/HomeScreen.kt`
- `app/src/main/java/ani/saikou/screens/home/HomeViewModel.kt`
- `app/src/main/java/ani/saikou/MiyoApplication.kt` (daily session log)
- `app/src/main/java/ani/saikou/di/AppModule.kt`
