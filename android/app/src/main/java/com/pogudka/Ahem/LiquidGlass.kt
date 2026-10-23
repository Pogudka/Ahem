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

// Простое полупрозрачное стекло. Цвет берётся из P.navGlass — в светлой теме белый 50%,
// в тёмной теме тёмный 50%. Одинаково выглядит в обеих темах. Никаких тяжёлых операций,
// скролл 60fps по построению.
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    content: @Composable BoxScope.() -> Unit
) {
    val bg = if (tint != Color.Unspecified) tint else ic(P.navGlass)
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.5.dp, border, shape)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
