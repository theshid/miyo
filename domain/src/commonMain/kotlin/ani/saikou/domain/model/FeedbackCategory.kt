package ani.saikou.domain.model

/**
 * Triage bucket the user picks on the feedback screen. Title + emoji are
 * presentation-friendly labels; `color` is a 24-bit RGB value forwarded
 * to the outbound platform impl (e.g. Discord embed color on Android).
 *
 * Lives in :domain so the VM and any future iOS impl share one source.
 */
enum class FeedbackCategory(
    val title: String,
    val emoji: String,
    val color: Int,
) {
    BUG("Bug report", "🐞", 0xE74C3C),
    FEATURE("Feature request", "✨", 0xF1C40F),
    GENERAL("General feedback", "💬", 0x3498DB),
}
