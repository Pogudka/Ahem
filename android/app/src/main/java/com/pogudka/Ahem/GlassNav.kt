package com.pogudka.Ahem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

internal class GlassDrawable : Drawable() {

    private var sourceRoot: View? = null
    private var excludeView: View? = null
    private var blurred: Bitmap? = null
    private var src: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurPx = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val locSelf = IntArray(2)
    private val locRoot = IntArray(2)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun attach(root: View, exclude: View) {
        sourceRoot = root
        excludeView = exclude
        root.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        requestRefresh()
    }

    fun setRadius(r: Float) { radius = r }
    fun setBlurRadius(px: Float) { blurPx = px }
    fun setGlassTint(color: Int) { tintPaint.color = color }

    fun setBorder(color: Int, widthPx: Float) {
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
    }

    fun requestRefresh() {
        if (pending) return
        pending = true
        handler.postDelayed({ pending = false; refresh() }, 90)
    }

    private fun refresh() {
        val root = sourceRoot ?: return
        val ex = excludeView ?: return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return
        var s = src
        if (s == null || s.width != w || s.height != h) {
            s = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            src = s
        }
        ex.getLocationInWindow(locSelf)
        root.getLocationInWindow(locRoot)
        val dx = (locSelf[0] - locRoot[0]).toFloat()
        val dy = (locSelf[1] - locRoot[1]).toFloat()
        val oldVis = ex.visibility
        ex.visibility = View.INVISIBLE
        val cs = Canvas(s)
        cs.drawColor(0x00000000)
        cs.translate(-dx, -dy)
        try { root.draw(cs) } catch (e: Exception) {}
        ex.visibility = oldVis

        var out = blurred
        if (out == null || out.width != w || out.height != h) {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val co = Canvas(out)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0f) {
            co.setRenderEffect(
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            )
            co.drawBitmap(s, 0f, 0f, paint)
            co.setRenderEffect(null)
        } else {
            // пирамида bilinear = приближение гаусса без пикселей
            var cw = w; var ch = h
            var cur = s
            var i = 0
            while (cw > 2 && ch > 2 && i < 3) {
                val nw = (cw / 2).coerceAtLeast(1)
                val nh = (ch / 2).coerceAtLeast(1)
                val tmp = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
                val ct = Canvas(tmp)
                ct.drawBitmap(cur, null, RectF(0f, 0f, nw.toFloat(), nh.toFloat()), paint)
                cur = tmp
                cw = nw; ch = nh; i++
            }
            co.drawBitmap(cur, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        }
        blurred = out
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = blurred
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CCW)
        canvas.save()
        canvas.clipPath(path)
        if (b != null) {
            canvas.drawBitmap(b, null, rect, paint)
        }
        canvas.drawRect(rect, tintPaint)
        hiPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, hiPaint)
        hiPaint.shader = null
        canvas.restore()
        canvas.drawPath(path, borderPaint)
        borderPaint.color = 0x33FFFFFF
        canvas.drawPath(insetPath(path, 1f), borderPaint)
        borderPaint.color = P.border
    }

    private fun insetPath(p: Path, d: Float): Path {
        val r = RectF()
        p.computeBounds(r, true)
        val np = Path()
        np.addRoundRect(r.left + d, r.top + d, r.right - d, r.bottom - d, radius, radius, Path.Direction.CCW)
        return np
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
