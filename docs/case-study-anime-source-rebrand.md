# Case Study: When the Anime Source Disappeared Overnight

## The Problem

The user opened the app, picked an anime, tapped the play button. The player
screen opened, sat for a moment, and then stopped. No spinner. No error. No
toast. No samurai-illustration error card. No "go back" button. Just an
empty player with no video, no error, and no way to know what was wrong.

Same thing for the next anime they tried. And the next one. **Every single
title** failed the same way.

Nothing in our app had changed — last commit was a packaging bump days ago.
Nothing on AniList had changed. The home screen, search, library, manga
reader, AI chat — everything else worked. But the anime player flatlined.

This is the story of how a third-party scraper target shut down without
telling us, how the failure cascaded through our pipeline silently because
of a tiny gap in error handling, and how we rebuilt the integration without
shipping a single hotfix that papered over the next break.

```
┌──────────────────────────────────────────────────────────────────────┐
│ Two stacked failures we had to peel apart                            │
│                                                                      │
│   Failure 1: anitaku.to shut down and now serves only a              │
│              "We Have Moved" landing page. Every URL — search,       │
│              anime, episode — returns the same static HTML.          │
│              Our parser found zero results for everything.           │
│                                                                      │
│   Failure 2: The ViewModel had a silent-fail branch for the          │
│              case where stream extraction returns an empty list.     │
│              That branch turned a real bug into "nothing happens."   │
│                                                                      │
│ The first failure was the trigger. The second failure is the         │
│ reason the user didn't see an error message.                         │
└──────────────────────────────────────────────────────────────────────┘
```

The lesson is bigger than either bug. Every third-party scraper integration
in this app has the same fragility, and every silent-fail branch in any VM
will hide the next outage just as cleanly. The post-mortem matters less
than the playbook.

## The Investigation

### Step 1: Establish that nothing on our side moved

The user's report — "all anime fail" — could mean an upstream outage, a
network condition, a bad commit, or a runtime config change. The cheapest
check is the one that rules out the most causes:

```bash
$ git log --oneline -5
2b09898 Bump to versionCode 3 / versionName 1.2.1
245390c Plug remaining "all caught up" false-positive cases
1112fb6 Bump to versionCode 2 / versionName 1.2.0
42a194c Trust AniList totalChapters over fragmentary source listings
9d2e1cf Set windowSoftInputMode=adjustResize so IME insets reach Compose
```

Recent commits touch the home screen, IME insets, and version metadata —
nothing in the anime watch path. The player ViewModel, the
`GogoParser`, and the `AnimeSourceRepository` haven't been touched in
weeks. So whatever broke, broke under us.

That ruled out "we shipped a regression" and shifted the suspicion to "the
upstream we depend on is broken."

### Step 2: Curl the upstream

Our anime scraper hits `anitaku.to`, a GogoAnime mirror. The first probe is
always the same: hit the actual URL the app would hit, with the exact same
User-Agent, and see what comes back.

```bash
$ curl -sI -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
       "https://anitaku.to/search.html?keyword=naruto"
HTTP/2 200
content-type: text/html
server: cloudflare
last-modified: Sat, 16 May 2026 05:52:44 GMT
```

200 OK, served by Cloudflare. So the host is up. The interesting part is
the `last-modified` timestamp — the same one returned for `/` and for any
other path. That's a very strong "this is a static page, not a dynamic
search response" signal.

```bash
$ curl -s -A "Mozilla/5.0..." "https://anitaku.to/search.html?keyword=naruto" | head -10
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />

  <title>We Have Moved to AniNeko.to</title>
  <meta name="description" content="We have moved to AniNeko.to..." />

  <meta http-equiv="refresh" content="5;url=https://anineko.to" />
```

There it is. `anitaku.to` is now nothing but a static "we moved" landing
page with a 5-second meta-refresh redirect to `anineko.to`. Every URL on
the old domain — search, category, episode — returns the same HTML. Our
search selector `.last_episodes > ul > li div.img > a` matches nothing on
that page, so `GogoParser.search()` quietly returns an empty list for every
title.

For thoroughness we also probed the AJAX fallback host:

```bash
$ curl -sI -A "Mozilla/5.0" "https://ajax.gogocdn.net/ajax/load-list-episode?..."
* Could not resolve host: ajax.gogocdn.net
```

DNS-dead. The CDN we used as a fallback to fetch full episode lists is gone
entirely. So the second branch of `getEpisodes()` couldn't save us even if
we caught the first failure.

