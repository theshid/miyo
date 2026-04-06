package ani.saikou.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TODO: Search input with autocomplete
        // TODO: Filter pills (Genre, Sort, Format, Status)
        // TODO: Active filter chips
        // TODO: Results grid/list with toggle
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
