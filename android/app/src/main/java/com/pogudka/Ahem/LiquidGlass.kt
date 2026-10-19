package com.pogudka.Ahem

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal fun ic(c: Int): Color = Color(
    (c shr 16 and 0xFF) / 255f,
    (c shr 8 and 0xFF) / 255f,
    (c and 0xFF) / 255f,
    (c shr 24 and 0xFF) / 255f
)

private object PixelCopyRef {
    private val cls by lazy { try { Class.forName("android.window.PixelCopy") } catch (t: Throwable) { null } }
    private val listenerCls by lazy { try { Class.forName("android.window.PixelCopy\$OnPixelCopyFinishedListener") } catch (t: Throwable) { null } }
    private val requestMethod: Method? by lazy {
        try {
            val l = listenerCls ?: return@lazy null
            cls?.getMethod("request", Window::class.java, Rect::class.java, Bitmap::class.java, l, Handler::class.java)
        } catch (t: Throwable) { null }
    }
    val available: Boolean get() = requestMethod != null && listenerCls != null
    fun request(win: Window, rect: Rect, bmp: Bitmap, onResult: (Int) -> Unit, handler: Handler): Boolean {
        val m = requestMethod ?: return false
        val l = listenerCls ?: return false
        val ih = InvocationHandler { _, method, args ->
            if (method.name == "onPixelCopyFinished" && args != null && args.isNotEmpty()) onResult(args[0] as Int)
            null
        }
        val proxy = Proxy.newProxyInstance(l.classLoader, arrayOf(l), ih)
        return try { m.invoke(null, win, rect, bmp, proxy, handler); true } catch (t: Throwable) { false }
    }
}

private const val REFRACT = 0.30f
private const val CHROMA = 0.16f
private const val EDGE = 7f
private const val HL = 0.95f
private const val BLUR_R = 9

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color.Unspecified,
    border: Color = ic(P.border),
    blur: Int = BLUR_R,
    tabKey: Any? = null,
    tick: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val window: Window? = activity?.window
    val dark = isDark(ctx)
    val effTint = if (tint != Color.Unspecified) tint else if (dark) Color(0x1FFFFFFF) else Color(0x14FFFFFF)

    var bg by remember { mutableStateOf<Bitmap?>(null) }
    var loc by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var capturing by remember { mutableStateOf(false) }
    var lastTab by remember { mutableStateOf(tabKey) }
    val shape = RoundedCornerShape(corner)

    LaunchedEffect(loc, sz, tabKey, tick) {
        val w = window ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        if (sz.width <= 0 || sz.height <= 0) return@LaunchedEffect
        val full = (bg == null) || (tabKey != lastTab)
        lastTab = tabKey
        capturing = true
        delay(if (full) 80L else 30L)
        var raw: Bitmap? = null
        if (PixelCopyRef.available) {
            var tries = 0
            while (tries < 3 && raw == null) {
                tries++
                val rect = Rect(loc.x.toInt(), loc.y.toInt(), loc.x.toInt() + sz.width, loc.y.toInt() + sz.height)
                val cap = try { Bitmap.createBitmap(sz.width, sz.height, Bitmap.Config.ARGB_8888) } catch (t: Throwable) { break }
                var done = false; var code = -1
                val ok = PixelCopyRef.request(w, rect, cap, { res ->
                    code = res
                    if (res == 0) raw = cap else cap.recycle()
                    done = true
                }, Handler(Looper.getMainLooper()))
                if (!ok) { cap.recycle(); break }
                var wait = 0
                while (!done && wait < 40) { delay(16); wait++ }
                if (code != 0) Log.w("AhemGlass", "PixelCopy res=$code try=$tries")
            }
        }
        if (raw == null) {
            try {
                val dv = act.window.decorView
                val dw = dv.width; val dh = dv.height
                if (dw > 0 && dh > 0) {
                    val fullBmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
                    dv.draw(Canvas(fullBmp))
                    val x = loc.x.toInt().coerceIn(0, dw - 1)
                    val y = loc.y.toInt().coerceIn(0, dh - 1)
                    val cw = sz.width.coerceAtMost(dw - x)
                    val ch = sz.height.coerceAtMost(dh - y)
                    if (cw > 0 && ch > 0) { raw = Bitmap.createBitmap(fullBmp, x, y, cw, ch); fullBmp.recycle() } else fullBmp.recycle()
                }
            } catch (t: Throwable) { Log.w("AhemGlass", "draw-fail ${t.message}") }
        }
        val src = raw
        if (src != null) {
            val out = withContext(Dispatchers.Default) {
                try {
                    if (full) refractGlass(cpuBlur(src, src.width, src.height, blur), src.width, src.height)
                    else cpuBlur(src, src.width, src.height, blur)
                } catch (t: Throwable) { Log.w("AhemGlass", "proc-fail ${t.message}"); null }
            }
            if (out != null) bg = out
            if (out !== src) src.recycle()
        }
        capturing = false
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { c -> loc = c.positionInWindow(); sz = c.size }
            .clip(shape)
            .border(1.dp, border, shape)
    ) {
        if (!capturing) {
            val b = bg
            if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(effTint))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(1f, 1f, 1f, 0.16f), Color(1f, 1f, 1f, 0f), Color(1f, 1f, 1f, 0.06f)))))
        Box(Modifier.fillMaxSize(), content = content)
    }
}

