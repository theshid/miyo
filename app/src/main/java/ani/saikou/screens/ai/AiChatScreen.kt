package ani.saikou.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import ani.saikou.components.MarkdownText
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceContainerHigh

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    viewModel: AiChatViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // ── Top Bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainer)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface,
                )
            }
            Column {
                Text(
                    text = "Miyo AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                )
                Text(
                    text = "Powered by GPT-4o",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
        }

        // ── Messages ─────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Welcome message when empty
            if (state.messages.isEmpty()) {
                item(key = "welcome") {
                    WelcomeCard(onSuggestionClick = { suggestion ->
                        viewModel.sendMessage(suggestion)
                    })
                }
            }

            items(
                items = state.messages,
                key = { "${it.isUser}_${it.text.hashCode()}_${state.messages.indexOf(it)}" },
            ) { bubble ->
                ChatBubbleRow(bubble = bubble)
            }

            // Typing indicator
            if (state.isLoading) {
                item(key = "loading") {
                    TypingIndicator()
                }
            }
        }

        // ── Input Bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask about anime, manga...", color = OnSurfaceVariant.copy(alpha = 0.5f))
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceContainerHigh,
                    unfocusedContainerColor = SurfaceContainerHigh,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = Primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !state.isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                ),
                singleLine = false,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !state.isLoading) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) Primary else Primary.copy(alpha = 0.3f)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(bubble: ChatBubble) {
    val alignment = if (bubble.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (bubble.isUser) Primary.copy(alpha = 0.15f) else SurfaceContainerHigh
    val textColor = if (bubble.isUser) OnSurface else OnSurface
    val shape = if (bubble.isUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(12.dp),
            ) {
                if (bubble.isUser) {
                    Text(
                        text = bubble.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                } else {
                    MarkdownText(
                        text = bubble.text,
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = Primary,
            strokeWidth = 2.dp,
        )
        Text(
            text = "Miyo is thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeCard(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Hey! I'm Miyo AI",
            style = MaterialTheme.typography.headlineSmall,
            color = Primary,
        )
        Text(
            text = "Your anime & manga assistant. Ask me anything!",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Suggestion chips
        val suggestions = listOf(
            "Find me something like Attack on Titan",
            "Catch me up on One Piece",
            "What should I watch next?",
            "Best romance manga of 2024",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
        )
    }
}
