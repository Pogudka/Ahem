package com.pogudka.Ahem

import android.os.Build
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

internal fun ic(c: Int): Color = Color(
    (c shr 16 and 0xFF) / 255f,
    (c shr 8 and 0xFF) / 255f,
    (c and 0xFF) / 255f,
    (c shr 24 and 0xFF) / 255f
)

// Живое стекло: на Android 12+ hazeChild рисует размытый фон (источник = контент экрана) через RenderEffect на GPU,
// следует за скроллом в кадре, 60fps, без PixelCopy/CPU/Bitmap/feedback-полос. Поверх blur — полупрозрачные
// specular/объём/хроматика (как Tinted Liquid Button на референсе). Ниже API 31 нативного backdrop blur нет ->
// откат на frosted-подложку (navGlass + те же градиенты), без лагов.
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isDark(LocalContext.current)
    val effTint = if (tint != Color.Unspecified) tint else if (dark) Color(0x33FFFFFF) else Color(0x22FFFFFF)
    val shape = RoundedCornerShape(corner)
    val canBlur = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hazeStyle = HazeStyle(
        tint = effTint,
        blurRadius = 28.dp,
        noiseFactor = 0.04f
    )

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (canBlur) {
                    Modifier.hazeChild(hazeState!!, shape, hazeStyle)
                } else {
                    Modifier.background(ic(P.navGlass))
                }
            )
            // мягкий диагональный specular (блик линзы) — поверх blur или поверх frosted
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.22f),
                        Color(1f, 1f, 1f, 0f),
                        Color(1f, 1f, 1f, 0.10f)
                    )
                )
            )
            // объём: светлая кромка сверху, тень снизу
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.18f),
                        Color(1f, 1f, 1f, 0f),
                        Color(0f, 0f, 0f, 0.12f)
                    )
                )
            )
            // хроматическая кромка: циан <-> маджента, тонко
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0.45f, 0.85f, 1f, 0.16f),
                        Color(1f, 1f, 1f, 0f),
                        Color(1f, 0.55f, 0.85f, 0.13f)
                    )
                )
            )
            .border(1.5.dp, border, shape)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