```
┌──────────────────────────────────────────────────────────────────────┐
│ What our app's pipeline actually did, post-shutdown                  │
│                                                                      │
│   VM: loadSources(title)                                             │
│      │                                                               │
│      ▼                                                               │
│   UseCase: ResolveAnimeSourcesUseCase(query=title)                   │
│      │                                                               │
│      ▼                                                               │
│   Repository: AnimeSourceRepository.search(query)                    │
│      │                                                               │
│      ▼                                                               │
│   Parser: Jsoup.connect("anitaku.to/search.html?keyword=naruto")     │
│      │       │                                                       │
│      │       └──► 200 OK, body = "We Have Moved to AniNeko.to"       │
│      │                                                               │
│      ▼                                                               │
│   doc.select(".last_episodes > ul > li div.img > a") → 0 elements    │
│      │                                                               │
│      ▼                                                               │
│   return emptyList()                                                 │
│      │                                                               │
│      ▼                                                               │
│   VM sees animeSources.isEmpty() → error "Anime not found on source" │
└──────────────────────────────────────────────────────────────────────┘
```

(That "Anime not found" message is what the user *would* have seen had
they been hitting the same flow as the unmigrated case. The reason they
didn't see anything will turn out to be a separate bug in a different
branch — that's the second failure mentioned at the top, and we'll come
back to it after we get the upstream rewired.)

### Step 3: Confirm the replacement exists

The landing page told us where to go. The first sanity check is just that
the new domain resolves and serves real content:

```bash
$ curl -sI "https://anineko.to/"
HTTP/2 200
content-type: text/html; charset=UTF-8
server: cloudflare
set-cookie: PHPSESSID=a4ar3l7b71tqpgbrpiitmrceng; path=/
```

200 with a PHP session cookie — this is a real dynamic site, not another
landing page. Good. But the old search URL pattern 404s on the new host:

```bash
$ curl -s -o /dev/null -w "%{http_code}\n" "https://anineko.to/search.html?keyword=naruto"
404
```

So the host is alive but the URL contract has changed. The new site reuses
the GogoAnime brand and probably most of the parser logic, but every path,
selector, and fallback needs to be re-derived from scratch. This is the
reverse-engineering phase.

### Step 4: Reverse-engineer the new site, top-down

The strategy is the same one any scraper rebuild follows: walk the public
pages a real user would walk, see what URLs the site uses and what markup
it ships, and write down the smallest contract that lets us re-derive
everything we need.

#### 4a. Find the search endpoint

Start with the home page. Every link in the navigation tells us about the
URL scheme:

```bash
$ curl -s "https://anineko.to/" | grep -oE 'href="[^"]+"' | sort -u | head -20
href="/browse"
href="/genres/action"
href="/genres/adventure"
href="/new-releases"
href="/ongoing"
href="/schedule"
href="/updates"
href="/watch/one-piece"
href="/watch/jujutsu-kaisen-the-culling-game-part-1"
href="/watch/mission-yozakura-family-season-2"
```

Two key facts: anime watch pages live at `/watch/<slug>` (no `/category/`
prefix anymore), and `/browse` is the browse/filter surface. A few
probes confirm the search URL:

```bash
$ for path in "/search.html?keyword=naruto" "/filter?keyword=naruto" \
              "/?s=naruto" "/?keyword=naruto" "/search/naruto" \
              "/browse?keyword=naruto"; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "https://anineko.to$path")
    echo "$code $path"
  done
404 /search.html?keyword=naruto
404 /filter?keyword=naruto
200 /?s=naruto
200 /?keyword=naruto
302 /search/naruto
200 /browse?keyword=naruto    ◄── the real one
```

`/browse?keyword=…` returns 200 and a real result page. The others either
404 or just render the home page with the query echoed back.

#### 4b. Figure out the search-result markup

Now that we know the URL, we need a selector that picks out one element
per anime card. Pull the page, find a known result, look at what wraps it:

```bash
$ curl -s "https://anineko.to/browse?keyword=naruto" -o /tmp/search.html
$ grep -B2 -A2 'href="/watch/naruto-shippuden"' /tmp/search.html | head -10
<article class="nv-anime-card nv-browse-card">
    <a class="nv-anime-thumb nv-browse-thumb" href="/watch/naruto-shippuden">
        <img src="https://cdn.cimovix.store/cover/274ad...webp"
             alt="Naruto: Shippuden" loading="lazy">
        ...
    </a>
    <h3 class="nv-anime-title"><a href="/watch/naruto-shippuden">Naruto: Shippuden</a></h3>
```

