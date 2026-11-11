package com.pogudka.Ahem

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Твои шрифты
internal val fontUnbounded = FontFamily(Font(R.font.unbounded))
internal val fontUbuntuReg = FontFamily(Font(R.font.ubuntu_regular))

// Параллельный декод (не ограничиваем 2 потоками — картинки появляются без задержки)
private val decodePool = Dispatchers.Default

// ic(Int) — в LiquidGlass.kt; P/fmtNum/getTs/isDark/applyTheme/dbDenied/decodeScaled — в Theme.kt;
// readOnce/readStatsInto/txWithRetry/scheduleMigration — в Stats.kt; Msg/PeerRow/чаты — в Chats.kt;
// Acc/аккаунты — в Account.kt; VideoScreen — в Video.kt; LiquidGlass — в LiquidGlass.kt.

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, radius: Int = 18, withBorder: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(ic(P.surface), RoundedCornerShape(radius.dp))
            .then(if (withBorder) Modifier.border(1.dp, ic(P.border), RoundedCornerShape(radius.dp)) else Modifier)
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
internal fun GlassIconButton(icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = ic(P.text))
    }
}

@Composable
internal fun AccentButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(ic(P.accent), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = ic(P.accentText), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(Color.Transparent, RoundedCornerShape(14.dp))
            .border(1.dp, ic(P.border), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = ic(P.text), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
internal fun Avatar(b64: String, nick: String, sizeDp: Dp, stableKey: String? = null) {
    val shown = if (b64.isNotEmpty()) rememberDecoded(b64, (sizeDp.value * 2).toInt(), stableKey) else null
    Box(
        Modifier.size(sizeDp).clip(CircleShape).background(ic(P.fieldBg)),
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(sizeDp))
        else Text(nick.take(1).uppercase(), color = ic(P.text), fontSize = (sizeDp.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun AuthorAvatar(userId: String, nick: String, sizeDp: Dp) {
    var b64 by remember(userId) { mutableStateOf<String?>(null) }
    val act = LocalContext.current as? MainActivity
    LaunchedEffect(userId) {
        b64 = act?.avatarCache?.value?.get(userId)
        if (b64.isNullOrEmpty() && act != null && userId.isNotEmpty()) {
            try {
                val s = awaitOnce(act.database.child("users").child(userId).child("avatar"))
                val v = s.getValue(String::class.java) ?: ""
                if (v.isNotEmpty()) {
                    act.avatarCache.value[userId] = v
                    b64 = v
                }
            } catch (t: Throwable) {}
        }
    }
    Avatar(b64 ?: "", nick, sizeDp, "av:$userId")
}

@Composable
internal fun StatCol(value: Long, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmtNum(value), color = ic(P.text), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ic(P.textSec), fontSize = 11.sp)
    }
}

internal fun putAvatar(act: MainActivity, uid: String, b64: String) {
    act.avatarCache.value[uid] = b64
}
internal fun putAvatars(act: MainActivity, map: Map<String, String>) {
    for ((k, v) in map) act.avatarCache.value[k] = v
}
internal fun hasAvatar(act: MainActivity, uid: String): Boolean = act.avatarCache.value.containsKey(uid)

internal suspend fun awaitOnce(ref: DatabaseReference): DataSnapshot = suspendCancellableCoroutine { cont ->
    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { if (cont.isActive) cont.resume(s) }
        override fun onCancelled(e: DatabaseError) { if (cont.isActive) cont.resumeWith(Result.failure(e.toException())) }
    })
}

// stableKey — стабильный ключ (postId:idx / av:uid / pending:i / zoom:page).
// Без него считается hashCode мегабайтной строки на каждой рекомпозиции — это и был тормоз.
@Composable
internal fun rememberDecoded(b64: String, px: Int, stableKey: String? = null): Bitmap? {
    val act = LocalContext.current as? MainActivity
    val key = stableKey ?: (b64.hashCode().toString() + ":" + px)
    var bmp by remember(key) { mutableStateOf<Bitmap?>(act?.bmpCache?.get(key)) }
    LaunchedEffect(key) {
        val cached = act?.bmpCache?.get(key)
        if (cached != null) { bmp = cached; return@LaunchedEffect }
        if (b64.isEmpty()) { bmp = null; return@LaunchedEffect }
        val d = withContext(decodePool) { decodeScaled(b64, px) }
        if (d != null) act?.bmpCache?.put(key, d)
        bmp = d
    }
    return bmp
}

@Composable
internal fun LinkText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val urlRe = remember { Regex("https?://\\S+") }
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in urlRe.findAll(text)) {
                append(text.substring(last, m.range.first))
                withStyle(SpanStyle(color = Color(0xFF4A90E2))) {
                    append(text.substring(m.range))
                }
                addStringAnnotation(tag = "URL", annotation = m.value, start = m.range.first, end = m.range.last + 1)
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    val ctx = LocalContext.current
    ClickableText(
        text = annotated,
        style = TextStyle(color = color, fontSize = fontSize, fontFamily = fontUbuntuReg),
        modifier = modifier,
        onClick = { off ->
            annotated.getStringAnnotations("URL", off, off).firstOrNull()?.let {
                try {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(it.item)))
                } catch (t: Throwable) {}
            }
        }
    )
}
