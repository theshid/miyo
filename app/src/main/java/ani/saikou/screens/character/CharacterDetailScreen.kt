package ani.saikou.screens.character

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun CharacterDetailScreen(
    characterId: Int,
    onBack: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TODO: Collapsing header with character art
        // TODO: Character name + role
        // TODO: "Appears in" media grid
        Text(
            text = "Character: $characterId",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
