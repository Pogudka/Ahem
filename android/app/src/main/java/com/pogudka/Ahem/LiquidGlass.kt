package com.pogudka.Ahem

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.draw.graphicsLayer
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
import kotlinx.coroutines.delay
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

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

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    tint: Color = ic(P.navGlass),
    border: Color = ic(P.border),
    blur: Int = 40,
    tick: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val activity = LocalContext.current as? Activity
    val window: Window? = activity?.window
    var bg by remember { mutableStateOf<Bitmap?>(null) }
    var loc by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var capturing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)

    LaunchedEffect(loc, sz, tick) {
        val w = window ?: return@LaunchedEffect
        if (!PixelCopyRef.available) return@LaunchedEffect
        if (sz.width <= 0 || sz.height <= 0) return@LaunchedEffect
        capturing = true
        delay(40)
        val rect = Rect(loc.x.toInt(), loc.y.toInt(), loc.x.toInt() + sz.width, loc.y.toInt() + sz.height)
        val cap = try { Bitmap.createBitmap(sz.width, sz.height, Bitmap.Config.ARGB_8888) } catch (t: Throwable) { capturing = false; return@LaunchedEffect }
        val ok = PixelCopyRef.request(w, rect, cap, { res ->
            val out = if (res == 0) cpuBlur(cap, sz.width, sz.height, blur) else null
            cap.recycle(); bg = out; capturing = false
        }, Handler(Looper.getMainLooper()))
        if (!ok) { cap.recycle(); capturing = false }
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (capturing) 0f else 1f }
            .clip(shape)
            .border(1.dp, border, shape)
    ) {
        val b = bg
        if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(tint))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(1f,1f,1f,0.35f), Color(1f,1f,1f,0f), Color(1f,1f,1f,0.08f)))))
        Box(Modifier.fillMaxSize(), content = content)
    }
}

private fun cpuBlur(src: Bitmap, w: Int, h: Int, radius: Int): Bitmap {
    val down = 8
    val tw = (w / down).coerceAtLeast(2); val th = (h / down).coerceAtLeast(2)
    val tiny = Bitmap.createScaledBitmap(src, tw, th, true)
    val pix = IntArray(tw * th); tiny.getPixels(pix, 0, tw, 0, 0, tw, th)
    val r = (radius / down).coerceAtLeast(2)
    boxH(pix, tw, th, r); boxV(pix, tw, th, r); boxH(pix, tw, th, r); boxV(pix, tw, th, r)
    tiny.setPixels(pix, 0, tw, 0, 0, tw, th)
    return Bitmap.createScaledBitmap(tiny, w, h, true).also { tiny.recycle() }
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
