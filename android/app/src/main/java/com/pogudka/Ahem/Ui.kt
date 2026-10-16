package com.pogudka.Ahem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

internal fun createAvatarBitmap(act: MainActivity, nick: String): Bitmap {
    val size = act.dp(100)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    paint.isAntiAlias = true
    paint.color = P.border
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    val textPaint = Paint()
    textPaint.color = P.textSec
    textPaint.textSize = size / 2.5f
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.isAntiAlias = true
    textPaint.typeface = act.fBold
    val initial = if (nick.isNotEmpty()) nick[0].uppercaseChar() else '?'
    val metrics = textPaint.fontMetrics
    val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(initial.toString(), size / 2f, y, textPaint)
    return bmp
}

internal fun avatarView(act: MainActivity, bmp: Bitmap?, size: Int, nick: String): ImageView {
    val iv = ImageView(act)
    iv.layoutParams = act.lp(size, size, 0)
    iv.scaleType = ImageView.ScaleType.FIT_CENTER
    iv.setImageBitmap(bmp ?: makeCircularBitmap(createAvatarBitmap(act, nick), size))
    return iv
}

internal fun surf(act: MainActivity, radius: Int, filled: Boolean = true): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(if (filled) P.surface else Color.TRANSPARENT)
    g.cornerRadius = act.dp(radius).toFloat()
    g.setStroke(act.dp(1), P.border)
    return g
}

internal fun fieldBg(act: MainActivity): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(P.fieldBg)
    g.cornerRadius = act.dp(14).toFloat()
    g.setStroke(act.dp(1), P.border)
    return g
}

internal fun accentBg(act: MainActivity, radius: Int): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(P.accent)
    g.cornerRadius = act.dp(radius).toFloat()
    return g
}

internal fun tv(act: MainActivity, text: String, size: Float, type: Typeface, color: Int): TextView {
    val t = TextView(act)
    t.text = text
    t.textSize = size
    t.typeface = type
    t.setTextColor(color)
    return t
}

internal fun field(act: MainActivity, hint: String): EditText {
    val e = EditText(act)
    e.hint = hint
    e.setHintTextColor(P.textSec)
    e.setTextColor(P.text)
    e.typeface = act.fReg
    e.textSize = 15f
    e.background = fieldBg(act)
    e.setPadding(act.dp(16), act.dp(13), act.dp(16), act.dp(13))
    return e
}

internal fun btnOutline(act: MainActivity, label: String): TextView {
    val t = tv(act, label, 14f, act.fSemi, P.text)
    t.gravity = Gravity.CENTER
    t.background = surf(act, 12, filled = false)
    t.setPadding(act.dp(14), act.dp(11), act.dp(14), act.dp(11))
    return t
}

internal fun btnAccent(act: MainActivity, label: String): TextView {
    val t = tv(act, label, 14f, act.fSemi, P.accentText)
    t.gravity = Gravity.CENTER
    t.background = accentBg(act, 12)
    t.setPadding(act.dp(14), act.dp(11), act.dp(14), act.dp(11))
    return t
}

internal fun icon(act: MainActivity, res: Int, tint: Int, size: Int): ImageView {
    val iv = ImageView(act)
    iv.setImageResource(res)
    iv.setColorFilter(tint)
    iv.layoutParams = act.lp(size, size, 0)
    return iv
}

internal fun iconRound(act: MainActivity, res: Int, tint: Int, size: Int): ImageView {
    val iv = ImageView(act)
    iv.setImageResource(res)
    iv.setColorFilter(tint)
    iv.layoutParams = act.lp(size, size, 0)
    iv.setPadding(act.dp(9), act.dp(9), act.dp(9), act.dp(9))
    val g = GlassDrawable()
    g.setRadius(size / 2f)
    g.setBlurRadius(act.dp(10).toFloat())
    g.setGlassTint(P.navGlass)
    g.setBorder(P.border, act.dp(1).toFloat())
    iv.background = g
    if (::act.rootFrame.isInitializedSafe()) {
        g.attach(act.rootFrame, iv)
    }
    return iv
}

private fun ::MainActivity.rootFrame.isInitializedSafe(): Boolean = true
