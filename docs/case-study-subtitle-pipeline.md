# Case Study: Why Frieren Episode 1 Had No Subtitles

## The Problem

Watching *Sousou no Frieren* episode 1 in the player. Audio plays. Video
renders. The "CC" button on the player UI is visible but tapping it does
nothing. There is no error, no toast, no log line in the user-visible UI.
Just no subtitles.

The settings gear next to the title bar also did nothing — `onClick` was a
literal `/* settings */` placeholder.

Three independent bugs were stacked on top of each other, each one masking
the next. The user only saw the surface symptom: "no subs."

```
┌─────────────────────────────────────────────────────────────────┐
│ What the user saw                                               │
│                                                                 │
│   Episode plays ✓                                               │
│   Subtitle icon is grey but tappable                            │
│   Tap subtitle icon → nothing happens                           │
│   Tap settings gear → nothing happens                           │
│                                                                 │
│ What was actually happening, from outside in                    │
│                                                                 │
│   Layer 1 (UI): icon's onClick had no fallback for the          │
│                 "no subs available" branch — silent no-op       │
│   Layer 2 (Parser): subs WERE in the embed URL but the          │
│                     parser missed them — wrong convention       │
│   Layer 3 (Player): even when subs reached the renderer,        │
│                     ExoPlayer crashed and silently retried      │
│                     video-only — Media3 default change          │
└─────────────────────────────────────────────────────────────────┘
```

## The Investigation

### Step 1: Instrument before guessing

The first instinct on a "feature doesn't work" bug is to read the relevant
code and try to spot the bug. With three layers stacked, that approach hits
the first issue, "fixes" it, and leaves the next two intact for users to
hit later.

The faster path is to **add structured logs across every layer**, run the
scenario once, and read the logs end to end. The bug usually announces
itself.

We added log statements at four places in `GogoParser.kt`:

```kotlin
// In parseSubtitlesFromUrl — when zero caption_X params are found,
// dump the actual query keys so we can see what convention the host used
if (tracks.isEmpty()) {
    val keys = uri.queryParameterNames.joinToString(",")
    Log.d("GogoParser", "    parseSubtitlesFromUrl: no caption_X params (queryKeys=[$keys])")
}

// In parseSubtitlesFromUnpacked — track count of .vtt URLs found in JS
Log.d("GogoParser", "    parseSubtitlesFromUnpacked: found ${tracks.size} .vtt URL(s)")

// In extractDirectLink — log which extraction path won
Log.d("GogoParser", "    extract path=raw-m3u8 | subs=${subtitles.size}")
Log.d("GogoParser", "    extract path=unpacked-m3u8 | subs=${allSubs.size}")
// (... and equivalents for raw-mp4, unpacked-mp4, no-stream)

// In extractDirectLink's catch block — previously silent
Log.e("GogoParser", "    extractDirectLink threw for $name", e)
```

The player side already had decent logging, so we left it alone. Then we
ran the app, picked Frieren ep 1, and read the logs.

### Step 2: Read the logs

The first server's log line was the smoking gun:

```
GogoParser  D  parseSubtitlesFromUrl: no caption_X params (queryKeys=[sub])
GogoParser  D  Server: HD-1 | embed: https://vibeplayer.site/a5bea5237ea4b378
                             ?sub=https://cdn.cimovix.store/.../...eng-2.vtt | subs: 0
GogoParser  D    extract path=raw-m3u8 | subs=0
```

Three lines, three signals:

1. The parser found **zero** `caption_X` parameters. `queryKeys=[sub]` told
   us the actual key the host used — single `sub` (no underscore, no index).
2. The embed URL on `vibeplayer.site` plainly contains a real subtitle URL
   in `?sub=https://cdn.cimovix.store/.../...eng-2.vtt`.
3. We extracted the stream successfully but with `subs=0`.

So we had data the parser was throwing away. Two extraction conventions
exist in the wild, and our parser only handled one:

| Convention | Used by | Format |
|------------|---------|--------|
| Indexed multi-track | `otakuhg.site`, `otakuvid.online` | `?caption_1=<url>&sub_1=English&caption_2=...` |
| Bare single track | `vibeplayer.site` | `?sub=<vtt-url>` |

The same logs also confirmed which servers genuinely had no subs. Five
servers with embed URLs like `https://otakuhg.site/e/3lcsjn8lm9w9` (no
query string at all) returned `queryKeys=[]` *and* `parseSubtitlesFromUnpacked:
found 0 .vtt URL(s)`. Those are hard-subbed sources — subtitles burned
into the video pixels — and there is nothing for the parser to find. Worth
distinguishing from "we missed something."

