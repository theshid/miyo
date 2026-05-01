package ani.saikou.screens.error

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ani.saikou.components.HalftoneButton
import ani.saikou.components.HalftoneSize
import ani.saikou.components.HalftoneVariant
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.SurfaceContainerHigh

@Composable
fun NoInternetScreen(onRetry: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon in a circle
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .background(SurfaceContainerHigh, MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No Connection",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
            )

            Text(
                text = "Check your internet and try again",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            HalftoneButton(
                text = "Retry",
                onClick = onRetry,
                size = HalftoneSize.LG,
                variant = HalftoneVariant.PURPLE,
                geistFamily = ani.saikou.ui.theme.Inter,
            )
        }
    }
}
