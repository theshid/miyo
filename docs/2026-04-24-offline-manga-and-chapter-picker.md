# Building offline manga reading (and a few reader polish items)

Date: 2026-04-24

A working session that started with a user recommendation — "can I read manga
offline?" — and ended with three pieces of a full offline stack, an
in-reader chapter picker, and a handful of supporting bug fixes that only
surfaced because the new features exposed them. This is a write-up of the
design decisions, the traps we walked around, and the stuff worth keeping in
mind next time.

---

## Part 1 — Offline reading, in three pieces

The existing app already had `MangaDownloadManager` and `DownloadService`
writing chapters to disk and tracking them in a Room table. Nothing read from
those files. So the shape of the work was:

1. **Cache-first reader**: make the reader check the download table before
   it touches the network, build the page list from `file://` URIs when a
   chapter is cached.
2. **Per-chapter Download button** on the media detail's chapter list —
   a concrete way to actually get chapters into that cache.
3. **End-of-chapter "Download next 5" banner** — the place where most users
   *think* about downloading is when they just finished reading.

We sequenced them in that order deliberately. Piece 1 is the plumbing — it
has no visible effect until something has been downloaded. Piece 2 and 3 are
the visible triggers. Shipping them out of order would either be a no-op
(buttons that do nothing useful) or fragile (downloads that can't be read).

### Piece 1 — making the reader look at disk before the network

Room already tracked downloads by `(mangaId, chapterKey)` where `chapterKey`
is parser-specific (MangaDex UUIDs, MangaPill URL slugs). To jump to a
downloaded chapter purely by its *number*, we needed a universal lookup key.

**Schema v5 → v6** added a `chapterNumber: Int` column with default `-1` for
legacy rows:

```sql
ALTER TABLE downloads ADD COLUMN chapterNumber INTEGER NOT NULL DEFAULT -1
```

And a corresponding DAO query:

```kotlin
@Query("""
    SELECT * FROM downloads
    WHERE mangaId = :mangaId AND chapterNumber = :chapterNumber AND status = 'COMPLETED'
    ORDER BY createdAt DESC LIMIT 1
""")
suspend fun getCompletedByChapterNumber(mangaId: Int, chapterNumber: Int): DownloadEntity?
```

The `ORDER BY createdAt DESC LIMIT 1` handles the case where the same chapter
has been downloaded from multiple sources — pick the most recent.

The reader-side change is small but carefully placed at the *very top* of
`MangaReaderViewModel.loadSources`, before any AniList call:

```kotlin
val localDownload = downloadDao.getCompletedByChapterNumber(mediaId, chapterNum)
if (localDownload != null) {
    val localPages = downloadManager.getLocalPages(mediaId, localDownload.chapterKey)
    if (!localPages.isNullOrEmpty()) {
        // Build UI state from local files and return@launch — NEVER touch the
        // network. Title + chapterName come from the download row; cover is
        // best-effort with a try/catch so offline reads don't fail.
        // ...
        return@launch
    }
}
```

The `return@launch` is the point. It's what turns this from "local cache as
optimization" into "local cache as full offline mode". Because Coil already
handles `file://` URIs the same as remote ones, the renderer didn't need to
change at all.

### Piece 2 — the per-chapter Download button

The media detail's chapter list already had the right anatomy for adding a
trailing button. The change was:

1. `MediaDetailViewModel.chapterDownloads: StateFlow<Map<Int, ChapterDownloadState>>`
   — reactively maps `DownloadDao.getDownloadsForManga(mediaId)` rows into
   a view keyed by chapter number.
2. `queueChapterDownload(chapterNumber, onQueued: () -> Unit)` — inserts the
   DB row, then invokes `onQueued` *after* the insert.
3. A `ChapterDownloadButton` whose icon is a pure function of `state?.status`:
   `null` → Download, `QUEUED`/`PAUSED` → Schedule clock, `DOWNLOADING` →
   circular progress with a ✕ in the middle, `COMPLETED` → DownloadDone
   (tinted Secondary), `ERROR` → red Download for retry.

The `onQueued` callback is the non-obvious bit. When the composable does:

```kotlin
onDownloadClick = { chapterNum ->
    viewModel.queueChapterDownload(chapterNum) {
        DownloadService.start(context)
    }
},
```

…we're avoiding a race. `queueChapterDownload` launches a coroutine; the
`DownloadService.start(...)` call would otherwise fire immediately on the
main thread, before the DB insert lands. When the service then runs
`dao.getPendingDownloads()`, it'd see nothing and exit. Threading the start
through `onQueued` guarantees the DB is ready when the service reads it.

### Piece 2.5 — the service had a blind spot

Once `DownloadService` was being triggered by real user taps, a latent bug
surfaced: `processQueue` only ever searched MangaDex to re-resolve the
source. Anything MangaDex didn't host (which for licensed titles means most
English-readable manga) silently failed. The reader's read path had a
MangaPill fallback for exactly this reason; the download path didn't.

