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

// Жидкое стекло = полупрозрачная подложка + диагональный specular-блик + объём (свет сверху / тень снизу)
// + радужная кромка (хроматика). Никакой пересъёмки/Bitmap/CPU в горячем пути -> скролл 60fps по построению.
// Размытие ФОНА в движении здесь нет намеренно: внутри одного Compose-холста его не сделать без либы;
// вместо него стекло прозрачное с бликом, как Surface/Tinted Liquid Button на референсе.
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isDark(LocalContext.current)
    // полупрозрачно (~40%), чтобы фон просвечивал — НЕ матово
    val base = if (dark) Color(0x66101016) else Color(0x66FFFFFF)
    val effTint = if (tint != Color.Unspecified) tint else base
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .clip(shape)
            .background(effTint)
            // диагональный specular: яркие углы (блик линзы), центр светлее/прозрачнее
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.55f),
                        Color(1f, 1f, 1f, 0.05f),
                        Color(1f, 1f, 1f, 0.42f)
                    )
                )
            )
            // объём: светлая кромка сверху, тёмная снизу
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.30f),
                        Color(1f, 1f, 1f, 0f),
                        Color(0f, 0f, 0f, 0.16f)
                    )
                )
            )
            // хроматическая кромка: циан в одном углу -> маджента в другом, тонко
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0.45f, 0.85f, 1f, 0.22f),
                        Color(1f, 1f, 1f, 0f),
                        Color(1f, 0.55f, 0.85f, 0.18f)
                    )
                )
            )
            .border(1.5.dp, border, shape)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
