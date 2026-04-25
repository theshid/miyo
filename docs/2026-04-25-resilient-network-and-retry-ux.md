# Two layers of resilience: invisible retries, visible feedback

Date: 2026-04-25

A real Sentry report came in:

```
Socket timeout has expired [url=https://graphql.anilist.co/, …]
io.ktor.client.plugins.HttpTimeoutKt:239 in SocketTimeoutException
```

The user had also mentioned, separately, hitting an HTTP 502 when
opening the manga tab. Different failures, same outcome from the user's
perspective: *blank screen, no idea what happened, no way to retry*.

This is the design of the fix — not because the implementation is
clever, but because the *layering* is. Retries belong at the network
layer. Feedback belongs at the screen layer. Each layer should know as
little as possible about the other.

---

## Part 1 — The problem with one-shot calls

The original `AnilistApi.execute` is a single attempt. Anything that
goes wrong becomes a `null` return:

```kotlin
suspend fun execute(query: String, variables: String = ""): JsonObject? {
    return try {
        val response = client.post(ENDPOINT) { /* … */ }
        // …parse, return jsonObj or null…
    } catch (e: Exception) {
        Log.e(TAG, "execute() failed: ${e.message}", e)
        null
    }
}
```

This is fine for *terminal* failures (bad GraphQL, expired token) where
retrying won't help. It's terrible for *transient* failures
(SocketTimeout, IOException, HTTP 502) where retrying with a brief
delay is exactly what you'd want.

A user on a flaky cell connection sees one timeout out of every three
calls. Without retries, they see blank screens 33% of the time — even
though the API is reachable, just slowly.

## Part 2 — Retry, but not blindly

The retry policy that ships in Miyo:

| Failure                          | Retry? | Why                                |
| -------------------------------- | ------ | ---------------------------------- |
| `SocketTimeoutException`         | Yes    | Network blip, often resolves       |
| `IOException` (incl. ConnectException) | Yes    | Connection reset, DNS hiccup       |
| HTTP 5xx                         | Yes    | Server flake, often resolves       |
| HTTP 4xx                         | **No** | Client error — won't fix itself    |
| JSON parse error                 | **No** | Bad payload — won't fix itself     |
| `CancellationException`          | **No** | User left the screen — leave alone |

The discriminator is "does retrying this have a reasonable chance of a
different outcome?" Anything that's deterministic given the same
request — auth failures, malformed queries, schema mismatches — is *not*
worth retrying and is in fact harmful (you triple the load on the user's
device for a guaranteed-to-fail outcome).

Implementation:

```kotlin
suspend fun execute(query: String, variables: String = ""): JsonObject? {
    val body = buildJsonObject { /* … */ }.toString()

    var lastFailureForRetry: AnilistFailure? = null
    for (attempt in 1..MAX_ATTEMPTS) {
        if (attempt > 1) {
            delay(500L * (1 shl (attempt - 2))) // 500ms, 1500ms
        }
        try {
            val response = client.post(ENDPOINT) { /* … */ }
            val status = response.status.value

            if (status in 500..599) {
                lastFailureForRetry = AnilistFailure.Server(status)
                continue
            }

            // …parse and return…
            _lastFailure.value = null
            return jsonObj
        } catch (e: SocketTimeoutException) {
            lastFailureForRetry = AnilistFailure.Network(e)
        } catch (e: IOException) {
            lastFailureForRetry = AnilistFailure.Network(e)
        } catch (e: Exception) {
            // Non-retriable — bail immediately.
            _lastFailure.value = AnilistFailure.Other(e)
            return null
        }
    }

    _lastFailure.value = lastFailureForRetry
    return null
}
```

Three things to notice:

**Exponential-ish backoff.** 500ms then 1500ms. The total wall-clock
worst case is ~2 seconds across three attempts, which is short enough
that a user staring at a loading spinner doesn't give up, and long
enough that a transient blip has time to clear. Going longer (5s, 10s)
makes the user feel the app is hung; going shorter (instant retry) just
hammers a recovering server.

**Three attempts, not "until success".** Infinite retry loops are
worse than failure — they hide problems by making the app feel slow,
they drain battery, and on cell data they cost the user money. Three
attempts is a sweet spot: covers genuine flakes, gives up on real
outages.

