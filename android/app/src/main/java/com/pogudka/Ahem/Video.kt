package com.pogudka.Ahem

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

internal fun buildVideo(act: MainActivity): View {
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL
    col.gravity = Gravity.CENTER
    col.setBackgroundColor(P.bg)
    col.setPadding(act.dp(24), act.dp(24), act.dp(24), 24)
    val card = LinearLayout(act)
    card.orientation = LinearLayout.VERTICAL
    card.gravity = Gravity.CENTER
    card.background = surf(act, 20, filled = true)
    card.setPadding(act.dp(28), act.dp(40), act.dp(28), act.dp(40))
    card.addView(icon(act, R.drawable.ic_video, P.text, act.dp(48)))
    val t = tv(act, "Видео скоро", 18f, act.fBold, P.text)
    t.layoutParams = act.lp(-2, -2, act.dp(6))
    t.gravity = Gravity.CENTER
    val s = tv(act, "Здесь появится лента клипов", 14f, act.fReg, P.textSec)
    s.gravity = Gravity.CENTER
    card.addView(t)
    card.addView(s)
    col.addView(card)
    return col
}
