package ani.saikou.data.android.update

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256StreamerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `hash of empty bytes matches the published SHA-256 of empty input`() {
        // Published constant from FIPS 180-2; sanity-check that we're computing SHA-256, not some other digest.
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, Sha256Streamer.hashOf(ByteArray(0)))
    }

    @Test
    fun `hash of abc matches the canonical SHA-256 test vector`() {
        // FIPS 180-2 Appendix B.1: SHA-256("abc").
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val actual = Sha256Streamer.hashOf("abc".toByteArray(Charsets.US_ASCII))
        assertEquals(expected, actual)
    }

    @Test
    fun `streamed file hash equals one-shot bytes hash`() {
        val payload = ByteArray(50_000) { it.toByte() }
        val file = tempFolder.newFile("blob.bin").apply { writeBytes(payload) }

        val streamed = Sha256Streamer.hashOf(file)
        val oneShot = Sha256Streamer.hashOf(payload)

        assertEquals(oneShot, streamed)
    }

    @Test
    fun `hashes are always lowercase hex of length 64`() {
        val hash = Sha256Streamer.hashOf("payload".toByteArray())
        assertEquals(hash, hash.lowercase())
        assertEquals(64, hash.length)
    }
}
