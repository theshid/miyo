package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.SurfaceBright
import ani.saikou.ui.theme.SurfaceContainer
import coil3.compose.AsyncImage

data class SourceItem(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectorSheet(
    title: String,
    sources: List<SourceItem>,
    onSelect: (SourceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = SurfaceBright,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Select the correct source",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(400.dp),
            ) {
                items(sources) { source ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(SurfaceContainer)
                                .clickable { onSelect(source) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = source.coverUrl,
                            contentDescription = source.title,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(48.dp, 68.dp)
                                    .clip(MaterialTheme.shapes.small),
                        )
                        Text(
                            text = source.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