**`MAX_ATTEMPTS` and the loop counter as the only state.** No fancy
RetryPolicy class, no abstraction over backoff strategies. The whole
retry logic is 20 lines and lives next to the call. If we ever need
this elsewhere (the source parsers might), we'll lift it then — not
now.

## Part 3 — The side-channel: `lastFailure`

Twelve callsites in the repo use `api.execute` and pattern-match on
`null`. Refactoring all twelve to consume a `Result<JsonObject>` or a
sealed class of outcomes is a substantial change with no immediate
benefit — most of those calls don't care about the difference between
"empty result" and "failed".

For the three that *do* care — the Home, Anime, and Manga screens —
exposing the last failure as a side-channel costs nothing:

```kotlin
sealed class AnilistFailure {
    data class Network(val cause: Throwable) : AnilistFailure()
    data class Server(val httpStatus: Int) : AnilistFailure()
    data class Other(val cause: Throwable) : AnilistFailure()
}

class AnilistApi(/* … */) {
    private val _lastFailure = MutableStateFlow<AnilistFailure?>(null)
    val lastFailure: StateFlow<AnilistFailure?> = _lastFailure.asStateFlow()
}
```

Set on retry-exhausted failures, cleared on the next successful call.
Any consumer that wants to know "did the most recent network call fail"
can just read `api.lastFailure.value`.

There is a real tradeoff here that's worth being honest about:
**this is a global mutable state shared across all callers.** Two
ViewModels loading concurrently could in theory race — VM A finishes
successfully, clearing the failure that VM B's call had just set.

In practice, for Miyo's flow, this isn't a problem:

- The three screens that read `lastFailure` (Home, Anime, Manga) are
  bottom-tab screens; the user is on one at a time.
- Each screen's `loadX()` fires several concurrent requests that share
  fate — if one times out, they all probably timed out. So a single
  shared `lastFailure` *correctly* represents "did this screen's batch
  of calls fail."
- The check happens *immediately* after `await()` on the last
  `Deferred`, before any other ViewModel could plausibly run.

If two screens *did* race in production, the worst case is that one
screen shows a stale "couldn't load" banner for one render frame, then
the user pulls to refresh. That's an acceptable failure mode.

The cleaner alternative is to thread failure information through the
return type — `suspend fun execute(...): ApiResult<JsonObject>`. That's
the right answer if you're starting fresh. As a retrofit on a working
codebase, it's not worth the churn for the current set of callers.

## Part 4 — The screen layer: how to tell the user

With retry transparently absorbing transient failures, only persistent
ones reach the user. When they do, three things have to happen:

1. The user has to *see* something went wrong.
2. They have to know it's a connectivity problem, not a bug, so they
   can do something about it.
3. They have to have a way to retry without leaving and re-entering
   the screen.

A toast doesn't work — it disappears. A full-screen error replaces
useful UI (header, search bar, navigation) with the error. The right
shape is an inline banner: shown at the top of the screen, between the
search bar and the (empty) sections.

```kotlin
@Composable
fun LoadErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(/* … card with icon, text, retry button … */) {
        Icon(Icons.Default.CloudOff, /* … */)
        Column(modifier = Modifier.weight(1f)) {
            Text("Couldn't load", /* … */)
            Text(message, /* … */)
        }
        Row(modifier = Modifier.clickable(onClick = onRetry)/* … */) {
            Icon(Icons.Default.Refresh, /* … */)
            Text("Retry", /* … */)
        }
    }
}
```

ViewModels translate `AnilistFailure` to user-facing copy:

```kotlin
private fun friendlyMessage(failure: AnilistFailure): String = when (failure) {
    is AnilistFailure.Network ->
        "Couldn't reach AniList. Check your connection."
    is AnilistFailure.Server  ->
        "AniList is having issues (HTTP ${failure.httpStatus})."
    is AnilistFailure.Other   ->
        "Something went wrong loading this page."
}
```

This is the only place in the codebase that converts internal
failure shapes to user-readable copy. Keeping it in one method per VM
(rather than centralized in the banner component) lets each screen
specialize the wording — the Home screen says "loading your home feed",
the Manga screen says "this page", etc.

