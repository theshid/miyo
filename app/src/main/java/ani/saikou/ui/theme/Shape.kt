package ani.saikou.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SaikouShapes =
    Shapes(
        // Chips, badges, small tags
        extraSmall = RoundedCornerShape(8.dp),
        // Poster cards, grid items
        small = RoundedCornerShape(12.dp),
        // Standard cards, inputs
        medium = RoundedCornerShape(16.dp),
        // Glass containers, dialogs, hero cards
        large = RoundedCornerShape(24.dp),
        // Bottom sheets, large modals
        extraLarge = RoundedCornerShape(32.dp),
    )

// Full round for pill buttons and avatars
val PillShape = RoundedCornerShape(percent = 50)
