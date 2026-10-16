package com.pogudka.Ahem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

internal class GlassDrawable : Drawable() {

    private var hostView: View? = null
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
    private var capturing = false
    private val locHost = IntArray(2)
    private val locEx = IntArray(2)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun setLiveBlur(v: Boolean) { liveBlur = v }

    fun attach(host: View, exclude: View) {
        hostView = host
        excludeView = exclude
        if (liveBlur) {
            host.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            host.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            requestRefresh()
        }
    }

    fun setRadius(r: Float) { radius = r }
    fun setBlurRadius(px: Float) { blurRadius = px.coerceIn(6f, 40f) }
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
        handler.postDelayed({ pending = false; refresh() }, 140)
    }

    private fun refresh() {
        val host = hostView ?: return
        val ex = excludeView ?: return
        if (ex.visibility != View.VISIBLE) return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return
        if (capturing) { handler.postDelayed({ refresh() }, 120); return }
        capturing = true

        val div = blurRadius
        val tw = (w / div).toInt().coerceIn(2, 80)
        val th = (h / div).toInt().coerceIn(2, 80)

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

        host.getLocationInWindow(locHost)
        ex.getLocationInWindow(locEx)
        val dx = (locEx[0] - locHost[0]).toFloat()
        val dy = (locEx[1] - locHost[1]).toFloat()

        // форсим софтверный слой на время кадра -> host.draw реально пишет пиксели,
        // а не пустой кадр (причина, по которой блюра не было)
        host.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val cs = Canvas(localSrc)
        cs.drawColor(0x00000000)
        cs.translate(-dx, -dy)
        cs.clipRect(0f, 0f, w.toFloat(), h.toFloat())
        try { host.draw(cs) } catch (e: Exception) {}
        host.setLayerType(View.LAYER_TYPE_NONE, null)

        rTiny.set(0f, 0f, tw.toFloat(), th.toFloat())
        val ct = Canvas(localTiny)
        ct.drawColor(0x00000000)
        ct.drawBitmap(localSrc, null, rTiny, paint)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val co = Canvas(localOut)
        co.drawColor(0x00000000)
        co.drawBitmap(localTiny, null, rect, paint)

        blurred = localOut
        capturing = false
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