Each card is wrapped in `<article class="nv-anime-card nv-browse-card">`,
and each one has exactly one `<a class="nv-anime-thumb">` containing the
href and an `<img>` whose `alt` attribute holds the display name. That's
the cleanest selector — it gives us **one element per anime**, with the
slug, the title, and the cover URL all reachable from there.

```
Selector: article.nv-browse-card a.nv-anime-thumb

  el.attr("href")          → "/watch/naruto-shippuden"
  el.selectFirst("img")
    .attr("alt")           → "Naruto: Shippuden"
  el.selectFirst("img")
    .attr("src")           → "https://cdn.cimovix.store/cover/...webp"
```

The old parser pulled the name from `el.attr("title")` — anineko's anchors
don't carry a `title` attribute, so reading from `img.alt` is the right
adaptation.

#### 4c. Reverse-engineer the anime page

The anime detail page lives at `/watch/<slug>` (no pagination, no
`/category/` prefix). The next question is whether the episode list is
server-rendered or lazy-loaded via AJAX:

```bash
$ curl -s "https://anineko.to/watch/naruto-shippuden" -o /tmp/anime.html -w "%{size_download}\n"
592635

$ grep -cE 'href="/watch/naruto-shippuden/ep-' /tmp/anime.html
1001    ◄── 500 episodes × 2 anchors each (thumb + title)

$ grep -oE 'class="[^"]*pag[a-z]*[^"]*"' /tmp/anime.html | sort -u
class="nv-info-page"    ◄── just the page-wrapper, not pagination
```

Naruto Shippuden ships all 500 episodes in a single 580KB page. To rule
out the case where a longer show *does* paginate, we cross-check One Piece:

```bash
$ curl -s "https://anineko.to/watch/one-piece" -o /tmp/op.html -w "%{size_download}\n"
1365960

$ grep -oE 'href="/watch/one-piece/ep-[0-9]+"' /tmp/op.html \
    | grep -oE '[0-9]+' | sort -n | tail -3
1160
1161
1161
```

1161 episodes in a single 1.3MB page. Anineko does **not** paginate. That
matters because the old parser had a fallback AJAX path that hit
`ajax.gogocdn.net` (now DNS-dead) for paginated shows. We can delete the
whole fallback branch — it would never run, and the host it pointed at
doesn't exist anymore.

The episode item markup is uniform:

```html
<article class="nv-info-episode-item">
    <a class="nv-info-episode-main" href="/watch/naruto-shippuden/ep-1">
        <strong>Episode 1</strong>
        <span>Homecoming</span>
    </a>
    ...
</article>
```

So the selector is `a.nv-info-episode-main`, and the episode number can
come straight out of the href via a regex on `/ep-(\d+)`. No more reading
from a `data-num` attribute, no more stripping an "EP" prefix off a
display name — just one regex, one path.

#### 4d. Reverse-engineer the episode page (where the streams are)

This is the deepest layer, and it's where reverse engineering pays off the
most. The episode page is what `getStreamLinks()` parses — it's the page
that wraps the embedded video servers. Pull it and look at the structure:

```bash
$ curl -s "https://anineko.to/watch/naruto-shippuden/ep-1" -o /tmp/ep.html
$ grep -oE 'class="nv-[a-z-]*(server|embed|player)[a-z-]*[^"]*"' /tmp/ep.html \
    | sort -u
class="nv-player-card"
class="nv-server-btn server-video server "
class="nv-server-btn server-video server default"
class="nv-server-grid nv-server-panel server-items lang-group"
class="nv-server-tabs server-type server-tab"

$ grep -oE 'data-video="[^"]+"' /tmp/ep.html | head -5
data-video="https://vibeplayer.site/a9bc97b7092b5bc1"
data-video="https://otakuhg.site/e/d6i8zmd97cpw"
data-video="https://otakuvid.online/embed/osjvl5b6ubmr"
data-video="https://playmogo.com/e/dbm5orupvt1o"
data-video="https://vibeplayer.site/ag9081f74101f453689d0e7dd27e22f9274h"
```

The embed URLs are stamped on something with class `nv-server-btn
server-video`, the URL itself lives on a `data-video` attribute, and there
are several mirror hosts (some of which we recognize from the old site —
`vibeplayer.site`, `otakuhg.site`, etc.). That's promising: the embed-side
extraction logic (the unpacker, the m3u8 regex) might still work.

The naive selector is `a.nv-server-btn.server-video[data-video]`. **It's
wrong, and the way it's wrong will bite us a few sections from now.** We'll
get to that.

### Step 5: Verify the embed pipeline before rewriting anything

The riskiest assumption in a rebrand response is "the old extractor still
works on the new embeds." Before pouring effort into selectors that depend
on it, we want a yes/no answer on the entire post-selector pipeline.

