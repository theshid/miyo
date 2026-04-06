package ani.saikou.screens.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun UserListsScreen(
    type: String,
    onBack: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TODO: App bar with title
        // TODO: Tab bar (Watching, Completed, Paused, Planning, Dropped)
        // TODO: Media list per tab
        // TODO: Long-press bottom sheet editor
        Text(
            text = "My $type List",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
