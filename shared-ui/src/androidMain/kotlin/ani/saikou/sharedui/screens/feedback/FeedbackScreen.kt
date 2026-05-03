package ani.saikou.sharedui.screens.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.domain.source.FeedbackService
import ani.saikou.presentation.screens.feedback.FeedbackViewModel
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.theme.GhostBorder
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import ani.saikou.sharedui.theme.SurfaceContainer
import ani.saikou.sharedui.theme.SurfaceContainerHigh
import ani.saikou.sharedui.theme.SurfaceVariant
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Pop the screen ~1.5s after a successful submit so the user sees the
    // confirmation banner before returning to where they came from.
    LaunchedEffect(state.didSubmit) {
        if (state.didSubmit) {
            delay(1500)
            viewModel.consumeSubmitted()
            onBack()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        // ── Top bar ──────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Text(
                "Send feedback",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Scrollable form content. Uses weight(1f) so the submit button below
        // stays pinned even when the keyboard pushes things up or the screen
        // is small.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Tell us what's working, what's broken, or what you'd like to see next. Your message lands in our team chat — no account or email required.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )

            // Category picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackService.Category.values().forEach { cat ->
                        GenreChip(
                            text = "${cat.emoji} ${cat.title}",
                            selected = state.category == cat,
                            onClick = { viewModel.selectCategory(cat) },
                        )
                    }
                }
            }

            // Message input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Message", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(SurfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, GhostBorder, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                ) {
                    BasicTextField(
                        value = state.message,
                        onValueChange = viewModel::updateMessage,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.message.isEmpty()) {
                                Text(
                                    "Be as specific as you can — what screen, what you tapped, what happened…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            inner()
                        },
                    )
                }
                state.errorMessage?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (state.didSubmit) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(SurfaceContainer)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
                    Text(
                        "Thanks — your feedback was sent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                    )
                }
            }
        }

        // Pinned submit button — always visible regardless of scroll/keyboard.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (state.isSubmitting) SurfaceContainerHigh else Primary)
                    .clickable(enabled = !state.isSubmitting && !state.didSubmit) { viewModel.submit() }
                    .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(24.dp),
                )
            } else {
                Text(
                    "Send feedback",
                    style = MaterialTheme.typography.labelLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
