package ani.saikou.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.components.MarkdownText
import ani.saikou.data.remote.OpenAiService
import ani.saikou.domain.model.Media
import org.koin.compose.koinInject
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainerHigh

@Composable
fun CatchMeUpSheet(
    media: Media,
    onDismiss: () -> Unit,
) {
    var summary by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val openAi = koinInject<OpenAiService>()

    LaunchedEffect(media.id) {

        val progressType = if (media.type == "MANGA") "chapters" else "episodes"
        val progressNum = media.userProgress ?: 0
        val total = media.totalEpisodes ?: media.totalChapters

        val prompt = buildString {
            append("The user is ${if (media.type == "MANGA") "reading" else "watching"} ")
            append("\"${media.displayTitle}\"")
            if (media.nameRomaji != null && media.nameRomaji != media.displayTitle) {
                append(" (${media.nameRomaji})")
            }
            append(". They are on $progressType $progressNum")
            if (total != null) append(" out of $total")
            append(".\n\n")

            val description = media.description
            if (!description.isNullOrBlank()) {
                val cleanDesc = description
                    .replace("<br>", "\n")
                    .replace(Regex("<[^>]*>"), "")
                append("Series synopsis: $cleanDesc\n\n")
            }

            val genres = media.genres
            if (!genres.isNullOrEmpty()) {
                append("Genres: ${genres.joinToString(", ")}\n\n")
            }

            append("Give them a snappy \"Catch Me Up\" recap up to $progressType $progressNum. ")
            append("Format strictly:\n")
            append("- One short hook sentence (max 25 words) describing where the story stands RIGHT NOW.\n")
            append("- Then exactly 3 bullet points covering the most important arcs or developments that got them here.\n")
            append("- Each bullet: one sentence, max 30 words.\n\n")
            append("Do NOT spoil anything beyond $progressType $progressNum. ")
            append("No headings, no preamble, no closing remarks — just the hook line and the 3 bullets.")
        }

        val messages = listOf(
            OpenAiService.ChatMessage(
                role = "system",
                content = "You are Miyo AI, an anime & manga assistant. You provide accurate, spoiler-aware recaps. " +
                    "Only summarize up to the point the user has reached. Never reveal future plot points.",
            ),
            OpenAiService.ChatMessage(role = "user", content = prompt),
        )

        summary = openAi.chat(messages)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(top = 8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Catch Me Up",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = OnSurfaceVariant,
                )
            }
        }

        // Subtitle
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val progressLabel = if (media.type == "MANGA") {
            "Ch. ${media.userProgress ?: 0}"
        } else {
            "Ep. ${media.userProgress ?: 0}"
        }
        Text(
            text = "Progress: $progressLabel",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Recapping what you've seen so far...",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                MarkdownText(
                    text = summary ?: "Couldn't generate a summary. Please try again.",
                    color = OnSurface,
                )
            }
        }
    }
}
