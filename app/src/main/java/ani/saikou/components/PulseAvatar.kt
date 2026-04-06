package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import coil.compose.AsyncImage

@Composable
fun PulseAvatar(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val gradientBorder = Brush.linearGradient(listOf(Primary, Secondary))

    Box(
        modifier = modifier
            .size(size + 6.dp)
            .border(width = 2.dp, brush = gradientBorder, shape = CircleShape)
            .padding(3.dp)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    ani.saikou.ui.theme.SurfaceContainerHigh,
                    CircleShape,
                ),
        )
    }
}
