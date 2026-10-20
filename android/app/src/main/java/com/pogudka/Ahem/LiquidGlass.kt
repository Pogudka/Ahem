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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

internal fun ic(c: Int): Color = Color(
    (c shr 16 and 0xFF) / 255f,
    (c shr 8 and 0xFF) / 255f,
    (c and 0xFF) / 255f,
    (c shr 24 and 0xFF) / 255f
)

@OptIn(ExperimentalHazeApi::class)
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
    val effTint = if (tint != Color.Unspecified) tint else if (dark) Color(0x1FFFFFFF) else Color(0x14FFFFFF)
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.hazeEffect(hazeState)
                } else {
                    Modifier.background(ic(P.navGlass))
                }
            )
            .background(effTint)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.18f),
                        Color(1f, 1f, 1f, 0f),
                        Color(1f, 1f, 1f, 0.07f)
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(1f, 1f, 1f, 0.16f),
                        Color(1f, 1f, 1f, 0f),
                        Color(0f, 0f, 0f, 0.10f)
                    )
                )
            )
            .border(1.dp, border, shape)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