The fastest way to do that without rebuilding the app is to simulate the
parser logic in another language. Python has the same regex flavor we use
in Kotlin and identical HTTP semantics over `urllib`, so a thirty-line
script exercises the whole pipeline against the live embeds:

```python
import re, urllib.request

def fetch(url, referer="https://anineko.to/"):
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': referer,
    })
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.read().decode('utf-8', errors='ignore')

def unpack(html):
    pat = r"eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)"
    m = re.search(pat, html)
    if not m: return None
    payload, base, count, keys = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4).split('|')
    # Reconstruct Dean Edwards' p.a.c.k.e.d original source
    result = payload
    for i in range(count - 1, -1, -1):
        enc = format(i, '0') if i == 0 else ''.join('0123456789abcdefghijklmnopqrstuvwxyz'[(i // base**j) % base]
                                                    for j in range(int.bit_length(i)/3+1, -1, -1)).lstrip('0') or '0'
        rep = keys[i] if i < len(keys) and keys[i] else enc
        result = re.sub(r'\b' + re.escape(enc) + r'\b', rep, result)
    return result

m3u8_re = re.compile(r'(https?://[^\s"\'\\]+\.m3u8[^\s"\'\\]*)')

for url in ["https://otakuhg.site/e/d6i8zmd97cpw",
            "https://otakuvid.online/embed/osjvl5b6ubmr",
            "https://vibeplayer.site/a9bc97b7092b5bc1"]:
    html = fetch(url)
    m = m3u8_re.search(html)
    if m:
        print("DIRECT:", url, "→", m.group(1)[:80]); continue
    unpacked = unpack(html)
    if unpacked:
        m = m3u8_re.search(unpacked)
        print("UNPACKED:", url, "→", m.group(1)[:80] if m else "no m3u8")
```

The output (paraphrased):

```
UNPACKED: https://otakuhg.site/e/d6i8zmd97cpw     → https://....premilkyway.com/.../master.m3u8
UNPACKED: https://otakuvid.online/embed/osjvl5b6ubmr → https://....dramiyos-cdn.com/.../master.m3u8
DIRECT:   https://vibeplayer.site/a9bc97b7092b5bc1 → https://vibeplayer.site/public/stream/.../master.m3u8
```

Three different embed providers, three working m3u8 URLs. The
post-selector pipeline — `extractDirectLink`, `unpackJsPacked`,
`findStreamUrl` — is **fine**. The break is entirely in the layer above:
URLs and selectors. That tells us the rewrite is contained and lets us
delete dead code (the AJAX fallback) with confidence instead of nervously
keeping it "just in case."

### Step 6: Write the new contract

With everything we just learned, the new `GogoSite` is a much smaller
object than the old one. The previous version carried selectors and tokens
for two parallel extraction paths (primary scrape + AJAX fallback) and for
two legacy markup shapes (current and "some mirrors still ship this").
Anineko serves one shape, on one path, with full server-side rendering, so
half of those slots can be deleted.

```kotlin
// data/.../anime/GogoSite.kt — new contract
internal object GogoSite {
    const val HOST = "https://anineko.to"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    const val DEFAULT_QUALITY = "Auto"

    object Paths {
        fun search(query: String): String = "$HOST/browse?keyword=$query"
        fun anime(slug: String): String = "$HOST/watch/$slug"
        fun absoluteUrl(p: String): String = if (p.startsWith("http")) p else "$HOST$p"
    }

    object Selectors {
        const val SEARCH_RESULTS = "article.nv-browse-card a.nv-anime-thumb"
        const val EPISODE_LINKS_PRIMARY = "a.nv-info-episode-main"
        const val SERVER_LINKS_PRIMARY = "a.nv-server-btn.server-video[data-video]"  // ◄ wrong, fixed later
    }

    object Tokens {
        const val SERVER_BUTTON_TEXT = "Choose this server"
        const val CATEGORY_PREFIX = "/watch/"
        const val EPISODE_PATH_FRAGMENT = "/ep-"
    }

    object Patterns {
        val EPISODE_NUMBER = Regex("""/ep-(\d+)""")
        // M3U8_URL, MP4_URL, VTT_URL, PACKED_JS unchanged — embed side still works
        val M3U8_URL = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""")
        val PACKED_JS = Regex(
            """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)""",
        )
    }
}
```

The parser collapses similarly. `search()` reads the title from `img.alt`
(anineko has no `title` on the anchor). `getEpisodes()` drops the entire
AJAX fallback branch and the `data-num`/`EP` prefix logic, replaced with
one regex on the href. `getStreamLinks()` drops its fallback selector path.
Net diff: −25 lines from the parser, two URL patterns, four dead-selector
constants gone.