We extracted the source resolution into a helper:

```kotlin
private suspend fun resolvePages(mangaTitle: String, chapterNumber: Int): List<MangaPage> {
    if (chapterNumber < 0) return emptyList()

    runCatching {
        val source = mangaDex.search(mangaTitle).firstOrNull()
        if (source != null) {
            val chapter = mangaDex.getChapters(source.id)
                .find { it.number.toInt() == chapterNumber }
            if (chapter != null) {
                val pages = mangaDex.getPages(chapter.id)
                if (pages.isNotEmpty()) return pages
            }
        }
    }
    // MangaPill fallback — same shape.
    runCatching {
        val source = mangaPill.search(mangaTitle).firstOrNull()
        if (source != null) {
            val chapter = mangaPill.getChapters(source.id)
                .find { it.number.toInt() == chapterNumber }
            if (chapter != null) {
                val pages = mangaPill.getPages(chapter.id)
                if (pages.isNotEmpty()) return pages
            }
        }
    }
    return emptyList()
}
```

**There was a follow-on bug** though. `MangaDownloadManager.downloadPage`
only applied a `User-Agent` header; it ignored `MangaPage.headers`. MangaPill
pages come with `Referer: https://mangapill.com/` set — their CDN returns
403 without it. We'd done this dance before in the reader (there's a
`case-study-mangapill-images.md` about it), and the download path needed
the same fix:

```kotlin
private fun downloadPage(url: String, destination: File, headers: Map<String, String> = emptyMap()) {
    val connection = URL(url).openConnection()
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Miyo/2.0")
    for ((k, v) in headers) {
        connection.setRequestProperty(k, v)
    }
    // ...
}
```

Worth saying out loud: parser code knows what headers its images require.
Any downstream code that fetches those images needs to honour them. It's a
trap you fall into once per surface — we've now done it for the renderer
(Coil) and for the downloader.

### Piece 3 — the end-of-chapter suggestion banner

The piece the user kept nudging us toward. The insight they articulated:
*"the Download button on the detail screen is fine, but I never visit that
screen when I'm in a reading session — I go from Continue Reading straight
into the reader."* The end-of-chapter moment is the one place where the
user has already proven they want more and is stationary long enough to
tap something.

Four things had to come together:

**1. Actually detecting end of chapter.** Our initial check
(`currentPage == totalPages`) broke silently for webtoon-mode readers
because `currentPage` was driven by `listState.firstVisibleItemIndex + 1`.
At the bottom of a tall scrolling strip where several panels fit on screen,
`firstVisibleItemIndex` stops a few items short of the last one and the
condition never fires. The fix: report the *last* visible index instead,
which for tall single-page manga is the same (one page fills the viewport)
and for webtoon mode finally reaches `totalPages`:

```kotlin
val currentPageIndex by remember {
    derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            ?: listState.firstVisibleItemIndex
    }
}
LaunchedEffect(currentPageIndex) {
    onPageChanged(currentPageIndex + 1)
}
```

This is the kind of bug that only reveals itself when you try to *use* a
signal you'd been computing for something else. The `currentPage` value
had been going into a cosmetic "1 / 25" counter, where the difference of
two or three pages is invisible. The moment we wired it to a gate, the
mismatch mattered.

**2. A real size estimate, not a guess.** First pass of the banner copy
said "Downloading 5 chapters will use about 150 MB." That number came from
rounding up "to be safe" and had no basis in data — typical B&W manga is
about 10 MB per chapter, not 30. We fixed the lie:

- Schema v6 → v7 added `fileSizeBytes: Long DEFAULT 0`.
- `MangaDownloadManager.downloadChapter` on success walks the chapter
  directory and writes the real total back: `dao.updateFileSize(...)`.
- `ChapterSizeEstimator` picks the best-available signal:
  1. Per-series average (best — same art style, same scanlation group).
  2. Global average across all completed downloads (fallback).
  3. A single hardcoded 10 MB/chapter for a user's very first download.
- A `format(bytes)` helper produces human-friendly "≈ 42 MB" / "≈ 1.2 GB".

The takeaway: if a UI claim is going to be numeric and user-facing, it's
nearly always cheap to make it measured instead of guessed. The first
completed download retires the fallback forever.

**3. Connectivity-aware confirmation.** The user explicitly asked for this —
on Wi-Fi, download silently; on cellular, show a confirmation dialog with
the estimate. We added one method to `ConnectivityObserver`:

```kotlin
fun isUnmetered(): Boolean {
    val active = connectivityManager.activeNetwork ?: return false
    val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
```

`NET_CAPABILITY_NOT_METERED` is Android's canonical "this network is OK to
use heavily" flag — Wi-Fi + Ethernet set it, cellular doesn't. Same signal
WorkManager uses under the hood for its UNMETERED network constraint.

When the user taps Download:

```kotlin
val unmetered = AppModule.connectivity().isUnmetered()
if (unmetered) startBatchDownload() else showCellularConfirm = true
```

The dialog body then reads like *"Downloading 5 chapters will use about
42 MB of data"* — concrete, measured, and the user can decide.

**4. A settings toggle, not a mode.** Our original sketch had an
"offline mode" toggle. The user correctly pushed back: *"modes that need
foresight tend to fail in the moment they're meant to help."* Instead we
added a single binary setting — *Suggest downloads at end of chapter* —
defaulted on, stored alongside the other reader settings. Users who hate
the banner can turn it off once; the rest never have to think about it.

---

## Part 2 — The chapter picker inside the reader

The user's next ask: *"I'm reading chapter 15. I want to jump to chapter 50
without backing out of the reader and scrolling through the detail screen."*
Natural — add a Chapter icon in the reader's top overlay, open a bottom
sheet listing all chapters, tap to jump.

### Extracting a shared row

The media detail screen already had a chapter row composable. Instead of
duplicating, we extracted:

- `components/ChapterRow.kt` — houses `ChapterDownloadState`,
  `ChapterDownloadButton`, `ChapterRow`, `ChapterRowShimmer`.
- Both the detail screen and the new reader sheet consume the same types.
- `ChapterRow` gained an `isCurrent: Boolean` flag — the reader sheet tints
  the current chapter's number badge in Primary so you can see where you
  are at a glance.

The Shimmer version came later but lives next to the real one on purpose —
they have to be visually indistinguishable in layout so the transition
from loading → loaded doesn't jump.

### Lazy-fetching the chapter list

The reader already has chapters in memory (it needed them to find the
currently-playing one), but only after it's done its full load. For the
sheet to work on a cold-open (user opens app → Continue Reading → sheet)
or on a cached-only path (user is offline, only has downloads), we needed
a way to either fetch fresh or make do with what's stored.

`MangaReaderViewModel.ensureChapterListLoaded()` is idempotent and safe to
call from UI:

```kotlin
fun ensureChapterListLoaded() {
    if (_allChapters.value.isNotEmpty() || _chapterListLoading.value) return
    viewModelScope.launch {
        _chapterListLoading.value = true
        val chapters = try { /* fetch */ } catch (_: Exception) { emptyList() }
        _allChapters.value = chapters
        _chapterListLoading.value = false
    }
}
```

Called from a `LaunchedEffect(showChapterList)` in the screen so it only
runs when the user actually opens the sheet. Nothing prefetched; nothing
wasted if the user never opens it.

### The Vinland Saga bug — MangaDex telling on itself

First run on Vinland Saga: the sheet showed only the 5 chapters the user
had downloaded plus three of the series' latest chapters (218, 219, 220).
The middle hundreds of chapters were just missing.

Debugging: MangaPill's HTML has all 224 chapter links in it (I curl'd to
confirm), the parser selector matched them, no script-only rendering
tricks. So MangaPill was fine. The bug was on the reader side — the
chapter-list fetch path was asking MangaDex first, accepting any non-null
search hit, and calling `mangaDex.getChapters(...)` — which for a licensed
title like Vinland Saga returns 3 sample chapters rather than 220.

