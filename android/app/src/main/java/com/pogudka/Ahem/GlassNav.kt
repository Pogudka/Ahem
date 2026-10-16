package com.pogudka.Ahem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

internal class GlassDrawable : Drawable() {

    private var sourceRoot: View? = null
    private var excludeView: View? = null
    private var blurred: Bitmap? = null
    private var mid: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var radius = 0f
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

    fun setRadius(r: Float) {
        radius = r
    }

    fun setTint(color: Int) {
        tintPaint.color = color
    }

    fun setBorder(color: Int, widthPx: Float) {
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
    }

    fun requestRefresh() {
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            refresh()
        }, 60)
    }

    private fun refresh() {
        val root = sourceRoot ?: return
        val ex = excludeView ?: return
        val w = ex.width
        val h = ex.height
        if (w <= 0 || h <= 0) return
        val sw = Math.max(1, w / 6)
        val sh = Math.max(1, h / 6)
        var small = mid
        if (small == null || small.width != sw || small.height != sh) {
            small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            mid = small
        }
        ex.getLocationInWindow(locSelf)
        root.getLocationInWindow(locRoot)
        val dx = (locSelf[0] - locRoot[0]).toFloat()
        val dy = (locSelf[1] - locRoot[1]).toFloat()
        val oldVis = ex.visibility
        ex.visibility = View.INVISIBLE
        val c = Canvas(small)
        c.scale(1f / 6f, 1f / 6f)
        c.translate(-dx, -dy)
        try {
            root.draw(c)
        } catch (e: Exception) {
        }
        ex.visibility = oldVis
        val sw2 = Math.max(1, sw / 2)
        val sh2 = Math.max(1, sh / 2)
        var out = blurred
        if (out == null || out.width != sw2 || out.height != sh2) {
            out = Bitmap.createBitmap(sw2, sh2, Bitmap.Config.ARGB_8888)
        }
        val c2 = Canvas(out)
        c2.drawBitmap(small, null, RectF(0f, 0f, sw2.toFloat(), sh2.toFloat()), paint)
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
        canvas.restore()
        canvas.drawPath(path, borderPaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
