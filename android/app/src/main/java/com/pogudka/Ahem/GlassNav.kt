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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.ViewTreeObserver
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class GlassDrawable : Drawable() {

    private var window: Window? = null
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
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rTiny = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurPx = 30
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val locEx = IntArray(2)

    private val DIV = 8
    private val copyMethod: Method? by lazy { findPixelCopyRequest() }
    private val listenerCls: Class<*>? by lazy {
        try { Class.forName("android.window.PixelCopy\$OnPixelCopyFinishedListener") } catch (t: Throwable) { null }
    }

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun setLiveBlur(v: Boolean) { liveBlur = v }
    fun setRadius(r: Float) { radius = r }
    fun setBlurRadius(px: Float) { blurPx = px.toInt().coerceIn(8, 80) }
    fun setGlassTint(color: Int) { tintPaint.color = color }

    fun setBorder(color: Int, widthPx: Float) {
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
        innerPaint.color = 0x40FFFFFF
        innerPaint.style = Paint.Style.STROKE
        innerPaint.strokeWidth = widthPx
    }

    // window + хост-контейнер (не используется для захвата, держим для симметрии) + сама пилюля
    fun attach(win: Window, host: View, exclude: View) {
        window = win
        excludeView = exclude
        if (liveBlur && copyMethod != null && listenerCls != null) {
            host.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            host.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            requestRefresh()
            handler.postDelayed({ requestRefresh() }, 350)
            handler.postDelayed({ requestRefresh() }, 900)
        }
    }

    fun requestRefresh() {
        if (!liveBlur || pending) return
        pending = true
        handler.postDelayed({ pending = false; refresh() }, 120)
    }

    private fun findPixelCopyRequest(): Method? = try {
        val cls = Class.forName("android.window.PixelCopy")
        cls.getMethod(
            "request", Window::class.java, Rect::class.java, Bitmap::class.java,
            Class.forName("android.window.PixelCopy\$OnPixelCopyFinishedListener"), Handler::class.java
        )
    } catch (t: Throwable) { null }

    private fun refresh() {
        val ex = excludeView ?: return
        if (ex.visibility != View.VISIBLE) return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return
        val win = window ?: return
        val mReq = copyMethod ?: return
        val lCls = listenerCls ?: return
        if (Build.VERSION.SDK_INT < 26) return

        val tw = (w / DIV).coerceAtLeast(2)
        val th = (h / DIV).coerceAtLeast(2)

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
        val full = try {
            Bitmap.createBitmap(srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) { return }

        val ih = InvocationHandler { _, m, args ->
            if (m.name == "onPixelCopyFinished" && args != null && args.isNotEmpty()) {
                val ok = (args[0] as? Int) == 0 // PixelCopy.SUCCESS == 0
                ex.post {
                    if (ok) blurPipeline(full, localTiny, localOut, w, h)
                    else full.recycle()
                }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(lCls.classLoader, arrayOf(lCls), ih)
        try {
            mReq.invoke(null, win, srcRect, full, proxy, handler)
        } catch (t: Throwable) {
            full.recycle()
        }
    }

    private fun blurPipeline(full: Bitmap, tinyBmp: Bitmap, outBmp: Bitmap, w: Int, h: Int) {
        rTiny.set(0f, 0f, tinyBmp.width.toFloat(), tinyBmp.height.toFloat())
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        var cur: Bitmap = full
        for (i in 0 until 2) {
            val ct = Canvas(tinyBmp)
            ct.drawColor(0x00000000)
            ct.drawBitmap(cur, null, rTiny, paint)
            val co = Canvas(outBmp)
            co.drawColor(0x00000000)
            co.drawBitmap(tinyBmp, null, rect, paint)
            cur = outBmp
        }
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
        if (liveBlur && b != null) canvas.drawBitmap(b, null, rect, paint)
        canvas.drawRect(rect, tintPaint)
        hiPaint.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, hiPaint)
        hiPaint.shader = null
        sheenPaint.shader = LinearGradient(0f, 0f, w, h, 0x14FFFFFF, 0x00000000, Shader.TileMode.CLAMP)
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