private fun refractGlass(src: Bitmap, w: Int, h: Int): Bitmap {
    val pix = IntArray(w * h)
    src.getPixels(pix, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)
    val cx = w / 2f; val cy = h / 2f
    val hw = w / 2f; val hh = h / 2f
    val rad = min(hw, hh)
    val innerW = max(hw - rad, 0f); val innerH = max(hh - rad, 0f)
    val dispMax = REFRACT * rad
    val edgePx = EDGE
    val lx = 0.7071f; val ly = 0.7071f
    for (y in 0 until h) {
        val py = y - cy
        val cly = py.coerceIn(-innerH, innerH)
        val dy = py - cly
        for (x in 0 until w) {
            val px = x - cx
            val clx = px.coerceIn(-innerW, innerW)
            val dx = px - clx
            val dl = sqrt(dx * dx + dy * dy)
            val idx = y * w + x
            if (dl < 1e-3f) { out[idx] = pix[idx]; continue }
            val nx = -dx / dl
            val ny = -dy / dl
            val t = (dl / rad).coerceIn(0f, 1f)
            val disp = dispMax * t * t
            val dr = disp
            val dg = disp * (1f - CHROMA)
            val db = disp * (1f - 2f * CHROMA)
            val rx = (x + nx * dr).toInt().coerceIn(0, w - 1)
            val ry = (y + ny * dr).toInt().coerceIn(0, h - 1)
            val gx = (x + nx * dg).toInt().coerceIn(0, w - 1)
            val gy = (y + ny * dg).toInt().coerceIn(0, h - 1)
            val bx = (x + nx * db).toInt().coerceIn(0, w - 1)
            val by = (y + ny * db).toInt().coerceIn(0, h - 1)
            val vr = (pix[ry * w + rx] shr 16) and 0xFF
            val vg = (pix[gy * w + gx] shr 8) and 0xFF
            val vb = (pix[by * w + bx]) and 0xFF
            val edgeT = ((dl - (rad - edgePx)) / edgePx).coerceIn(0f, 1f)
            val ang = (nx * lx + ny * ly).coerceIn(0f, 1f)
            val add = edgeT * (0.30f + 0.70f * ang) * HL
            val sh = edgeT * (0.55f - 0.55f * ang) * 0.30f
            var r = vr + ((255 - vr) * add).toInt()
            var g = vg + ((255 - vg) * add).toInt()
            var b = vb + ((255 - vb) * add).toInt()
            r = (r * (1f - sh)).toInt().coerceIn(0, 255)
            g = (g * (1f - sh)).toInt().coerceIn(0, 255)
            b = (b * (1f - sh)).toInt().coerceIn(0, 255)
            out[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    res.setPixels(out, 0, w, 0, 0, w, h)
    return res
}

private fun cpuBlur(src: Bitmap, w: Int, h: Int, radius: Int): Bitmap {
    if (radius <= 0) return src
    val down = 6
    val tw = (w / down).coerceAtLeast(2); val th = (h / down).coerceAtLeast(2)
    val tiny = Bitmap.createScaledBitmap(src, tw, th, true)
    val pix = IntArray(tw * th); tiny.getPixels(pix, 0, tw, 0, 0, tw, th)
    val r = (radius / down).coerceAtLeast(1)
    boxH(pix, tw, th, r); boxV(pix, tw, th, r)
    tiny.setPixels(pix, 0, tw, 0, 0, tw, th)
    return Bitmap.createScaledBitmap(tiny, w, h, true)
}
private fun boxH(p: IntArray, w: Int, h: Int, r: Int) {
    for (y in 0 until h) { val row = y * w; val tmp = IntArray(w)
        for (x in 0 until w) { var a=0;var rr=0;var gg=0;var bb=0;var c=0
            val lo=(x-r).coerceAtLeast(0); val hi=(x+r).coerceAtMost(w-1)
            for (xx in lo..hi){val v=p[row+xx];a+=(v ushr 24) and 0xFF;rr+=(v ushr 16) and 0xFF;gg+=(v ushr 8) and 0xFF;bb+=v and 0xFF;c++}
            tmp[x]=(a/c shl 24) or (rr/c shl 16) or (gg/c shl 8) or (bb/c) }
        for (x in 0 until w) p[row+x]=tmp[x] } }
private fun boxV(p: IntArray, w: Int, h: Int, r: Int) {
    for (x in 0 until w) { val tmp = IntArray(h)
        for (y in 0 until h) { var a=0;var rr=0;var gg=0;var bb=0;var c=0
            val lo=(y-r).coerceAtLeast(0); val hi=(y+r).coerceAtMost(h-1)
            for (yy in lo..hi){val v=p[yy*w+x];a+=(v ushr 24) and 0xFF;rr+=(v ushr 16) and 0xFF;gg+=(v ushr 8) and 0xFF;bb+=v and 0xFF;c++}
            tmp[y]=(a/c shl 24) or (rr/c shl 16) or (gg/c shl 8) or (bb/c) }
        for (y in 0 until h) p[y*w+x]=tmp[y] } }
