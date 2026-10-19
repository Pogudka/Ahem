package com.pogudka.Ahem

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal val fontUnbounded = FontFamily(Font(R.font.unbounded))
internal val fontUbuntuMed = FontFamily(Font(R.font.ubuntu_medium))
internal val fontUbuntuReg = FontFamily(Font(R.font.ubuntu_regular))

internal suspend fun awaitOnce(ref: com.google.firebase.database.DatabaseReference): DataSnapshot =
    suspendCancellableCoroutine { cont ->
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { if (cont.isActive) cont.resume(s) }
            override fun onCancelled(e: DatabaseError) { if (cont.isActive) cont.resumeWith(Result.failure(RuntimeException(e.message ?: "db"))) }
        }
        ref.addListenerForSingleValueEvent(l)
        cont.invokeOnCancellation { try { ref.removeEventListener(l) } catch (t: Throwable) {} }
    }

internal fun avatarBitmapC(nick: String, px: Int): Bitmap {
    val size = px.coerceAtLeast(2)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = P.border
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    val tp = Paint(Paint.ANTI_ALIAS_FLAG); tp.color = P.textSec; tp.textSize = size / 2.5f
    tp.textAlign = Paint.Align.CENTER; tp.typeface = Typeface.DEFAULT_BOLD
    val initial = if (nick.isNotEmpty()) nick[0].uppercaseChar().toString() else "?"
    val fm = tp.fontMetrics; val y = size / 2f - (fm.ascent + fm.descent) / 2f
    c.drawText(initial, size / 2f, y, tp)
    return bmp
}

@Composable
internal fun Avatar(b64: String, nick: String, size: Dp) {
    val ctx = LocalContext.current
    val px = (size.value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(2)
    var bmp by remember(b64, nick, px) { mutableStateOf<Bitmap?>(if (b64.isNotEmpty()) decodeScaled(b64, px) else null) }
    if (bmp == null && b64.isNotEmpty()) LaunchedEffect(b64, px) { bmp = decodeScaled(b64, px) }
    val shown = bmp?.let { makeCircularBitmap(it, px) } ?: avatarBitmapC(nick, px)
    Image(bitmap = shown.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
}

@Composable
internal fun AuthorAvatar(uid: String, nickFallback: String, size: Dp) {
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    val px = (size.value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(2)
    var bmp by remember(uid, px) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uid, px) {
        if (act == null || uid.isEmpty()) return@LaunchedEffect
        try {
            val s = awaitOnce(act.database.child("users").child(uid).child("avatar"))
            val str = s.getValue(String::class.java) ?: ""
            if (str.isNotEmpty()) bmp = decodeScaled(str, px)
        } catch (t: Throwable) {}
    }
    val shown = bmp?.let { makeCircularBitmap(it, px) } ?: avatarBitmapC(nickFallback, px)
    Image(bitmap = shown.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
}

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, radius: Int = 18, filled: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier = modifier
            .background(if (filled) ic(P.surface) else Color.Transparent, shape)
            .border(1.dp, ic(P.border), shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content
    )
}

@Composable
internal fun AccentButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.background(ic(P.accent), RoundedCornerShape(12.dp)).clickable { onClick() }.padding(14.dp, 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ic(P.accentText), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.background(Color.Transparent, RoundedCornerShape(12.dp)).border(1.dp, ic(P.border), RoundedCornerShape(12.dp)).clickable { onClick() }.padding(14.dp, 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = ic(P.text), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GlassIconButton(res: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.size(38.dp).clip(CircleShape).background(ic(P.navGlass)).border(1.dp, ic(P.border), CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(painter = painterResource(res), contentDescription = null, tint = ic(P.text), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun StatCol(num: Long, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Text(fmtNum(num), color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ic(P.textSec), fontSize = 11.sp)
    }
}

private val urlRegex = Regex("https?://[^\\s]+")

internal fun linkify(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in urlRegex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val url = m.value
        withStyle(SpanStyle(color = ic(P.accent), textDecoration = TextDecoration.Underline)) { append(url) }
        addStringAnnotation(tag = "URL", annotation = url, start = length - url.length, end = length)
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
internal fun LinkText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val annotated = remember(text) { linkify(text) }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontFamily = fontUbuntuReg),
        onClick = { off ->
            annotated.getStringAnnotations("URL", off, off).firstOrNull()?.let { a ->
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.item))) } catch (t: Throwable) {}
            }
        }
    )
}