**The insight**: MangaDex tells you this directly in the search response.
`attributes.lastChapter` is the *series' known last chapter number* — e.g.
"220" for Vinland Saga. The feed endpoint tells you what MangaDex *actually
hosts* — 7 chapters for the same title. When those numbers disagree by a
lot, MangaDex has a licensed/partial catalog and you shouldn't trust it as
a primary source.

We surfaced the hint on the domain model:

```kotlin
data class MangaSource(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val totalChapterHint: Int? = null,  // NEW
)
```

And used it as a gate in the VM:

```kotlin
val mdx = mangaDex.search(title).firstOrNull()
val mdxChapters = if (mdx != null) mangaDex.getChapters(mdx.id) else emptyList()

// Coverage check: if MangaDex says the series has N chapters and returned M,
// is M/N above the acceptance threshold?
val mdxCovers = mdx?.totalChapterHint == null ||
    mdxChapters.size >= (mdx.totalChapterHint * 0.9)

if (mdxChapters.isNotEmpty() && mdxCovers) {
    // MangaDex has it in full — skip MangaPill entirely.
} else {
    // MangaPill fallback.
}
```

Three real-world cases:

- **Vinland Saga**: hint 220, feed 7, coverage = 3 %. Falls through to
  MangaPill. Correct.
- **A fully-MangaDex-hosted series**: hint 45, feed 45, coverage = 100 %.
  Accept MangaDex, never touch MangaPill. Fast.
- **Ongoing with unknown finale**: hint `null`, we give MangaDex the benefit
  of the doubt. Matches the old behaviour.

We set the threshold to 90 % — slightly higher than the 80 % first pass —
because typical translation gaps (one missing side-chapter, a half-chapter
skipped by the group) shouldn't force a MangaPill round-trip, but anything
below 90 % almost always means licensed or partial.

### Bucketed display for long manga

The detail screen had already solved "how do you render a 220-chapter list
in a sane way" — chunk into `100`-chapter buckets with a horizontal chip
row on top. The sheet now does the same:

- `totalBuckets = (chapterNumbers.size + 99) / 100`
- Each chip reads "1–100", "101–200", etc.
- Initial selected bucket is whichever contains the current chapter.
- Within the selected bucket, `LazyColumn` scrolls to the current chapter
  on first display so the user lands on where they are.

If the list is ≤ 100 chapters, no chip row renders — short manga behave
the same as before.

### Shimmer while the rest of the list loads

One last polish. When the sheet opens:

- The cached downloaded chapters are available instantly from the DB.
- The full parser list takes a second or two to arrive.

In between, the sheet looked *done* — some chapters shown, no indication
that more are on the way. We fixed it with:

