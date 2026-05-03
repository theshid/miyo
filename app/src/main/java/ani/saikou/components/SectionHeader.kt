package ani.saikou.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.Primary

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}
