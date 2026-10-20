package com.pogudka.Ahem

import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

internal fun ic(c: Int): Color = Color(
    (c shr 16 and 0xFF) / 255f,
    (c shr 8 and 0xFF) / 255f,
    (c and 0xFF) / 255f,
    (c shr 24 and 0xFF) / 255f
)

// Живое стекло: нативный View.setBackgroundBlurRadius (GPU, API 31+) размазывает контент окна ПОЗАДИ панели (= ленту).
// Иконки/градиенты рисуются Compose поверх нативной размытой подложки. Без PixelCopy, без либ, без feedback-полос, 60fps.
// Android 7-11: нативного backdrop blur нет -> frosted-подложка (background + градиенты) без размытия, без лагов.
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    blur: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val dark = isDark(ctx)
    val effTint = if (tint != Color.Unspecified) tint else if (dark) Color(0x33FFFFFF) else Color(0x22FFFFFF)
    val density = LocalDensity.current
    val radiusPx = with(density) { corner.toPx() }
    val blurPx = with(density) { blur.toPx() }.toInt().coerceAtLeast(1)
    val shape = RoundedCornerShape(corner)
    val androidTint = android.graphics.Color.argb(
        (effTint.alpha * 255f).toInt().coerceIn(0, 255),
        (effTint.red * 255f).toInt().coerceIn(0, 255),
        (effTint.green * 255f).toInt().coerceIn(0, 255),
        (effTint.blue * 255f).toInt().coerceIn(0, 255)
    )

    Box(modifier = modifier.clip(shape).border(1.dp, border, shape)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                FrameLayout(c).apply {
                    setBackgroundColor(androidTint)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                            }
                        }
                        clipToOutline = true
                        setBackgroundBlurRadius(blurPx)
                    }
                }
            },
            update = { v ->
                v.setBackgroundColor(androidTint)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) v.setBackgroundBlurRadius(blurPx)
            }
        )
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(1f, 1f, 1f, 0.16f), Color(1f, 1f, 1f, 0f), Color(1f, 1f, 1f, 0.06f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(1f, 1f, 1f, 0.14f), Color(1f, 1f, 1f, 0f), Color(0f, 0f, 0f, 0.08f)))))
        Box(Modifier.fillMaxSize(), content = content)
    }
}