1. An `isLoading` flag passed unconditionally (not gated on "has any
   chapters"). The old code did `isLoading = chapterListLoading && mergedNumbers.isEmpty()`
   which meant any cached chapter suppressed the loading signal.
2. A state-aware subtitle: "N cached · fetching more…" while partial,
   "N available" when done, "Fetching chapters…" when empty.
3. A `ChapterRowShimmer` composable that renders the same three-column
   layout as a real row (40dp badge / title bar / 24dp trailing icon)
   but every element is a `ShimmerBox` from the existing shimmer kit.
4. The sheet appends four shimmer items at the end of the `LazyColumn`
   when `isLoading` is true. Regardless of how many chapters are actually
   missing — it's a "something's happening" signal, not a count.

The result: user opens the sheet, sees their downloads immediately plus
four shimmering placeholders at the bottom, and those get replaced by real
rows a beat later.

---

## Stuff that fell out of the way while we were in there

A few adjacent fixes that only existed because the new features were
specifically exercising these code paths.

### Webtoon end-of-page detection

Covered above in piece 3, but worth restating as a pattern: when you
discover a signal is wrong because you try to use it for something new,
that's the moment it was a latent bug. The `firstVisibleItemIndex + 1`
path had been live forever and nobody noticed because nothing consumed it
in a binary way.

### Catch Me Up output format

Separate feature from earlier — the AI-generated recap bottom sheet on the
media detail screen. The user said 400 words felt boring. We changed the
prompt to force a strict format:

```
- One short hook sentence (max 25 words) describing where the story stands RIGHT NOW.
- Then exactly 3 bullet points covering the most important arcs.
- Each bullet: one sentence, max 30 words.
- No headings, no preamble, no closing remarks.
```

Word budget cut roughly 3× and the output reads faster. The "no
preamble, no closing remarks" line is what it sounds like — LLMs love to
open with "Sure! Here's a recap…" and close with "Hope that helps!", both
of which pad without adding content.

---

## Patterns worth remembering

A grab-bag of transferable lessons from this session:

**Schema changes are cheap when they solve the right problem.** We did two
migrations (v5→v6 for `chapterNumber`, v6→v7 for `fileSizeBytes`). Both
replaced hacks — parser-specific ID lookups and guessed size labels —
with data the database already had the best view of. A migration is work,
but hauling around the wrong abstraction is more work, forever.

**Honour parser-side headers wherever you fetch.** MangaPill's CDN needs a
Referer. Coil had to know that; the downloader had to know that; any
future caching layer will too. If your data class has a `headers` field,
every consumer of its URLs has to apply it. No free rides.

**Don't drop a race behind a single-line call.** `viewModel.queue(...)
.then .startService()` looks fine until `then` is "a coroutine, eventually"
and `startService()` immediately tries to read what `queue` hasn't written
yet. The fix is a post-queue callback, not clever scheduling.

**Use the remote source's metadata as a decision signal.** MangaDex's
`attributes.lastChapter` is free data we were ignoring. Checking
`feed.size / lastChapter >= 0.9` before trusting MangaDex turns a
"always fall back to MangaPill" pattern into "skip the fallback when the
primary is demonstrably complete", which saves network on every
well-hosted series.

**A latent bug becomes real the moment something depends on it.** The
webtoon page counter was "wrong" forever, silently. The moment we wired
it to a gate that said "show the banner when we're at the last page", the
wrongness became observable. The lesson isn't "don't cut corners" — it's
"when you add a new consumer of an existing signal, audit that signal".

**Shimmer placeholders are four-lines of value.** The `ShimmerBox` primitive
cost nothing to reuse — we wrote a single `ChapterRowShimmer` that mimics
the real row layout, and four instances at the bottom of the LazyColumn
turned a "looks done" state into "actively loading". Sometimes the smallest
UI change is the one users notice most.

**Mode toggles ≤ smart defaults.** We almost built an "offline mode". The
user pushed us toward a single "Suggest downloads" toggle defaulted on,
with everything else inferred from network state. Users don't flip
settings in advance of needing them; the app has to be ready.

---

## Files touched

```
app/src/main/java/ani/saikou/
  components/
    ChapterRow.kt                         NEW — shared row + button + shimmer + state
  data/
    local/
      ConnectivityObserver.kt             + isUnmetered()
      db/
        DownloadDao.kt                    + getCompletedByChapterNumber + updateFileSize
                                            + average-size queries
        DownloadEntity.kt                 + chapterNumber + fileSizeBytes
        SaikouDatabase.kt                 v5→v6, v6→v7 migrations
      downloads/
        ChapterSizeEstimator.kt           NEW — per-series / global / fallback estimator
        DownloadService.kt                MangaPill fallback in processQueue
        MangaDownloadManager.kt           honour MangaPage.headers; write fileSize on done
    remote/
      parsers/
        MangaDexParser.kt                 surface attributes.lastChapter on MangaSource
  domain/model/
    Chapter.kt                            MangaSource.totalChapterHint
  screens/
    detail/
      CatchMeUpSheet.kt                   hook + 3-bullet format
      MediaDetailScreen.kt                adopt shared ChapterRow
      MediaDetailViewModel.kt             drop duplicate state struct
    reader/
      MangaReaderScreen.kt                cache-first hook; end-of-chapter banner;
                                            cellular dialog; chapter sheet trigger;
                                            webtoon last-visible fix
      MangaReaderViewModel.kt             cache-first load; allChapters + downloads
                                            flows; queueNext + queueSingle;
                                            ensureChapterListLoaded with coverage gate
      ReaderChapterListSheet.kt           NEW — bucketed sheet with shimmer
      ReaderSettingsStorage.kt            + suggestDownloads persistence
```
