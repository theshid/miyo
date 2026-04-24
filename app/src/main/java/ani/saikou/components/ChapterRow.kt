package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainer

/**
 * UI-level download state for a chapter row. Keep this outside of any single
 * ViewModel so both the media detail screen and the reader's chapter sheet can
 * consume the same type.
 */
data class ChapterDownloadState(
    val status: String, // QUEUED | DOWNLOADING | PAUSED | COMPLETED | ERROR
    val downloadedPages: Int,
    val totalPages: Int,
) {
    val progress: Float
        get() = if (totalPages > 0) downloadedPages.toFloat() / totalPages else 0f
}

/**
 * One row in a list of chapters. Shows a number badge (read-tinted when the
 * user has already read it), the chapter title, and a trailing download
 * button whose icon is derived purely from [downloadState].
 */
@Composable
fun ChapterRow(
    chapterNumber: Int,
    chapterLabel: String = "Chapter $chapterNumber",
    read: Boolean = false,
    isCurrent: Boolean = false,
    downloadState: ChapterDownloadState? = null,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when {
                            isCurrent -> Primary.copy(alpha = 0.25f)
                            read -> Secondary.copy(alpha = 0.2f)
                            else -> SurfaceContainer
                        },
                        MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$chapterNumber",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        isCurrent -> Primary
                        read -> Secondary
                        else -> OnSurface
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = chapterLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                modifier = Modifier.weight(1f),
            )
            ChapterDownloadButton(
                state = downloadState,
                onDownload = onDownloadClick,
                onCancel = onCancelDownloadClick,
            )
        }
    }
}

@Composable
fun ChapterDownloadButton(
    state: ChapterDownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state?.status) {
        null -> {
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download chapter",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        "QUEUED", "PAUSED" -> {
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Queued — tap to cancel",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        "DOWNLOADING" -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    color = Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel download",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        "COMPLETED" -> {
            Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                tint = Secondary,
                modifier = Modifier.size(24.dp),
            )
        }
        "ERROR" -> {
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download failed — tap to retry",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Placeholder ChapterRow while the full list is still being fetched. */
@Composable
fun ChapterRowShimmer() {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
        contentPadding = 12.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(Modifier.size(40.dp))
            ShimmerBox(
                Modifier
                    .weight(1f)
                    .height(14.dp)
                    .width(120.dp),
            )
            ShimmerBox(Modifier.size(24.dp))
        }
    }
}
