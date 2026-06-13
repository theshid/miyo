package ani.saikou.data.source.anime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-based tests for the Cloudflare-challenge classifier. The actual
 * markup samples are abbreviated versions of what `curl https://anineko.to`
 * returns under challenge — enough to match each detection path without
 * pulling 5 KB strings into the test file.
 */
class CloudflareDetectorTest {
    @Test
    fun `cf-mitigated challenge header alone classifies as challenge`() {
        // Cloudflare's documented machine-readable signal — wins regardless
        // of status code or body, even when the body has been replaced by a
        // CDN edge function with a different shape.
        val headers = mapOf("cf-mitigated" to "challenge")
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 200,
                headersLookup = { headers[it.lowercase()] },
                body = "<html><body>hello</body></html>",
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `Just a moment title with 403 status classifies as challenge`() {
        val body = JUST_A_MOMENT_FIXTURE
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { null },
                body = body,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `503 Service Unavailable with challenge body classifies as challenge`() {
        // CF sometimes uses 503 for sustained challenges.
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 503,
                headersLookup = { null },
                body = JUST_A_MOMENT_FIXTURE,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `429 rate limit with challenge body classifies as challenge`() {
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 429,
                headersLookup = { null },
                body = JUST_A_MOMENT_FIXTURE,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `body referencing challenges-cloudflare-com classifies as challenge`() {
        // Turnstile script tag — even if the title is different (e.g.
        // managed-challenge variants), this domain is the strongest body
        // tell.
        val body = """<html><body><script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script></body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { null },
                body = body,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `legacy cf-browser-verification marker classifies as challenge`() {
        // Older I-am-under-attack mode response.
        val body = """<html><body class="cf-browser-verification">Verifying...</body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 503,
                headersLookup = { null },
                body = body,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `cf-im-under-attack class on body classifies as challenge`() {
        val body = """<html><body class="cf-im-under-attack">Hold on…</body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { null },
                body = body,
            )
        assertTrue(isChallenge)
    }

    @Test
    fun `200 OK with normal body is NOT a challenge`() {
        val body = """<html><body><h1>Welcome</h1></body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 200,
                headersLookup = { null },
                body = body,
            )
        assertFalse(isChallenge)
    }

    @Test
    fun `404 with body content is NOT classified as challenge`() {
        // 404 is not in the challenge status set — body sniff is gated by
        // status so an unrelated 404 page mentioning Cloudflare for any
        // reason doesn't false-positive.
        val body = """<html><body>Page not found. Hosted on Cloudflare.</body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 404,
                headersLookup = { null },
                body = body,
            )
        assertFalse(isChallenge)
    }

    @Test
    fun `403 with non-challenge body is NOT a challenge`() {
        // Legitimate auth-style 403 with no CF markers — must not false-positive.
        val body = """<html><body><h1>Forbidden</h1><p>You don't have access.</p></body></html>"""
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { null },
                body = body,
            )
        assertFalse(isChallenge)
    }

    @Test
    fun `null body short-circuits to false even on suspicious status`() {
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { null },
                body = null,
            )
        assertFalse(isChallenge)
    }

    @Test
    fun `cf-mitigated with unrelated value is NOT a challenge`() {
        // The header exists but doesn't contain 'challenge' — could be e.g.
        // 'block' or some other CF action. Don't flag.
        val headers = mapOf("cf-mitigated" to "block")
        val isChallenge =
            CloudflareDetector.isChallenge(
                statusCode = 403,
                headersLookup = { headers[it.lowercase()] },
                body = "<html></html>",
            )
        assertFalse(isChallenge)
    }

    @Test
    fun `cfRay returns the cf-ray header when present`() {
        val headers = mapOf("cf-ray" to "8a3b9c0d-DFW")
        val cfRay = CloudflareDetector.cfRay { headers[it.lowercase()] }
        assertEquals("8a3b9c0d-DFW", cfRay)
    }

    @Test
    fun `cfRay returns null when the header is missing`() {
        val cfRay = CloudflareDetector.cfRay { null }
        assertNull(cfRay)
    }

    private companion object {
        // Abbreviated version of the actual anineko.to challenge response
        // (full body is ~5 KB).
        const val JUST_A_MOMENT_FIXTURE =
            """<!DOCTYPE html><html lang="en-US">
            <head>
              <title>Just a moment...</title>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            </head>
            <body class="no-js"><div class="main-wrapper" role="main">
              <h1>Verifying you are human...</h1>
            </div></body></html>"""
    }
}