### Step 3: Fix the parser

```kotlin
private fun parseSubtitlesFromUrl(embedUrl: String): List<SubtitleTrack> {
    val tracks = mutableListOf<SubtitleTrack>()
    try {
        val uri = Uri.parse(embedUrl)
        // Convention 1: indexed caption_X + sub_X (otakuhg, otakuvid).
        var i = 1
        while (true) {
            val captionUrl = uri.getQueryParameter("caption_$i") ?: break
            val label = uri.getQueryParameter("sub_$i") ?: "Track $i"
            tracks.add(SubtitleTrack(url = captionUrl, label = label))
            i++
        }
        // Convention 2: bare ?sub=<vtt-url> (vibeplayer.site). The URL itself
        // is the subtitle; no separate label key — default to English.
        if (tracks.isEmpty()) {
            val bareSub = uri.getQueryParameter("sub")
            if (!bareSub.isNullOrBlank() && bareSub.startsWith("http", ignoreCase = true)) {
                tracks.add(SubtitleTrack(url = bareSub, label = "English"))
            }
        }
        // ... diagnostic log on the still-empty case
    } catch (e: Exception) {
        Log.e("GogoParser", "    parseSubtitlesFromUrl threw", e)
    }
    return tracks
}
```

Built, redeployed, replayed Frieren ep 1. The logs were now:

```
GogoParser  D  Server: HD-1 | embed: ...?sub=...eng-2.vtt | subs: 1
GogoParser  D    Sub: English → https://cdn.cimovix.store/.../eng-2.vtt
GogoParser  D    extract path=raw-m3u8 | subs=1
VideoPlayer D  Subtitles: 1 tracks
VideoPlayer D    [0] English (en): https://cdn.cimovix.store/.../eng-2.vtt
VideoPlayer D    ✓ Created subtitle source: English → ...
VideoPlayer D    Merging 1 subtitle sources with video
VideoPlayer D    Text tracks enabled, preferred language: en
```

Every layer happy. Subtitle reached the player. Track was enabled. Two
seconds later, in the same log dump:

```
VideoPlayer E  Playback error with subtitles — retrying video-only
VideoPlayer E  ExoPlaybackException: Unexpected runtime error
VideoPlayer E    Caused by: java.lang.IllegalStateException:
                 Legacy decoding is disabled, can't handle text/vtt samples
                 (expected application/x-media3-cues).
VideoPlayer E      at androidx.media3.exoplayer.text.TextRenderer
                       .assertLegacyDecodingEnabledIfRequired(TextRenderer.java:615)
```

Bug #1 was the UI silence. Bug #2 was the parser miss. Bug #3 was buried
underneath bug #2, only became reachable once subs actually arrived at the
renderer.

### Step 4: Decode the renderer error

