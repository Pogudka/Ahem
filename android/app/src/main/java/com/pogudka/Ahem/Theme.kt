_package com.pogudka.Ahem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot

internal object P {
    var bg = 0xFFFFFFFF.toInt()
    var surface = 0xFFF7F7F9.toInt()
    var fieldBg = 0xFFFFFFFF.toInt()
    var border = 0xFFE2E2E6.toInt()
    var text = 0xFF000000.toInt()
    var textSec = 0xFF8A8A8E.toInt()
    var accent = 0xFF000000.toInt()
    var accentText = 0xFFFFFFFF.toInt()
    var navInactive = 0xFF9A9AA0.toInt()
    var heartOn = 0xFF000000.toInt()
    var scrim = 0x66000000.toInt()
}

internal fun applyTheme(ctx: Context, dark: Boolean) {
    if (dark) {
        P.bg = 0xFF000000.toInt()
        P.surface = 0xFF1C1C1E.toInt()
        P.fieldBg = 0xFF1C1C1E.toInt()
        P.border = 0xFF3A3A3C.toInt()
        P.text = 0xFFFFFFFF.toInt()
        P.textSec = 0xFF98989F.toInt()
        P.accent = 0xFFFFFFFF.toInt()
        P.accentText = 0xFF000000.toInt()
        P.navInactive = 0xFF7C7C82.toInt()
        P.heartOn = 0xFFFFFFFF.toInt()
        P.scrim = 0x99000000.toInt()
    } else {
        P.bg = 0xFFFFFFFF.toInt()
        P.surface = 0xFFF7F7F9.toInt()
        P.fieldBg = 0xFFFFFFFF.toInt()
        P.border = 0xFFE2E2E6.toInt()
        P.text = 0xFF000000.toInt()
        P.textSec = 0xFF8A8A8E.toInt()
        P.accent = 0xFF000000.toInt()
        P.accentText = 0xFFFFFFFF.toInt()
        P.navInactive = 0xFF9A9AA0.toInt()
        P.heartOn = 0xFF000000.toInt()
        P.scrim = 0x66000000.toInt()
    }
}

internal fun isDark(ctx: Context): Boolean =
    ctx.getSharedPreferences("ahem", Context.MODE_PRIVATE).getBoolean("dark", false)

internal fun dbDenied(act: MainActivity, error: DatabaseError) {
    if (error.code == "PERMISSION_DENIED") {
        Toast.makeText(act, "Нет доступа к БД: обнови Rules в Firebase Console", Toast.LENGTH_LONG).show()
    }
}

internal fun trimNum(d: Double): String {
    val r = Math.round(d * 10.0) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
}

internal fun fmtNum(n: Long): String {
    if (n >= 1_000_000L) return trimNum(n / 1_000_000.0) + "M"
    if (n >= 1_000L) return trimNum(n / 1_000.0) + "K"
    return n.toString()
}

internal fun getTs(post: DataSnapshot): Long {
    val v = post.child("timestamp").value
    if (v is Number) return v.toLong()
    return (v as? String)?.toLongOrNull() ?: 0L
}

internal fun parseCount(t: TextView): Int {
    val s = t.text.toString().substringAfter("♥").trim()
    if (s.isEmpty()) return 0
    var numberPart = s
    var multiplier = 1L
    if (numberPart.endsWith("M", ignoreCase = true)) {
        multiplier = 1_000_000L
        numberPart = numberPart.dropLast(1)
    } else if (numberPart.endsWith("K", ignoreCase = true)) {
        multiplier = 1_000L
        numberPart = numberPart.dropLast(1)
    }
    val value = numberPart.toDoubleOrNull() ?: return 0
    val result = (value * multiplier).toLong()
    return when {
        result > Int.MAX_VALUE -> Int.MAX_VALUE
        result < 0 -> 0
        else -> result.toInt()
    }
}

internal fun decodeScaled(b64: String, reqPx: Int): Bitmap? {
    return try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val req = reqPx.coerceAtLeast(1)
        while (bounds.outWidth / sample / 2 >= req && bounds.outHeight / sample / 2 >= req) {
            sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (e: Exception) {
        null
    }
}

internal fun makeCircularBitmap(source: Bitmap, targetSize: Int): Bitmap {
    val side = minOf(source.width, source.height)
    val sq = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(sq, targetSize, targetSize, true)
    val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f - 1f, paint)
    return out
}
