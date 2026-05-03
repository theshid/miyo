package ani.saikou.sharedui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.sharedui.theme.HiroMisake
import ani.saikou.sharedui.theme.PillShape
import ani.saikou.sharedui.theme.Primary

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Primary,
    contentColor: Color = Color.White,
    textStyle: TextStyle =
        MaterialTheme.typography.labelLarge.copy(
            fontFamily = HiroMisake,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = PillShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.35f),
                disabledContentColor = contentColor.copy(alpha = 0.35f),
            ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = textStyle,
        )
    }
}
