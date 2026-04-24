# Case Study: Why MangaPill Images Showed a Blank White Screen

## The Problem

When reading Vinland Saga in the manga reader, the screen was completely white.
The logs showed that 88 pages were successfully loaded from MangaPill — but nothing rendered.

## The Investigation

### Step 1: Verify the data pipeline works

We added logging at every stage:

```
Source search → MangaDex found Vinland Saga (but only chapters 218-220, no English ch.1)
             → Fallback to MangaPill
             → MangaPill found 224 chapters
             → Chapter 1 found, ID: /chapters/4741-10001000/vinland-saga-chapter-1
             → 88 pages loaded with CDN URLs
```

**Result**: Data pipeline is fine. The URLs are correct. The issue is in **image loading**.

### Step 2: Understand how images load in the app

Here's the chain from server to screen:

```
┌──────────────────┐     HTTP GET      ┌──────────────────┐
│                  │ ───────────────→  │                  │
│   Coil (image    │                   │   MangaPill CDN  │
│   loader in app) │ ←─────────────── │   (serves JPGs)  │
│                  │   Image bytes     │                  │
└──────────────────┘    or 403 error   └──────────────────┘
         │
         ▼
┌──────────────────┐
│  AsyncImage()    │
│  (Compose UI)    │
│  renders bitmap  │
└──────────────────┘
```

Coil silently fails on HTTP errors — it doesn't crash, it just shows nothing.
That's why we got a white screen instead of an error message.

### Step 3: Test the CDN directly

We used `curl` to test if the image URL works:

```bash
# Without Referer header
curl -s -w "HTTP: %{http_code}" -o /dev/null \
  "https://cdn.readdetectiveconan.com/file/mangap/4741/10001000/1.jpg" \
  -H "User-Agent: Mozilla/5.0"
# Result: HTTP 403 ← BLOCKED

# With Referer header
curl -s -w "HTTP: %{http_code}" -o /dev/null \
  "https://cdn.readdetectiveconan.com/file/mangap/4741/10001000/1.jpg" \
  -H "User-Agent: Mozilla/5.0" \
  -H "Referer: https://mangapill.com/"
# Result: HTTP 200 ← SUCCESS
```

**Root cause found**: The CDN requires a `Referer` header.

### Step 4: Understand the Referer header

When you visit a website in your browser and click on an image, your browser
automatically sends a `Referer` header telling the server which page you came from:

```
┌─────────────────────────────────────────────────────────────────┐
│ Normal browser flow (works)                                     │
│                                                                 │
│  User visits: mangapill.com/chapters/4741.../chapter-1          │
│       │                                                         │
│       ▼                                                         │
│  Browser sees <img src="cdn.example.com/1.jpg">                 │
│       │                                                         │
│       ▼                                                         │
│  Browser sends:                                                 │
│    GET /file/mangap/4741/10001000/1.jpg                         │
│    Host: cdn.readdetectiveconan.com                              │
│    Referer: https://mangapill.com/    ◄── "I came from here"    │
│       │                                                         │
│       ▼                                                         │
│  CDN checks: Is mangapill.com allowed? → YES → serve image     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Our app (broken — no Referer)                                   │
│                                                                 │
│  App has URL: "cdn.example.com/1.jpg"                           │
│       │                                                         │
│       ▼                                                         │
│  Coil sends:                                                    │
│    GET /file/mangap/4741/10001000/1.jpg                         │
│    Host: cdn.readdetectiveconan.com                              │
│    (no Referer header)              ◄── "I came from... ???"    │
│       │                                                         │
│       ▼                                                         │
│  CDN checks: No Referer? → BLOCKED → 403 Forbidden             │
│       │                                                         │
│       ▼                                                         │
│  Coil gets 403 → silently fails → AsyncImage shows nothing     │
│       │                                                         │
│       ▼                                                         │
│  User sees: blank white screen                                  │
└─────────────────────────────────────────────────────────────────┘
```

Why do CDNs do this? To prevent **hotlinking** — other websites embedding
their images and stealing their bandwidth. The Referer check ensures only
requests originating from their own website are served.

## The Fix

Three changes across three layers:

### Layer 1: Data model — add headers to MangaPage

