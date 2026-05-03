package ani.saikou.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.presentation.screens.detail.ChapterDownloadState
import ani.saikou.sharedui.components.ChapterRow
import ani.saikou.sharedui.components.ChapterRowShimmer
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import ani.saikou.sharedui.theme.SurfaceBright

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderChapterListSheet(
    currentChapterNumber: Int,
    userProgress: Int?,
    chapterNumbers: List<Int>,
    downloadStates: Map<Int, ChapterDownloadState>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onDownloadClick: (Int) -> Unit,
    onCancelDownloadClick: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBright,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "CHAPTERS",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            when {
                                isLoading && chapterNumbers.isNotEmpty() ->
                                    "${chapterNumbers.size} cached · fetching more…"
                                isLoading -> "Fetching chapters…"
                                else -> "${chapterNumbers.size} available"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (chapterNumbers.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                    } else {
                        Text(
                            "No chapters available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            // Bucket long chapter lists by 100s — same UX as the detail screen.
            val bucketSize = 100
            val totalBuckets = (chapterNumbers.size + bucketSize - 1) / bucketSize
            // Start on the bucket containing the current chapter
            val initialBucket =
                remember(chapterNumbers, currentChapterNumber) {
                    val idx = chapterNumbers.indexOf(currentChapterNumber).takeIf { it >= 0 } ?: 0
                    (idx / bucketSize).coerceAtLeast(0)
                }
            var selectedBucket by remember(chapterNumbers, currentChapterNumber) {
                mutableIntStateOf(initialBucket)
            }

            if (totalBuckets > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(count = totalBuckets, key = { it }) { bucket ->
                        val startIdx = bucket * bucketSize
                        val endIdx = (startIdx + bucketSize - 1).coerceAtMost(chapterNumbers.lastIndex)
                        val label = "${chapterNumbers[startIdx]}–${chapterNumbers[endIdx]}"
                        GenreChip(
                            text = label,
                            selected = bucket == selectedBucket,
                            onClick = { selectedBucket = bucket },
                        )
                    }
                }
            }

            val bucketStart = selectedBucket * bucketSize
            val bucketEnd = (bucketStart + bucketSize).coerceAtMost(chapterNumbers.size)
            val bucketChapters = chapterNumbers.subList(bucketStart, bucketEnd)

            // Scroll to current chapter within the bucket on first display
            val listState = rememberLazyListState()
            LaunchedEffect(bucketChapters, currentChapterNumber) {
                val index = bucketChapters.indexOfFirst { it == currentChapterNumber }
                if (index >= 0) {
                    listState.scrollToItem(index.coerceAtLeast(0))
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = bucketChapters,
                    key = { it },
                ) { chapterNum ->
                    ChapterRow(
                        chapterNumber = chapterNum,
                        read = userProgress != null && chapterNum <= userProgress,
                        isCurrent = chapterNum == currentChapterNumber,
                        downloadState = downloadStates[chapterNum],
                        onClick = {
                            if (chapterNum != currentChapterNumber) {
                                onJumpToChapter(chapterNum)
                            } else {
                                onDismiss()
                            }
                        },
                        onDownloadClick = { onDownloadClick(chapterNum) },
                        onCancelDownloadClick = { onCancelDownloadClick(chapterNum) },
                    )
                }
                // Placeholder shimmer rows while the parser is still fetching
                // the full list. Shown alongside any cached chapters above so
                // the user knows more is on the way.
                if (isLoading) {
                    items(count = 4, key = { "shimmer_$it" }) {
                        ChapterRowShimmer()
                    }
                }
            }
        }
    }
}
