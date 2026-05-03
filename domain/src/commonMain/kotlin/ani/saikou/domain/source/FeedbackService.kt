package ani.saikou.domain.source

/**
 * Outbound feedback channel — POSTs user-submitted reports to whatever
 * sink the platform implementation uses. The Android impl posts to a
 * Discord webhook with embedded device/version metadata; iOS would do
 * the equivalent via its own HTTP client.
 *
 * Stateless. The VM holds the form state; this interface only carries
 * the submit verb plus the contract types ([Category] and [Result]).
 */
interface FeedbackService {
    suspend fun submit(
        category: Category,
        message: String,
    ): Result

    /**
     * Triage bucket the user picked in the screen. The numeric `color` is
     * a 24-bit RGB value the Android impl forwards to Discord's embed
     * color field — kept on the domain side so iOS impls don't have to
     * re-pick palette decisions.
     */
    enum class Category(
        val title: String,
        val emoji: String,
        val color: Int,
    ) {
        BUG("Bug report", "🐞", 0xE74C3C),
        FEATURE("Feature request", "✨", 0xF1C40F),
        GENERAL("General feedback", "💬", 0x3498DB),
    }

    sealed class Result {
        data object Success : Result()

        data class Failure(
            val reason: String,
        ) : Result()
    }
}
