package ani.saikou.data.android.update

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UpdateManifestDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun `parses well-formed manifest with all fields`() {
        val raw =
            """
            {
              "versionCode": 5,
              "versionName": "1.3.0",
              "minimumSupportedVersion": 1,
              "apkUrl": "https://github.com/theshid/miyo/releases/download/v1.3.0/miyo-v1.3.0.apk",
              "sha256": "ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
              "releaseNotes": "Bug fixes and stability",
              "mandatory": false,
              "publishedAt": "2026-06-13T10:00:00Z"
            }
            """.trimIndent()

        val parsed = json.decodeFromString(UpdateManifestDto.serializer(), raw).toDomain()

        assertEquals(5, parsed.versionCode)
        assertEquals("1.3.0", parsed.versionName)
        assertEquals(1, parsed.minimumSupportedVersion)
        assertEquals(
            "https://github.com/theshid/miyo/releases/download/v1.3.0/miyo-v1.3.0.apk",
            parsed.apkUrl,
        )
        // SHA-256 is normalised to lowercase by toDomain().
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            parsed.sha256,
        )
        assertEquals("Bug fixes and stability", parsed.releaseNotes)
        assertEquals(false, parsed.mandatory)
        assertEquals("2026-06-13T10:00:00Z", parsed.publishedAt)
    }

    @Test
    fun `defaults mandatory to false when omitted`() {
        val raw =
            """
            {
              "versionCode": 5,
              "versionName": "1.3.0",
              "minimumSupportedVersion": 1,
              "apkUrl": "https://example.invalid/m.apk",
              "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
              "releaseNotes": ""
            }
            """.trimIndent()

        val parsed = json.decodeFromString(UpdateManifestDto.serializer(), raw).toDomain()

        assertEquals(false, parsed.mandatory)
        assertEquals(null, parsed.publishedAt)
    }

    @Test
    fun `tolerates unknown fields — server side can add new keys without breaking old clients`() {
        val raw =
            """
            {
              "versionCode": 5,
              "versionName": "1.3.0",
              "minimumSupportedVersion": 1,
              "apkUrl": "https://example.invalid/m.apk",
              "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
              "releaseNotes": "Notes",
              "futureField": { "nested": "ignored" },
              "anotherField": 42
            }
            """.trimIndent()

        val parsed = json.decodeFromString(UpdateManifestDto.serializer(), raw).toDomain()
        assertEquals(5, parsed.versionCode)
    }

    @Test
    fun `rejects malformed JSON with SerializationException`() {
        val malformed = "{ not real json"

        try {
            json.decodeFromString(UpdateManifestDto.serializer(), malformed)
            fail("expected SerializationException")
        } catch (_: SerializationException) {
            // expected
        }
    }

    @Test
    fun `rejects manifest missing a required field`() {
        // Missing apkUrl.
        val raw =
            """
            {
              "versionCode": 5,
              "versionName": "1.3.0",
              "minimumSupportedVersion": 1,
              "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
              "releaseNotes": "Notes"
            }
            """.trimIndent()

        try {
            json.decodeFromString(UpdateManifestDto.serializer(), raw)
            fail("expected SerializationException for missing apkUrl")
        } catch (_: SerializationException) {
            // expected
        }
    }
}
