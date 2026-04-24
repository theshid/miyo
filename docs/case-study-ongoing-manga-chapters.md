# Case Study: Why Ongoing Manga Showed "No Chapter Data Available"

## The Problem

When opening an ongoing manga like **One Piece** or **Jujutsu Kaisen** and tapping
the Chapters tab, the user saw "No chapter data available" — even though these
manga have hundreds or thousands of chapters.

## Understanding the Data Sources

The app has two sources of truth for "how many chapters does this manga have":

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Source 1: AniList API (metadata)                               │
│  ─────────────────────────────                                  │
│  Returns: title, cover, score, status, totalChapters            │
│                                                                 │
│  Problem: For ongoing manga, totalChapters = null               │
│  Why: AniList doesn't know the final count because              │
│       the manga is still being published                        │
│                                                                 │
│  Example:                                                       │
│    One Piece  → chapters: null,  status: "RELEASING"            │
│    Naruto     → chapters: 700,   status: "FINISHED"             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Source 2: MangaDex / MangaPill (actual chapters)               │
│  ────────────────────────────────────────────────                │
│  Returns: list of actual chapter files you can read             │
│                                                                 │
│  These always know the current count because they host          │
│  the actual chapter pages                                       │
│                                                                 │
│  Example:                                                       │
│    One Piece on MangaPill → 1196 chapters                       │
│    Naruto on MangaDex     → 700 chapters                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## The Bug

The `ChaptersTab` composable used AniList's `totalChapters` directly:

```kotlin
val count = totalChapters ?: 0    // null becomes 0
if (count == 0) {
    Text("No chapter data available")  // Dead end — user can't read
    return
}
// Generate chapter list from 1..count
```

For ongoing manga: `totalChapters = null` → `count = 0` → "No chapter data" → blocked.

```
┌─────────────────────────────────────────────────────────────────┐
│ The broken flow                                                 │
│                                                                 │
│  AniList API                                                    │
│    │                                                            │
│    │  "One Piece has chapters: null (still releasing)"          │
│    │                                                            │
│    ▼                                                            │
│  ChaptersTab                                                    │
│    │                                                            │
│    │  count = null ?? 0 = 0                                     │
│    │                                                            │
│    ▼                                                            │
│  "No chapter data available"                                    │
│                                                                 │
│  ╳ User cannot read any chapters                                │
│  ╳ MangaPill has 1196 chapters but we never ask                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## The Investigation

### Step 1: Verify AniList returns null

```bash
curl -s 'https://graphql.anilist.co' \
  -H 'Content-Type: application/json' \
  -d '{"query":"{Media(search:\"One Piece\",type:MANGA){chapters status}}"}'
```

Result:
```json
{ "chapters": null, "status": "RELEASING" }
```

Confirmed: AniList doesn't know the total for ongoing manga.

### Step 2: Check what MangaPill has

```bash
curl -s "https://mangapill.com/manga/2/one-piece" | grep -oE 'href="/chapters/[^"]*"' | wc -l
```

Result: **1196 chapters**. The data exists — we just weren't asking.

### Step 3: Identify the design flaw

The `ChaptersTab` had a single source of truth (AniList) and a hard block
when that source returned null. It never consulted the actual chapter sources
(MangaDex/MangaPill) which always know the current count.

## The Fix

### Approach: Source-aware chapter count with async fallback

When AniList says `null`, asynchronously fetch the chapter count from manga
sources. Show a loading spinner while fetching, then display all chapters.

```
┌─────────────────────────────────────────────────────────────────┐
│ The fixed flow                                                  │
│                                                                 │
│  AniList API                                                    │
│    │                                                            │
│    │  "One Piece has chapters: null"                            │
│    │                                                            │
│    ▼                                                            │
│  ChaptersTab                                                    │
│    │                                                            │
│    │  count = null → trigger source lookup                      │
│    │                                                            │
│    ▼                                                            │
│  "Fetching chapters from source..." (spinner)                   │
│    │                                                            │
│    ├──→ MangaDex.search("ONE PIECE")                            │
│    │      └── getChapters() → found? use last chapter number    │
│    │                                                            │
│    ├──→ (if MangaDex fails) MangaPill.search("ONE PIECE")       │
│    │      └── getChapters() → 1196 chapters                    │
│    │                                                            │
│    ▼                                                            │
│  count = 1196                                                   │
│    │                                                            │
│    ▼                                                            │
│  Display chapters with pagination:                              │
│  [1-100] [101-200] [201-300] ... [1101-1196]                   │
│                                                                 │
│  ✓ User can read any of the 1196 chapters                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### The code