The build went green. We assumed we were done. We were not.

## The Second Problem: Nothing Loads, No Error Either

After the rebuild, the user said:

> error screen is not showing but nothing is loading

This is a *different* failure shape from the original "all anime fail."
Originally, the entire flow died at the search step — search returned an
empty list, the VM saw `animeSources.isEmpty()`, and surfaced "Anime not
found on source." That branch was wired correctly.

The new symptom — "no error, but nothing plays" — meant the flow had
progressed past the search step, found an anime, and then **silently
parked** at a state with no video and no error. That has to be a code path
we hadn't looked at.

### Step 7: Trace the silent-fail path

The ViewModel's stream-loading logic looks like this:

```kotlin
private suspend fun loadEpisodeFromSource(slug: String) {
    val links = loadEpisodeStream(slug, episodeNum)
    if (links == null) {
        _uiState.update { it.copy(isLoading = false, error = "Episode $episodeNum not found") }
        reportPlayerError("Episode not found on source")
        return
    }
    _uiState.update {
        it.copy(
            streamLinks = links,
            selectedLink = links.firstOrNull(),
            isLoading = false,
        )
    }
}
```

`loadEpisodeStream` returns `List<StreamLink>?`. The contract is "null
means the episode isn't on the source's list; an empty list means we
looked but didn't extract anything." The VM handles the `null` case with a
real error message. **It does not handle the empty-list case at all.**

If `links.isEmpty()`, the else branch fires, sets `isLoading = false`,
sets `streamLinks = []`, sets `selectedLink = null` (from
`links.firstOrNull()`), and clears nothing — so `error` stays at its
previous value (which, after `retry()`, is `null`). The UI sees: not
loading, no stream, no error.

The player screen then renders nothing — there's no spinner because
`isLoading` is false, no error card because `error` is null, no video
surface because `selectedLink` is null. It looks like the app just gave
up.

```
┌──────────────────────────────────────────────────────────────────────┐
│ The silent-fail branch, drawn out                                    │
│                                                                      │
│   loadEpisodeStream(slug, ep) returns:                               │
│      │                                                               │
│      ├── null         → "Episode N not found" surfaces ✓             │
│      │                                                               │
│      ├── List(N>0)    → first stream plays ✓                         │
│      │                                                               │
│      └── emptyList()  → isLoading=false, error=null, selectedLink=null │
│                         → UI shows nothing, no way to recover         │
└──────────────────────────────────────────────────────────────────────┘
```

So we have two bugs to fix:

1. **The VM should not silently absorb `emptyList()`.** That branch should
   surface a real error, just like the null branch.
2. **`getStreamLinks` is returning `emptyList()`,** and we need to figure
   out why — because if the fix to bug 1 only converts "nothing loads"
   into "shows an error," the user still can't watch anything.

### Step 8: Patch the VM (defense, not cure)

The defense-in-depth fix is one branch:

```kotlin
private suspend fun loadEpisodeFromSource(slug: String) {
    val links = loadEpisodeStream(slug, episodeNum)
    if (links == null) {
        _uiState.update { it.copy(isLoading = false, error = "Episode $episodeNum not found") }
        reportPlayerError("Episode not found on source")
        return
    }
    if (links.isEmpty()) {                                          // ◄ NEW
        _uiState.update { it.copy(isLoading = false, error = "No playable streams found") }
        reportPlayerError("No playable streams extracted from embeds")
        return
    }
    _uiState.update {
        it.copy(streamLinks = links, selectedLink = links.firstOrNull(), isLoading = false)
    }
}
```

That doesn't make anything play, but it guarantees the next time something
in the embed pipeline breaks, the user sees an error message and reports
it to us within minutes instead of staring at a blank player and assuming
the app crashed.

### Step 9: Find out why streams are empty — with a smoke test

This is where the curl-driven investigation that worked perfectly for the
rebuild *started lying to us*. Probing with curl showed five usable embed
URLs on the episode page:

```bash
$ curl -s "https://anineko.to/watch/naruto-shippuden/ep-1" \
    | grep -oE 'data-video="[^"]+"' | wc -l
12
```

Twelve `data-video` attributes. Plenty of stuff to pick. But our parser
was getting zero. The disagreement between "curl shows 12 things" and
"our app shows 0 streams" had to live somewhere in our use of Jsoup.

A smoke test against the live site is the right tool here. We added one
in `:data/src/androidUnitTest/`:

```kotlin
// data/.../GogoSelectorSmokeTest.kt
@Test
fun `server-embed selector matches at least one embed`() {
    val doc = Jsoup.connect("${GogoSite.HOST}/watch/naruto-shippuden/ep-1")
        .userAgent(GogoSite.USER_AGENT)
        .timeout(10_000)
        .get()
    val servers = doc.select(GogoSite.Selectors.SERVER_LINKS_PRIMARY)
    assertTrue(servers.size > 0)
}
```

Three tests like this — one for search, one for episode list, one for
server embeds. The first two passed. The third failed:

```
servers found: 0
```

So Jsoup, when running this exact selector against this exact page, found
zero matches. That ruled out network-level differences (Cloudflare, IP
geo, bot detection) — we were getting the right HTML, we just weren't
selecting anything from it.

### Step 10: The selector ladder — narrow the failure

To find the selector "boundary" between "matches" and "doesn't match," we
added a tiny diagnostic step to the smoke test that tried a ladder of
progressively weaker selectors and printed the match count for each:

```kotlin
listOf(
    "a[data-video]",
    "a.server-video",
    "a.nv-server-btn",
    "a.nv-server-btn.server-video",
    "a.nv-server-btn.server-video[data-video]",
    ".server-items a[data-video]",
    ".nv-server-grid a[data-video]",
    "[data-video]",
).forEach { sel ->
    println("[$sel] -> ${doc.select(sel).size}")
}
```

The output told the whole story in one line:

```
[a[data-video]]                                  -> 0
[a.server-video]                                 -> 0
[a.nv-server-btn]                                -> 0
[a.nv-server-btn.server-video]                   -> 0
[a.nv-server-btn.server-video[data-video]]       -> 0
[.server-items a[data-video]]                    -> 0
[.nv-server-grid a[data-video]]                  -> 0
[[data-video]]                                   -> 12   ◄── HERE
```

**Every selector that started with `a` matched zero. The selector that
made no claim about the tag name matched twelve.**

The embeds aren't anchors. They're some other tag. We just had to ask the
DOM what tag it actually was.

### Step 11: The reveal

The test then dumped 500 characters of raw HTML around the first
`data-video=` occurrence:

```html
<button class="nv-server-btn server-video server default" type="button"
        data-video="https://vibeplayer.site/a9bc97b7092b5bc1"
        data-tab="tab_0">
    HD-1                                                <span>Hard Sub</span>
</button>
```

They're `<button>` elements. Not anchors.

This wasn't visible in our earlier `grep -oE 'data-video="[^"]+"'` probe
because `grep -oE` just extracts the matching substring — it strips away
everything before the matched text, including the tag name. The string
`data-video="..."` looked exactly the same whether it sat on an `<a>`, a
`<button>`, a `<div>`, or anything else. We had been mentally substituting
"those were anchors on the old site, so they're probably anchors on the
new one" without ever verifying.

The old anitaku.to site put server embeds on anchors:

```html
<li class="server"><a class="server-video" data-video="...">HD-1</a></li>
```

Anineko uses a button-driven UI:

```html
<button class="nv-server-btn server-video server" data-video="...">HD-1 …</button>
```

Different tag, same data. CSS-selector-wise, completely different.

### Step 12: Fix the selector, verify, ship

```kotlin
// data/.../anime/GogoSite.kt
- const val SERVER_LINKS_PRIMARY = "a.nv-server-btn.server-video[data-video]"
+ /** Server-embed buttons on the episode page. `<button>` (NOT `<a>`)
+  *  — the `data-video` attr carries the embed URL; the button text
+  *  carries the server name (e.g. "HD-1 Hard Sub", "Earnvids Hard Sub"). */
+ const val SERVER_LINKS_PRIMARY = "button[data-video]"
```

We deliberately used the weakest selector that still describes the intent:
`button[data-video]`. Not `button.nv-server-btn[data-video]` and not
`button.server-video.nv-server-btn[data-video]`. The reason is the same
reason the bug existed in the first place: if anineko renames its CSS
classes next week, the class-bound selector breaks; the tag-and-attribute
form keeps working as long as embed URLs remain on buttons.

The comment matters too. It tells the next person reading this code
*exactly* what surprised the previous reader, so they don't repeat the
mistake. The case for "no comment unless the why is non-obvious" applies
to most code, but a scraper that depends on third-party markup is one of
the rare cases where calling out a non-obvious historical gotcha is
durably useful.

With the selector fixed, the smoke test went green and an end-to-end
verification by curling + Python-simulating the extractor showed all three
working embed providers (`vibeplayer.site`, `otakuhg.site`,
`otakuvid.online`) yield extractable m3u8 streams.