## Part 5 — The empty-vs-failed distinction

The trickiest piece is deciding *when* to show the banner. Just
"results are empty" is wrong — a brand-new account legitimately has
no entries on their continue-watching list. The check has to be:

```kotlin
val networkFailure = api.lastFailure.value
val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()

_uiState.value = AnimeUiState(
    /* …data fields… */
    error = if (networkFailure != null && allEmpty) {
        friendlyMessage(networkFailure)
    } else null,
)
```

Two conjuncts:
1. **`allEmpty`** — every section is blank. Empty + populated mix means
   the user is seeing *something*, so we shouldn't blanket-block them
   with an error banner.
2. **`networkFailure != null`** — the API actually failed at the
   network layer. Without this guard, a brand-new user with empty
   AniList lists would see "Couldn't reach AniList" — totally false.

Either condition without the other is wrong:

| `allEmpty` | `networkFailure` | Show banner?                       |
| ---------- | ---------------- | ---------------------------------- |
| ✓          | ✓                | **Yes** — real network failure     |
| ✓          | ✗                | No — legit empty, log a warning    |
| ✗          | ✓                | No — partial data, don't alarm     |
| ✗          | ✗                | No — happy path                    |

The third row matters: if 2 of 3 sections loaded and 1 timed out, we
have *enough* data to render usefully and the user can pull-to-refresh
the missing piece. Showing them a banner anyway is annoying.

## Part 6 — The retry button

```kotlin
fun retry() {
    loadAnimeData()
}
```

That's it. The `loadAnimeData` already resets `isLoading = true` and
`error = null` at the top of its body, so calling it again is a clean
restart. The retry button on the banner just calls this method.

This was almost the simplest part of the change, but it's worth calling
out because the alternative (a separate "retry" code path that
duplicates the load logic) is a common mistake. The retry path *is* the
load path. Always.

## What this looks like in practice

A user opens the Manga tab on a flaky 4G connection:

1. `MangaViewModel.loadMangaData()` fires three concurrent AniList
   requests.
2. One of them times out at 15s on the first attempt. Sentry logs an
   `Info`-level breadcrumb. Retry waits 500ms, fires again. Succeeds.
   The user never knew.
3. Later, the user is in a bad-signal area. All three requests time
   out three times across ~6 seconds. `lastFailure` is set.
4. Sections come back empty. `allEmpty && lastFailure != null` →
   banner shows: *"Couldn't load — Couldn't reach AniList. Check your
   connection. [Retry]"*.
5. User walks 10 feet to better signal, taps Retry. Load fires. Works.
   Banner clears. Sections populate.

The Sentry dashboard now only sees *step 3* — three retry-exhausted
failures, grouped into one issue. Not the dozen individual timeouts
that the original code would have logged before any of them had a
chance to succeed.

## Lessons

1. **Layer retries at the network, not the call site.** Twelve repo
   methods don't need to know about backoff. One `execute()` does.

2. **A side-channel `StateFlow` is a cheap retrofit.** Don't refactor
   12 call signatures to thread an `ApiResult` through if you only have
   3 callers that care. Expose the failure where it happens; let the
   callers that care read it.

3. **Distinguish "empty" from "failed".** `allEmpty && failureFlag` is
   the conjunction that catches real problems without alarming
   legitimate empty states.

4. **The retry button calls the load method.** No parallel code paths.
   `fun retry() = load()` is the whole implementation, every time.

5. **Backoff is bounded, not infinite.** 3 attempts, 2 seconds total.
   The user is waiting; respect their time.

6. **Sentry sees only retry-exhausted failures.** This is the same
   change in two domains: retries hide transient flakes from the user
   *and* from your dashboard. The dashboard now only shows persistent
   problems, which is exactly what should be on it.

The pieces — three-attempt retry, a `StateFlow` side-channel, a single
inline banner component, the `allEmpty && failure` guard — together
took about an afternoon. The user-visible result is the difference
between *"the app is broken"* and *"my connection is flaky, let me
retry."* That distinction, more than anything else, is what makes a
hobby app feel like a real product.
