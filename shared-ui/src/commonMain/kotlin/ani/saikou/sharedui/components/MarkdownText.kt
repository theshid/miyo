package ani.saikou.sharedui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import ani.saikou.sharedui.theme.Primary

/**
 * Renders a subset of Markdown inline formatting:
 * - **bold** → bold
 * - *italic* → italic
 * - `code` → monospace-styled
 * - Bullet lines (- item or • item) get a bullet prefix
 * - ### headings → bold + primary color
 *
 * [color] sets the base body color. Defaults to [Color.Unspecified], which
 * lets Compose fall through to `LocalContentColor` — but most callers should
 * pass an explicit color, since the chat bubbles this renders into don't sit
 * inside a Material `Surface` and `LocalContentColor` defaults to black.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val annotated = remember(text) { parseMarkdown(text) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

private fun parseMarkdown(text: String): AnnotatedString =
    buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                // Headings
                trimmed.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Primary)) {
                        append(trimmed.removePrefix("### "))
                    }
                }
                trimmed.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Primary)) {
                        append(trimmed.removePrefix("## "))
                    }
                }
                // Bullet points
                trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ") -> {
                    append("  •  ")
                    appendInlineFormatted(trimmed.substring(2))
                }
                // Numbered lists
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val dotIndex = trimmed.indexOf(". ")
                    append("  ${trimmed.substring(0, dotIndex + 2)}")
                    appendInlineFormatted(trimmed.substring(dotIndex + 2))
                }
                else -> {
                    appendInlineFormatted(trimmed)
                }
            }
            if (index < lines.lastIndex) append("\n")
        }
    }

private fun AnnotatedString.Builder.appendInlineFormatted(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold: **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Inline code: `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Medium,
                            color = Primary,
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic: *text* (single asterisk, not double)
            text[i] == '*' && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