## The Smoke Test as a Permanent Artifact

We kept the smoke test in the repo, marked `@Ignore`:

```kotlin
@Ignore("live-network probe; un-Ignore and run locally when verifying GogoSite selectors")
class GogoSelectorSmokeTest {
    // ... three tests, one per selector
}
```

There's a real tension here. On one hand, network-dependent tests run on
every CI build will flake at the worst moments — a Cloudflare hiccup
fails the build during an unrelated PR review. On the other hand, the
test we just wrote is *exactly* the test that would have caught the next
rebrand before users ever hit it.

The `@Ignore` is the compromise. The test is documentation of what
selectors should match against the live site, it's a one-keystroke
diagnostic when scraping breaks (un-comment the `@Ignore`, run, read), and
it doesn't make CI flaky. The cost of remembering to run it is real, but
the cost of remembering is bounded — only when the player breaks — and
it's *much* lower than the cost of dealing with random CI failures from
upstream outages.

In a project with more contributors, the right answer might be to move
this test into a "nightly" or "weekly" CI lane that's allowed to fail
without blocking PRs. For a solo-maintained app, the `@Ignore` lane is
fine.

## Where the Same Pattern Lives in the Codebase

This isn't an isolated risk. Every scraper integration we have is one
markup change away from this same class of failure. The manga side has
the same shape on a different host:

| Component                  | Lives at                                                  | Risk            |
|----------------------------|-----------------------------------------------------------|-----------------|
| `GogoParser`               | `data/.../anime/GogoParser.kt`                            | anime streams   |
| `GogoSite` (contract)      | `data/.../anime/GogoSite.kt`                              | anime streams   |
| `MangaPillParser`          | `data/.../manga/MangaPillParser.kt`                       | manga pages     |
| `MangaDexParser`           | `data/.../manga/MangaDexParser.kt`                        | manga pages     |
| `ANNNewsSource`            | `data-android/.../remote/news/ANNNewsSource.kt`           | news headlines  |
| AniList GraphQL            | `app/.../data/remote/AnilistApi.kt`                       | metadata        |
| Jikan REST                 | `data-android/.../remote/news/JikanScheduleSource.kt`     | airing schedule |
| AniSkip REST               | `data-android/.../remote/AniSkipApi.kt`                   | OP/ED skipping  |

