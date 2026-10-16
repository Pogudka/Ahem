package com.pogudka.Ahem

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Outline
import android.view.View
import android.view.ViewOutlineProvider

internal class GlassDrawable : Drawable() {

    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurPx = 30f

    fun setLiveBlur(v: Boolean) { /* сохранено для совместимости вызовов */ }
    fun setRadius(r: Float) { radius = r }
    fun setBlurRadius(px: Float) { blurPx = px.coerceIn(8f, 80f) }
    fun setGlassTint(color: Int) { tintPaint.color = color }

    fun setBorder(color: Int, widthPx: Float) {
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
        innerPaint.color = 0x40FFFFFF
        innerPaint.style = Paint.Style.STROKE
        innerPaint.strokeWidth = widthPx
    }

    // публичный API 31: система моет фон ЗА вьюхой, захват не нужен
    fun attach(host: View, exclude: View) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                exclude.setBlurBehindRadius(blurPx.toInt())
                exclude.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, radius)
                    }
                }
                exclude.clipToOutline = true
            } catch (e: Throwable) {
            }
        }
    }

    fun requestRefresh() { /* не нужен: размытие делает композитор, а не bitmap */ }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CCW)

        canvas.save()
        canvas.clipPath(path)
        // полупрозрачное тело стекла поверх системного блюра
        canvas.drawRect(rect, tintPaint)
        // верхний блик (стекло ловит свет сверху)
        hiPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.5f,
            0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, hiPaint)
        hiPaint.shader = null
        // лёгкий диагональный сдвиг света = «жидкость»
        sheenPaint.shader = LinearGradient(
            0f, 0f, w, h,
            0x14FFFFFF, 0x00000000, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, sheenPaint)
        sheenPaint.shader = null
        canvas.restore()

        canvas.drawPath(path, borderPaint)
        canvas.drawPath(insetPath(rect, 1f), innerPaint)
    }

    private fun insetPath(r: RectF, d: Float): Path {
        val np = Path()
        np.addRoundRect(r.left + d, r.top + d, r.right - d, r.bottom - d, radius, radius, Path.Direction.CCW)
        return np
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
