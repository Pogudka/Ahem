package com.pogudka.Ahem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.window.PixelCopy

internal class GlassDrawable : Drawable() {

    private var window: Window? = null
    private var excludeView: View? = null
    private var liveBlur = true
    private var blurred: Bitmap? = null
    private var src: Bitmap? = null
    private var tiny: Bitmap? = null
    private var out: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rTiny = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurRadius = 18f
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val locSelf = IntArray(2)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun setLiveBlur(v: Boolean) { liveBlur = v }

    fun attach(win: Window, exclude: View) {
        window = win
        excludeView = exclude
        if (liveBlur) {
            win.decorView.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            win.decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            requestRefresh()
        }
    }

    fun setRadius(r: Float) { radius = r }
    fun setBlurRadius(px: Float) { blurRadius = px.coerceIn(4f, 40f) }
    fun setGlassTint(color: Int) { tintPaint.color = color }

    fun setBorder(color: Int, widthPx: Float) {
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
        innerPaint.color = 0x33FFFFFF
        innerPaint.style = Paint.Style.STROKE
        innerPaint.strokeWidth = widthPx
    }

    fun requestRefresh() {
        if (!liveBlur || pending) return
        pending = true
        handler.postDelayed({ pending = false; refresh() }, 110)
    }

    private fun refresh() {
        val win = window ?: return
        val ex = excludeView ?: return
        if (ex.visibility != View.VISIBLE) return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return

        val div = blurRadius.coerceAtLeast(4f)
        val tw = (w / div).toInt().coerceAtLeast(2)
        val th = (h / div).toInt().coerceAtLeast(2)

        val localSrc: Bitmap
        val es = src
        if (es != null && es.width == w && es.height == h) localSrc = es
        else { localSrc = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); src = localSrc }

        val localTiny: Bitmap
        val et = tiny
        if (et != null && et.width == tw && et.height == th) localTiny = et
        else { localTiny = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888); tiny = localTiny }

        val localOut: Bitmap
        val eo = out
        if (eo != null && eo.width == w && eo.height == h) localOut = eo
        else { localOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); out = localOut }

        ex.getLocationInWindow(locSelf)
        val srcRect = Rect(locSelf[0], locSelf[1], locSelf[0] + w, locSelf[1] + h)

        try {
            PixelCopy.request(win, srcRect, localSrc, object : PixelCopy.OnPixelCopyFinishedListener {
                override fun onPixelCopyFinished(result: Int) {
                    if (result == PixelCopy.SUCCESS) blurAndShow(localSrc, localTiny, localOut)
                }
            }, handler)
        } catch (e: Exception) {
            // окно ещё не готово к копированию — следующий request перекроет
        }
    }

    private fun blurAndShow(s: Bitmap, t: Bitmap, o: Bitmap) {
        rTiny.set(0f, 0f, t.width.toFloat(), t.height.toFloat())
        val ct = Canvas(t)
        ct.drawColor(0x00000000)
        ct.drawBitmap(s, null, rTiny, paint)
        rect.set(0f, 0f, o.width.toFloat(), o.height.toFloat())
        val co = Canvas(o)
        co.drawColor(0x00000000)
        co.drawBitmap(t, null, rect, paint)
        blurred = o
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CCW)
        canvas.save()
        canvas.clipPath(path)
        val b = blurred
        if (liveBlur && b != null) {
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
