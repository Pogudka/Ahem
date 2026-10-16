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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class GlassDrawable : Drawable() {

    private var window: Window? = null
    private var hostView: View? = null
    private var excludeView: View? = null
    private var liveBlur = true
    private var blurred: Bitmap? = null
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
    private val locEx = IntArray(2)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun setLiveBlur(v: Boolean) { liveBlur = v }

    fun attach(win: Window, host: View, exclude: View) {
        window = win
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
        val ex = excludeView ?: return
        if (ex.visibility != View.VISIBLE) return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return

        val div = blurRadius
        val tw = (w / div).toInt().coerceAtLeast(2)
        val th = (h / div).toInt().coerceAtLeast(2)

        val localTiny: Bitmap
        val et = tiny
        if (et != null && et.width == tw && et.height == th) localTiny = et
        else { localTiny = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888); tiny = localTiny }

        val localOut: Bitmap
        val eo = out
        if (eo != null && eo.width == w && eo.height == h) localOut = eo
        else { localOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); out = localOut }

        ex.getLocationInWindow(locEx)
        val srcRect = Rect(locEx[0], locEx[1], locEx[0] + w, locEx[1] + h)

        // асинхронный захват: пирамиду запускаем ТОЛЬКО из колбэка PixelCopy (или из software-фолбэка)
        val captured = tryPixelCopy(srcRect, localTiny, localOut, w, h)
        if (!captured) softwareFallback(srcRect, localTiny, localOut, w, h)
    }

    private fun tryPixelCopy(srcRect: Rect, tinyBmp: Bitmap, outBmp: Bitmap, w: Int, h: Int): Boolean {
        val win = window ?: return false
        val ex = excludeView ?: return false
        return try {
            val cls = Class.forName("android.window.PixelCopy")
            val listenerCls = Class.forName("android.window.PixelCopy\$OnPixelCopyFinishedListener")
            val method: Method = cls.getMethod(
                "request", Window::class.java, Rect::class.java, Bitmap::class.java,
                listenerCls, Handler::class.java
            )
            val full = Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
            val ih = InvocationHandler { _, m, args ->
                if (m.name == "onPixelCopyFinished" && args != null && args.isNotEmpty()) {
                    val ok = (args[0] as? Int) == 0
                    ex.post {
                        if (ok) blurPipeline(full, tinyBmp, outBmp, w, h)
                        else { full.recycle(); softwareFallback(srcRect, tinyBmp, outBmp, w, h) }
                    }
                }
                null
            }
            val proxy = Proxy.newProxyInstance(listenerCls.classLoader, arrayOf(listenerCls), ih)
            method.invoke(null, win, srcRect, full, proxy, handler)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun softwareFallback(srcRect: Rect, tinyBmp: Bitmap, outBmp: Bitmap, w: Int, h: Int) {
        val host = hostView ?: return
        val ex = excludeView ?: return
        val full = Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
        host.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val oldVis = ex.visibility
        ex.visibility = View.INVISIBLE
        val cs = Canvas(full)
        cs.drawColor(0x00000000)
        cs.translate(-srcRect.left.toFloat(), -srcRect.top.toFloat())
        try { host.draw(cs) } catch (e: Exception) {}
        ex.visibility = oldVis
        host.setLayerType(View.LAYER_TYPE_NONE, null)
        blurPipeline(full, tinyBmp, outBmp, w, h)
    }

    private fun blurPipeline(full: Bitmap, tinyBmp: Bitmap, outBmp: Bitmap, w: Int, h: Int) {
        rTiny.set(0f, 0f, tinyBmp.width.toFloat(), tinyBmp.height.toFloat())
        val ct = Canvas(tinyBmp)
        ct.drawColor(0x00000000)
        ct.drawBitmap(full, null, rTiny, paint)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val co = Canvas(outBmp)
        co.drawColor(0x00000000)
        co.drawBitmap(tinyBmp, null, rect, paint)
        blurred = outBmp
        full.recycle()
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