The GraphQL and REST integrations (AniList, Jikan, AniSkip) are much less
fragile — they have versioned API contracts, breaking changes are usually
announced, and a 5xx from one of them surfaces as a real error. The
HTML-scraper integrations (Gogo, MangaPill, ANN, MangaDex's web extras)
are the ones to worry about. Every one of them deserves the same shape of
smoke test that `GogoSelectorSmokeTest` now provides for the anime side.

## Key Takeaways

1. **Static "we moved" landing pages serve `200 OK`.** A naive health
   check that only watches status codes will report green while every
   user is silently broken. The cheapest fix is to add a content-shape
   assertion to any probe: "200 + body contains an expected token" is
   the minimum bar, not just "200."

2. **Silent-fail branches in a state machine compound upstream failures
   into invisible ones.** The ViewModel had one — an empty list from
   `loadEpisodeStream` set `isLoading = false` and cleared nothing else.
   It only mattered the day an upstream change started returning empty
   lists. Audit your state machines for branches that null-coalesce or
   `firstOrNull()` into a hidden "do nothing" state, and surface them as
   errors.

3. **`grep -oE` strips the tag name.** Probing HTML for the existence of
   an attribute tells you nothing about what element it's on. When you're
   about to write a selector based on what you saw in a curl probe, dump
   200 characters of surrounding context and verify the tag. We lost
   half a debug cycle to this.

4. **A selector ladder is the fastest diagnostic for "Jsoup matches zero."**
   When a selector you're sure should match finds nothing, list five or
   six progressively weaker selectors and run them all. The boundary
   between "matches" and "doesn't" tells you exactly what claim in your
   selector was wrong — class name? tag name? attribute? Saves you from
   guessing at five hypotheses sequentially.

5. **Simulate the post-selector pipeline in Python before rewriting
   anything in Kotlin.** The embed-side logic — regex extraction, packed
   JS unpacking, m3u8 detection — is regex-and-string work that ports
   cleanly between languages. Thirty lines of Python against the live
   site told us our existing extractor would survive the rebrand, which
   bounded the rewrite to "URLs and selectors only" with confidence
   instead of fear.

6. **A site rebrand is a chance to delete code, not just rewrite it.**
   The old `GogoSite` had an entire AJAX fallback path for paginated
   episode lists, plus a "legacy markup" fallback selector for "some
   mirrors still ship this." Anineko serves one shape on one path with
   server-side rendering; the fallbacks would never run. Deleting them
   shrank the parser by 25 lines and the contract object by half. The
   instinct to keep dead code "just in case" is what made the original
   parser big in the first place; if the original code couldn't be
   trusted, the dead code certainly can't.

7. **A weak selector is better than a precise one for third-party
   markup.** `button[data-video]` is more durable than
   `button.nv-server-btn.server-video[data-video]`. Class-name
   reshuffles are common; tag-and-attribute pairs are stable. Be as
   permissive as the surrounding code's filtering allows — the parser
   already throws away anchors with empty `data-video` and empty server
   names, so over-matching costs nothing.

8. **A `@Ignore`d live smoke test is documentation, a diagnostic, and a
   reverse-engineering log all at once.** It tells the next reader what
   selectors are *supposed* to match. It's a one-keystroke
   "is the site still alive?" check. And the test bodies double as a
   record of which slugs we used to verify (so re-verification next time
   is "run the same test with the same slugs" rather than "find a
   live-as-of-today URL"). The `@Ignore` keeps it out of CI; the
   in-file comment explains when to un-Ignore.

9. **Curl is for surface anatomy. Jsoup is for what your code sees.**
   Curl is faster for "is the host alive, what's the URL pattern, what's
   the rough shape of the markup." Jsoup running in a unit test is the
   one tool that has the *exact same* HTML parser, header set, redirect
   handling, and cookie behavior as the production code. When the two
   disagree, you have a bug in the bridge between them — that's what
   happened here.

## Reverse-Engineering Checklist for the Next Rebrand

When the next third-party source rotates, run through this list in order.
Each step rules out the kind of failure that step looks at, before
sinking time into the next step.

1. **Is our side identical to what it was when the feature worked?**
   `git log` on the parser and its consumers. Rule out "we broke it."

2. **Is the host responding at all?** `curl -sI <baseUrl>`. If DNS fails
   or it's not 200, the host is dead — find a replacement.

3. **Is the host returning real content or a parking page?** `curl -s
   <baseUrl> | head -40`. Look for the `<title>` and `meta refresh`.
   `200 OK` plus "We Have Moved" plus a redirect target is the rebrand
   signal. Note the redirect target — it's the new host.

4. **Does the old URL pattern still 200 on the new host?** Probably no.
   Find the new pattern by curling the new host's home page and pulling
   nav links: `curl -s https://newhost/ | grep -oE 'href="[^"]+"' | sort -u`.
   The site map is in there.

5. **Walk a representative user flow on the new host.** Search →
   detail → child page. For each page, save the HTML to `/tmp/` and look
   for the markup that wraps a known result. Identify selectors that
   yield exactly one element per item.

6. **Verify the post-selector pipeline still works.** Whatever extraction
   logic comes after the selectors (regex, unpacking, decoding) should be
   probed independently against a sample of the new payloads. Python is
   the right scratch-pad. If extraction breaks, that's a much bigger
   rewrite; if it works, the rewrite is contained.

7. **Update the contract object.** Centralize the URL paths, selectors,
   regex patterns, and string tokens in one file (we have `GogoSite` for
   anime, `MangaPillSite` analog for manga). Comment any non-obvious
   gotcha — especially the cross-tag ones that bit on this rebrand.

8. **Write a smoke test before you trust the rebuild.** Three Jsoup
   probes — one per selector — against known live URLs. Run it against
   the new site. If it passes, the rebuild is wired right. If it fails,
   the selector is wrong and the smoke test will say which one.

9. **Audit the VM for silent-fail branches.** The empty-list-from-the-
   repository case is the most common. If the VM has a path that ends
   in `isLoading = false` without setting `error`, that path is a future
   silent failure. Plug it now while the integration is fresh in your
   head, not when the next break drops a user into it.

10. **`@Ignore` the smoke test before committing.** Keep it in the repo
    for the next rebrand. Note in its comment when to un-Ignore.

## Closing Thought

Every scraper integration is a contract with a third party who didn't
sign anything. The contract holds because the markup happens to match
what we wrote down. The day it stops matching, our app stops working —
and if our code is *too* silent about the failure, our users stop
trusting the app long before we even know there's a bug to fix.

The defense isn't "make the integration unbreakable" (impossible) or
"check for breakage every minute" (overkill). It's two cheap habits:
write down the markup contract explicitly in one place so the fix is
surgical when the contract breaks, and make every state-machine branch
either *load* something or *surface an error* — never both quiet.
