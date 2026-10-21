package com.pogudka.Ahem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun ic(c: Int): Color = Color(
    (c shr 16 and 0xFF) / 255f,
    (c shr 8 and 0xFF) / 255f,
    (c and 0xFF) / 255f,
    (c shr 24 and 0xFF) / 255f
)

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isDark(LocalContext.current)
    // плотный frosted: на светлой теме — насыщенный белый, на тёмной — тёмный, чтобы читалось как стекло, а не «прозрачный калл»
    val effTint = if (tint != Color.Unspecified) tint else if (dark) Color(0xE61C1C1E) else Color(0xE6FFFFFF)
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .clip(shape)
            .background(effTint)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.34f),
                        Color(1f, 1f, 1f, 0.05f),
                        Color(1f, 1f, 1f, 0.18f)
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.22f),
                        Color(1f, 1f, 1f, 0f),
                        Color(0f, 0f, 0f, 0.12f)
                    )
                )
            )
            .border(1.5.dp, border, shape)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