```kotlin
// Before
data class MangaPage(
    val index: Int,
    val imageUrl: String,
)

// After — headers can now travel with the URL
data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),  // NEW
)
```

### Layer 2: Parser — attach Referer when creating pages

```kotlin
// In MangaPillParser.getPages()
val referer = mapOf("Referer" to "https://mangapill.com/")

doc.select("img[data-src*=mangap]").mapIndexed { index, img ->
    MangaPage(
        index = index,
        imageUrl = img.attr("data-src"),
        headers = referer,  // Every page carries its Referer
    )
}
```

MangaDex pages don't need this (their CDN serves images without Referer),
so their `headers` stays empty — backward compatible.

### Layer 3: UI — pass headers to Coil

```kotlin
// Before — just a URL string, no headers
AsyncImage(
    model = page.imageUrl,  // Coil sends a bare GET
    ...
)

// After — build an ImageRequest with headers when needed
val model = if (page.headers.isNotEmpty()) {
    ImageRequest.Builder(context)
        .data(page.imageUrl)
        .apply {
            page.headers.forEach { (k, v) -> addHeader(k, v) }
        }
        .crossfade(true)
        .build()
} else {
    page.imageUrl  // No headers needed, use simple URL
}

AsyncImage(
    model = model,  // Coil now sends GET with Referer header
    ...
)
```

### The fixed flow:

```
┌─────────────────────────────────────────────────────────────────┐
│ Our app (fixed — with Referer)                                  │
│                                                                 │
│  MangaPillParser creates:                                       │
│    MangaPage(                                                   │
│      imageUrl = "cdn.example.com/1.jpg",                        │
│      headers = {"Referer": "https://mangapill.com/"}            │
│    )                                                            │
│       │                                                         │
│       ▼                                                         │
│  Reader builds ImageRequest with headers                        │
│       │                                                         │
│       ▼                                                         │
│  Coil sends:                                                    │
│    GET /file/mangap/4741/10001000/1.jpg                         │
│    Host: cdn.readdetectiveconan.com                              │
│    Referer: https://mangapill.com/   ◄── "I came from here"    │
│       │                                                         │
│       ▼                                                         │
│  CDN checks: mangapill.com? → YES → 200 OK → serve image       │
│       │                                                         │
│       ▼                                                         │
│  Coil decodes JPEG → AsyncImage renders → user sees manga page  │
└─────────────────────────────────────────────────────────────────┘
```

## Key Takeaways

1. **Silent failures are the hardest bugs.** Coil doesn't throw on 403 — it
   just shows nothing. Always check the actual HTTP response when images
   don't render.

2. **Test with curl first.** Before writing code, verify the URL works in
   isolation. Adding/removing headers in curl is instant — much faster than
   rebuilding the app.

3. **Different CDNs have different requirements.** MangaDex serves images
   freely. MangaPill requires Referer. Some require cookies. The parser
   should know what its CDN needs and attach the right headers.

4. **Headers should travel with the data.** Don't hardcode headers in the UI
   layer. Attach them to the data model (MangaPage) so the UI doesn't need
   to know which source it's rendering.

5. **The same pattern applies to video.** We had the exact same issue with
   HLS video streams earlier — the CDN returned 403 without Referer. The
   fix was identical: pass headers through the data model to ExoPlayer's
   `DefaultHttpDataSource.Factory`.

## Where this pattern appears in the codebase

| Component | Header needed | Where it's set |
|-----------|--------------|----------------|
| MangaPill images | `Referer: https://mangapill.com/` | `MangaPillParser.getPages()` |
| HLS video streams | `Referer: <embed URL>` | `GogoParser.extractDirectLink()` |
| Subtitle VTT files | None (separate CDN) | `VideoPlayerScreen` uses separate factory |
| MangaDex images | None | Headers empty by default |

## Debugging checklist for "images not loading"

1. Are the URLs correct? → Add logging to the parser
2. Do the URLs work in curl? → `curl -s -w "HTTP: %{http_code}" -o /dev/null <URL>`
3. Does adding headers help? → `curl -H "Referer: <origin>" <URL>`
4. Is Coil receiving the headers? → Build `ImageRequest` with `.addHeader()`
5. Is the image format supported? → Check if it's JPEG/PNG/WebP (Coil handles all three)