In Media3 1.4+ (we're on 1.5.1), the default `TextRenderer` was rewritten
to expect **pre-parsed** `application/x-media3-cues` samples instead of
raw subtitle formats. The parsing moved upstream into the extractor.

Our pipeline:

```
┌──────────────────────────────────────────────────────────────────┐
│  How sideloaded subtitles flow through the player                │
│                                                                  │
│   GogoParser builds StreamLink with subtitles list               │
│         │                                                        │
│         ▼                                                        │
│   For each subtitle URL, build a MediaItem.SubtitleConfiguration │
│         │                                                        │
│         ▼                                                        │
│   Wrap in SingleSampleMediaSource (ships raw text/vtt samples)   │
│         │                                                        │
│         ▼                                                        │
│   MergingMediaSource(videoSource, subtitleSource1, ...)          │
│         │                                                        │
│         ▼                                                        │
│   ExoPlayer.setMediaSource(merged) → prepare() → play()          │
│         │                                                        │
│         ▼                                                        │
│   TextRenderer.onStreamChanged()  ◄── Crash point in 1.4+        │
│     "I expected application/x-media3-cues, got text/vtt"         │
│     throws IllegalStateException                                 │
└──────────────────────────────────────────────────────────────────┘
```

`SingleSampleMediaSource` doesn't run an extractor. It hands raw bytes to
the renderer with the configured MIME type. So
`DefaultMediaSourceFactory.experimentalParseSubtitlesDuringExtraction(true)`
— the canonical "modern" toggle — doesn't apply here; there is no
extraction pipeline to flip a flag on.

The only way to make `TextRenderer` accept raw `text/vtt` in 1.5.1 is to
call `experimentalSetLegacyDecodingEnabled(true)` on the `TextRenderer`
itself. But there's no factory-level helper for it — the toggle exists
only as an instance method on the renderer. Solution: subclass
`DefaultRenderersFactory` and override the hook that constructs the
text renderer.

```kotlin
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            TextRenderer(output, outputLooper).apply {
                experimentalSetLegacyDecodingEnabled(true)
            },
        )
    }
}
ExoPlayer.Builder(context, renderersFactory)
    .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
    .build()
```

That's it. After this change, the same Frieren ep 1 plays with subtitles
visible.

## The Other Half of the Task: User Feedback

With the data pipeline working, we still had a UX problem: the subtitle
icon's `onClick` had **no behaviour** for the case where a stream genuinely
had no subtitles. From the user's perspective, "tap does nothing" looks
identical regardless of cause.

Before:

```kotlin
IconButton(
    onClick = {
        // Toggle subtitle track on/off
        val trackParams = exoPlayer.trackSelectionParameters
        val currentlyEnabled = ...
        val hasTextTracks = playerState.selectedLink?.subtitles?.isNotEmpty() == true
        if (hasTextTracks) {
            // toggle
        }
        // else: silently do nothing  ← the bug
    },
)
```

After:

```kotlin
val hasSubs = playerState.selectedLink?.subtitles?.isNotEmpty() == true
IconButton(
    onClick = {
        if (!hasSubs) {
            Toast.makeText(
                context,
                "No subtitles available for this episode",
                Toast.LENGTH_SHORT,
            ).show()
            return@IconButton
        }
        // toggle text-track rendering on/off
        ...
    },
) {
    Icon(
        Icons.Default.Subtitles,
        "Subtitles",
        tint = if (hasSubs) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(20.dp),
    )
}
```

Two changes:

1. The icon now visibly **dims** when there are no subs — it stops
   pretending to be an active control.
2. If the user taps anyway, they get a toast — the negative answer is
   still an answer.

The settings gear got the same treatment but more substantial — a
playback-speed bottom sheet wired to `exoPlayer.setPlaybackSpeed(speed)`,
since "tap does nothing" was its only behaviour previously. Quality
switching and audio track were deliberately deferred — they need new
ViewModel state and the parser doesn't currently surface server
alternatives.

## The Pretty-Log Detour

While debugging, the new logs we added went into Logcat through
Android's plain `D/GogoParser:` prefix. The project already has
[Pretty-Log](https://github.com/theshid/Pretty-Log) wired up — it's
initialized in `MiyoApplication.onCreate()` with a Sentry-breadcrumb
forwarding wrapper — but five files still imported `android.util.Log`
and never reached it.

```kotlin
// in five files
import android.util.Log  // ← bypasses Pretty-Log's formatter
```

A one-line swap in each file routed everything through the configured
`PrettyLoggingService`:

```kotlin
import io.github.theshid.prettylog.Log
```

Pretty-Log's API is signature-compatible with `android.util.Log` for
`Log.d`, `Log.i`, `Log.e(tag, msg, throwable)` — so call sites didn't
need to change. The one exception: `Log.w` doesn't take a `Throwable`
parameter in Pretty-Log. Five call sites used `Log.w(tag, msg, e)`, which
we promoted to `Log.e(tag, msg, e)` to preserve the stack trace. That's
a small severity bump, the alternative was losing the trace.

After the swap, every log is boxed, tagged with thread/caller info, and
auto-forwarded to Sentry as a breadcrumb on Info+ — so a future crash
report carries the last ~100 lines of context.

This wasn't strictly required to fix the Frieren bug, but it's the
"sharpen the saw" lesson: if you have logging infrastructure, **make
sure your logs actually go through it**. Otherwise the next bug looks
no easier than the last one.

## Key Takeaways

1. **Stacked bugs hide each other.** Three independent issues all
   produced the same end-user symptom ("no subs"). Fixing the topmost
   one (UI feedback) wouldn't have surfaced the parser miss; fixing
   the parser miss revealed a renderer crash that had been latent
   the whole time. **Instrument the whole pipeline before guessing
   which layer is broken** — then you find all the bugs in one trip
   instead of three.

2. **Web sources have multiple conventions for the same thing.** Our
   parser assumed one subtitle URL convention (`?caption_1=...&sub_1=...`)
   and silently dropped subs from hosts using the bare `?sub=<url>`
   convention. The fix isn't "find the convention" — it's "log what
   the wild data actually looks like" (`queryKeys=[sub]`) and extend
   accordingly. Real-world parsers always end up with multiple branches.

3. **A silent fallback is a debugging tarpit.** Our player had a
   "playback error → retry video-only" recovery path that masked the
   `text/vtt` renderer crash. Recovery is good UX — but the recovery
   path **must log the original failure** loudly. Without it, the
   third bug would have been invisible.

4. **Library default changes are silent landmines.** Media3 1.4+
   flipped `TextRenderer`'s default to require pre-parsed cues. There
   was no migration warning for code that uses `SingleSampleMediaSource`
   directly because that path is technically deprecated, even though
   it still compiles and runs (and indeed is still emitted by the
   1.5.1 sources jar). Always read major-version changelogs for
   media stacks; "deprecated but still works" is rarely the same as
   "still works correctly."

5. **`A` button doing `nothing` is a worse bug than `A` button
   crashing.** A user can report a crash. They cannot report
   `nothing`. The empty-case branch needs a fallback every time —
   either a visual disabled state, a toast, a snackbar, *something*
   that makes the negative answer legible. Our subtitle button
   conformed to none of those before this fix; the gear button was
   actively misleading.

6. **Make the diagnostic logs themselves work first.** Half the time
   spent on this bug went into noticing that Pretty-Log was wired up
   but bypassed. If the format is unreadable or the routing is wrong,
   you'll do the right investigation and still miss the answer.

## The Subtitle Pipeline (Reference)

```
┌────────────────────────────────────────────────────────────────────┐
│  Final shape of the subtitle pipeline after this case study        │
│                                                                    │
│   ┌─────────────────────┐                                          │
│   │ GogoParser          │  Two URL conventions handled:            │
│   │  parseSubtitles     │  - ?caption_1=<url>&sub_1=<label>... ✓   │
│   │   FromUrl           │  - ?sub=<url>                       ✓   │
│   │   FromUnpacked      │  - .vtt regex over unpacked JS      ✓   │
│   └──────────┬──────────┘                                          │
│              │ List<SubtitleTrack>                                 │
│              ▼                                                     │
│   ┌─────────────────────┐                                          │
│   │ StreamLink          │  Carries url + headers + subtitles      │
│   └──────────┬──────────┘                                          │
│              │                                                     │
│              ▼                                                     │
│   ┌─────────────────────┐                                          │
│   │ VideoPlayerScreen   │  For each subtitle:                      │
│   │                     │  → MediaItem.SubtitleConfiguration       │
│   │                     │  → SingleSampleMediaSource (raw VTT)     │
│   │                     │  → MergingMediaSource(video, subs...)    │
│   └──────────┬──────────┘                                          │
│              │                                                     │
│              ▼                                                     │
│   ┌─────────────────────────────────────────────────────────┐      │
│   │ ExoPlayer.Builder(context, renderersFactory)            │      │
│   │                                                         │      │
│   │ renderersFactory: object : DefaultRenderersFactory {    │      │
│   │   override buildTextRenderers { out ->                  │      │
│   │     out += TextRenderer(...).apply {                    │      │
│   │       experimentalSetLegacyDecodingEnabled(true)        │      │
│   │     }                                                   │      │
│   │   }                                                     │      │
│   │ }                                                       │      │
│   └──────────┬──────────────────────────────────────────────┘      │
│              │                                                     │
│              ▼                                                     │
│   ┌─────────────────────┐                                          │
│   │ User sees subs ✓    │                                          │
│   │ Toggle button works │                                          │
│   │ (Empty case shows   │                                          │
│   │  toast + dim icon)  │                                          │
│   └─────────────────────┘                                          │
└────────────────────────────────────────────────────────────────────┘
```

## Debugging Checklist for "Subtitles Don't Show"

1. **Log at the parser.** Print every embed URL, every `queryParameterNames`,
   and the resulting `subtitles.size`. If `size == 0`, you'll see
   immediately whether the source provides subs or not.
2. **Distinguish hard-sub from soft-sub sources.** `queryKeys=[]` and
   `parseSubtitlesFromUnpacked: found 0` together means the source is
   genuinely hard-subbed — not a bug.
3. **Log the player-side track count.** `Subtitles: N tracks` in
   `VideoPlayer` confirms data crossed the parser-to-player boundary.
4. **Check for silent ExoPlayer errors.** `IllegalStateException: Legacy
   decoding is disabled` is the canonical Media3 1.4+ symptom. Any
   `Caused by` in `TextRenderer.assertLegacyDecodingEnabledIfRequired`
   means the renderer rejected the format.
5. **UI fallback exists.** Tapping the subtitle icon when no subs are
   available should show a toast, not silently do nothing. Same goes for
   any empty-state control.
