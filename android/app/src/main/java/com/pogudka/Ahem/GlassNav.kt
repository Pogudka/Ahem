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

    private var sourceRoot: View? = null
    private var excludeView: View? = null
    private var liveBlur = true
    private var blurred: Bitmap? = null
    private var src: Bitmap? = null
    private var small: Bitmap? = null
    private var quarter: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rSmall = RectF()
    private val rQuarter = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurLevel = 3
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val locSelf = IntArray(2)
    private val locRoot = IntArray(2)

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestRefresh() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { requestRefresh() }

    fun setLiveBlur(v: Boolean) { liveBlur = v }

    fun attach(root: View, exclude: View) {
        sourceRoot = root
        excludeView = exclude
        if (liveBlur) {
            root.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            requestRefresh()
        }
    }

    fun setRadius(r: Float) { radius = r }
    fun setBlurLevel(level: Int) { blurLevel = level.coerceIn(1, 5) }
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
        val root = sourceRoot ?: return
        val ex = excludeView ?: return
        if (ex.visibility != View.VISIBLE) return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return

        val hw = (w / 2).coerceAtLeast(1)
        val hh = (h / 2).coerceAtLeast(1)
        val qw = (w / 4).coerceAtLeast(1)
        val qh = (h / 4).coerceAtLeast(1)

        // локальные non-null буферы: smart cast на поля класса не требуется
        val localSrc: Bitmap
        val es = src
        if (es != null && es.width == w && es.height == h) localSrc = es
        else { localSrc = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); src = localSrc }

        val localSmall: Bitmap
        val em = small
        if (em != null && em.width == hw && em.height == hh) localSmall = em
        else { localSmall = Bitmap.createBitmap(hw, hh, Bitmap.Config.ARGB_8888); small = localSmall }

        val localQuarter: Bitmap
        val eq = quarter
        if (eq != null && eq.width == qw && eq.height == qh) localQuarter = eq
        else { localQuarter = Bitmap.createBitmap(qw, qh, Bitmap.Config.ARGB_8888); quarter = localQuarter }

        ex.getLocationInWindow(locSelf)
        root.getLocationInWindow(locRoot)
        val dx = (locSelf[0] - locRoot[0]).toFloat()
        val dy = (locSelf[1] - locRoot[1]).toFloat()
        val oldVis = ex.visibility
        ex.visibility = View.INVISIBLE
        val cs = Canvas(localSrc)
        cs.drawColor(0x00000000)
        cs.translate(-dx, -dy)
        cs.clipRect(0f, 0f, w.toFloat(), h.toFloat())
        try { root.draw(cs) } catch (e: Exception) {}
        ex.visibility = oldVis

        rSmall.set(0f, 0f, hw.toFloat(), hh.toFloat())
        rQuarter.set(0f, 0f, qw.toFloat(), qh.toFloat())

        val cd1 = Canvas(localSmall)
        cd1.drawColor(0x00000000)
        cd1.drawBitmap(localSrc, null, rSmall, paint)
        var cur: Bitmap = localSmall
        for (i in 0 until blurLevel) {
            val cq = Canvas(localQuarter)
            cq.drawColor(0x00000000)
            cq.drawBitmap(cur, null, rQuarter, paint)
            val cu = Canvas(localSmall)
            cu.drawColor(0x00000000)
            cu.drawBitmap(localQuarter, null, rSmall, paint)
            cur = localSmall
        }
        blurred = cur
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
