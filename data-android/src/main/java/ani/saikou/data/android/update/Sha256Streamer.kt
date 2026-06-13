package ani.saikou.data.android.update

import java.io.File
import java.security.MessageDigest

/**
 * Computes the lowercase hex SHA-256 of a file by streaming 8 KiB chunks
 * through [`MessageDigest`]. Bounded memory regardless of APK size.
 */
internal object Sha256Streamer {
    private const val BUFFER_SIZE = 8 * 1024

    fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexLower()
    }

    fun hashOf(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHexLower()
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