```kotlin
@Composable
private fun ChaptersTab(
    totalChapters: Int?,       // From AniList (null for ongoing)
    userProgress: Int?,
    mediaTitle: String?,       // NEW — needed to search sources
    onChapterClick: (Int) -> Unit,
) {
    var sourceChapterCount by remember { mutableStateOf<Int?>(null) }
    var loadingCount by remember { mutableStateOf(false) }

    // When AniList doesn't know the count, ask the sources
    if (totalChapters == null || totalChapters == 0) {
        LaunchedEffect(mediaTitle) {
            loadingCount = true
            withContext(Dispatchers.IO) {
                // Try MangaDex first
                val dexSources = MangaDexParser().search(title)
                if (dexSources.isNotEmpty()) {
                    val chapters = MangaDexParser().getChapters(dexSources.first().id)
                    sourceChapterCount = chapters.lastOrNull()?.number?.toInt()
                }
                // Fallback to MangaPill
                if (sourceChapterCount == null) {
                    val pillSources = MangaPillParser().search(title)
                    if (pillSources.isNotEmpty()) {
                        val chapters = MangaPillParser().getChapters(pillSources.first().id)
                        sourceChapterCount = chapters.lastOrNull()?.number?.toInt()
                    }
                }
            }
            loadingCount = false
        }
    }

    // Use AniList count if available, otherwise use source count
    val count = when {
        totalChapters != null && totalChapters > 0 -> totalChapters
        sourceChapterCount != null && sourceChapterCount > 0 -> sourceChapterCount
        else -> 0
    }

    // Show loading, empty state, or chapter list based on count
    when {
        count == 0 && loadingCount -> ShowSpinner("Fetching chapters...")
        count == 0 -> ShowEmpty("No chapters found")
        else -> ShowChapterList(count, ...)
    }
}
```

### The priority chain

```
Priority 1: AniList totalChapters (fast, cached, accurate for completed manga)
    │
    │  null or 0?
    │
    ▼
Priority 2: MangaDex chapter count (API-based, has most manga)
    │
    │  empty or 0?
    │
    ▼
Priority 3: MangaPill chapter count (HTML-parsed, has licensed manga)
    │
    │  empty or 0?
    │
    ▼
Fallback: "No chapters found" (genuinely unavailable)
```

## Key Takeaways

1. **Never block the user on a single data source.** AniList is great for
   metadata but doesn't know everything. When one source says "I don't know",
   ask the next one.

2. **Ongoing content is inherently incomplete.** Any API that tracks ongoing
   series will have gaps — missing chapter counts, missing episode counts,
   null release dates. Design for `null` as a normal state, not an error.

3. **Async fallback with loading state.** The source lookup takes 2-3 seconds
   (network calls to MangaDex + MangaPill). Show a spinner instead of blocking
   the UI. `LaunchedEffect` with `withContext(Dispatchers.IO)` keeps the main
   thread responsive.

4. **The chapter count ≠ the chapter list.** We only need the *last* chapter
   number to generate the list (1..N). We don't need to fetch all chapter
   details upfront — that happens when the user actually taps a chapter.

## Where this pattern applies elsewhere

| Scenario | AniList says | Source provides |
|----------|-------------|----------------|
| Ongoing manga chapters | `null` | Actual count (MangaPill: 1196) |
| Ongoing anime episodes | `null` | Actual count (GogoAnime) |
| Unreleased season episode count | `null` | Not available yet |

The same async-fallback pattern could be applied to the Episodes tab for
ongoing anime where AniList doesn't know the total episode count.
