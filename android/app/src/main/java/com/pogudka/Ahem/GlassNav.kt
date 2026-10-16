лpackage com.pogudka.Ahem

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
    private var blurred: Bitmap? = null
    private var src: Bitmap? = null
    private var smallBuf: Bitmap? = null
    private var bufA: Bitmap? = null
    private var bufB: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val dstRect = RectF()
    private val path = Path()
    private var radius = 0f
    private var blurLevel = 3
    private var borderColor = 0xFFE2E2E6.toInt()
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
    fun setBlurLevel(level: Int) { blurLevel = level.coerceIn(1, 6) }
    fun setGlassTint(color: Int) { tintPaint.color = color }

    fun setBorder(color: Int, widthPx: Float) {
        borderColor = color
        borderPaint.color = color
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = widthPx
        innerPaint.color = 0x33FFFFFF
        innerPaint.style = Paint.Style.STROKE
        innerPaint.strokeWidth = widthPx
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
        val sw = (w / 2).coerceAtLeast(1)
        val sh = (h / 2).coerceAtLeast(1)
        var small = smallBuf
        if (small == null || small.width != sw || small.height != sh) {
            small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            smallBuf = small
        }
        var a = bufA
        if (a == null || a.width != w || a.height != h) {
            a = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bufA = a
        }
        var b = bufB
        if (b == null || b.width != w || b.height != h) {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bufB = b
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

        // итеративный bilinear blur: down->up, несколько проходов = гауссоподобно, без пикселей
        dstRect.set(0f, 0f, sw.toFloat(), sh.toFloat())
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        var cur: Bitmap = s
        for (i in 0 until blurLevel) {
            val cd = Canvas(small)
            cd.drawColor(0x00000000)
            cd.drawBitmap(cur, null, dstRect, paint)
            val outBuf = if (i % 2 == 0) a else b
            val cu = Canvas(outBuf)
            cu.drawColor(0x00000000)
            cu.drawBitmap(small, null, full, paint)
            cur = outBuf
        }
        blurred = cur
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
