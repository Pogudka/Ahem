package com.pogudka.Ahem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

internal data class Msg(val from: String, val text: String, val ts: Long)
internal data class PeerRow(val uid: String, val nick: String, val last: String, val ts: Long)
internal data class Acc(val uid: String, val nick: String, val email: String, val pass: String)

internal val fontUnbounded = androidx.compose.ui.text.font.FontFamily.Default
internal val fontUbuntuReg = androidx.compose.ui.text.font.FontFamily.Default

internal object P {
    val bg = Color(0)
    val surface = Color(0)
    val fieldBg = Color(0)
    val border = Color(0)
    val text = Color(0)
    val textSec = Color(0)
    val accent = Color(0)
    val accentText = Color(0)
    val navInactive = Color(0)
    val heartOn = Color(0)
}

internal fun ic(c: Color): Color = c

@Composable
internal fun CardSurface(modifier: Modifier = Modifier, radius: Int = 18, withBorder: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(ic(P.surface), RoundedCornerShape(radius.dp))
            .then(if (withBorder) Modifier.border(1.dp, ic(P.border), RoundedCornerShape(radius.dp)) else Modifier)
    ) { content() }
}

@Composable
internal fun LiquidGlass(modifier: Modifier = Modifier, border: Color = Color.Transparent, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Color(0x88000000), RoundedCornerShape(24.dp))
            .border(1.dp, border, RoundedCornerShape(24.dp))
    ) { content() }
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
internal fun Avatar(b64: String, nick: String, sizeDp: androidx.compose.ui.unit.Dp) {
    val shown = if (b64.isNotEmpty()) rememberDecoded(b64, (sizeDp.value * 2).toInt()) else null
    Box(
        Modifier.size(sizeDp).clip(CircleShape).background(ic(P.fieldBg)),
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(sizeDp))
        else Text(nick.take(1).uppercase(), color = ic(P.text), fontSize = (sizeDp.value * 0.5f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun AuthorAvatar(userId: String, nick: String, sizeDp: androidx.compose.ui.unit.Dp) {
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
    Avatar(b64 ?: "", nick, sizeDp)
}

@Composable
internal fun StatCol(value: Long, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val m = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmtNum(value), color = ic(P.text), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ic(P.textSec), fontSize = 11.sp)
    }
}

internal fun fmtNum(n: Long): String {
    if (n < 1000) return n.toString()
    if (n < 1_000_000) return String.format("%.1fК", n / 1000.0).replace(".0", "")
    return String.format("%.1fМ", n / 1_000_000.0).replace(".0", "")
}

internal fun getTs(p: DataSnapshot): Long = (p.child("timestamp").value as? Number)?.toLong() ?: 0L

internal fun isDark(ctx: Context): Boolean =
    ctx.getSharedPreferences("ahem", Context.MODE_PRIVATE).getBoolean("dark", true)

internal fun applyTheme(ctx: Context, dark: Boolean) {}

internal fun authErrorText(e: Exception?): String = e?.message ?: "Ошибка входа"
internal fun isAuthNetworkError(e: Exception?): Boolean = (e?.message ?: "").contains("network", ignoreCase = true)

internal fun dbDenied(act: MainActivity, e: DatabaseError) {}

internal fun scheduleMigration(act: MainActivity) {}

internal fun putAvatar(act: MainActivity, uid: String, b64: String) {
    act.avatarCache.value[uid] = b64
}

internal fun putAvatars(act: MainActivity, map: Map<String, String>) {
    for ((k, v) in map) act.avatarCache.value[k] = v
}

internal fun hasAvatar(act: MainActivity, uid: String): Boolean = act.avatarCache.value.containsKey(uid)

internal fun saveAccount(act: MainActivity, uid: String, nick: String, email: String, pass: String) {}
internal fun loadAccounts(act: MainActivity): List<Acc> = emptyList()
internal fun updatePostsNick(act: MainActivity, newNick: String) {}

internal fun loadMyFollowing(act: MainActivity, onDone: () -> Unit) {
    act.myFollowingIds.clear()
    act.database.child("following").child(act.currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { for (c in s.children) c.key?.let { act.myFollowingIds.add(it) }; onDone() }
        override fun onCancelled(e: DatabaseError) { onDone() }
    })
}

internal fun followUser(act: MainActivity, userId: String, nick: String) {
    if (act.currentUserId.isEmpty() || userId.isEmpty() || userId == act.currentUserId) return
    if (userId in act.myFollowingIds) return
    act.myFollowingIds.add(userId)
    act.database.child("following").child(act.currentUserId).child(userId).setValue(true)
    act.database.child("followers").child(userId).child(act.currentUserId).setValue(true)
    act.database.child("following").child(act.currentUserId).child(userId).child("nick").setValue(nick)
    act.database.child("followers").child(userId).child(act.currentUserId).child("nick").setValue(act.currentNick)
}

internal fun loadConversations(act: MainActivity) {
    act.peersList.clear()
    act.database.child("conversations").child(act.currentUserId).addValueEventListener(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            act.peersList.clear()
            for (c in s.children) {
                val peer = c.key ?: continue
                val nick = c.child("nick").getValue(String::class.java) ?: "?"
                val last = c.child("lastText").getValue(String::class.java) ?: ""
                val ts = (c.child("lastTs").value as? Number)?.toLong() ?: 0L
                act.peersList.add(PeerRow(peer, nick, last, ts))
            }
        }
        override fun onCancelled(e: DatabaseError) {}
    })
}

internal fun closeConversation(act: MainActivity) {
    act.chatOpen.value = false
    act.chatPeerUid = ""
    act.chatPeerNick = ""
    act.chatMsgs.clear()
}

internal fun openChat(act: MainActivity, peerUid: String, peerNick: String) {
    act.chatPeerUid = peerUid
    act.chatPeerNick = peerNick
    act.chatOpen.value = true
    act.chatMsgs.clear()
    act.showTab(2)
}

// Firebase helpers

internal suspend fun awaitOnce(ref: com.google.firebase.database.DatabaseReference): DataSnapshot = suspendCancellableCoroutine { cont ->
    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { if (cont.isActive) cont.resume(s) }
        override fun onCancelled(e: DatabaseError) { if (cont.isActive) cont.resumeWith(Result.failure(e.toException())) }
    })
}

internal fun readOnce(ref: com.google.firebase.database.DatabaseReference, act: MainActivity, retries: Int, onOk: (DataSnapshot) -> Unit, onErr: (Throwable) -> Unit) {
    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { onOk(s) }
        override fun onCancelled(e: DatabaseError) {
            if (retries > 0) readOnce(ref, act, retries - 1, onOk, onErr) else onErr(e.toException())
        }
    })
}

internal fun readStatsInto(act: MainActivity, onDone: () -> Unit) {
    act.database.child("postStats").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            act.statsMap.clear()
            for (c in s.children) c.key?.let { act.statsMap[it] = c }
            act.statsVer.value++
            onDone()
        }
        override fun onCancelled(e: DatabaseError) { onDone() }
    })
}

internal fun txWithRetry(ref: com.google.firebase.database.DatabaseReference, act: MainActivity, retries: Int, tx: (MutableData) -> Transaction.Result, onDone: (Boolean, DataSnapshot?, DatabaseError?) -> Unit) {
    ref.runTransaction(object : com.google.firebase.database.Transaction.Handler {
        override fun doTransaction(data: MutableData): Transaction.Result = tx(data)
        override fun onComplete(e: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
            if (!committed && e != null && retries > 0) txWithRetry(ref, act, retries - 1, tx, onDone)
            else onDone(committed, data, e)
        }
    })
}

internal fun isNickTaken(act: MainActivity, nick: String, excludeUid: String, onDone: (Boolean) -> Unit) {
    act.database.child("users").orderByChild("nick").equalTo(nick).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            for (c in s.children) {
                val uid = c.key ?: continue
                if (uid != excludeUid) { onDone(true); return }
            }
            onDone(false)
        }
        override fun onCancelled(e: DatabaseError) { onDone(false) }
    })
}

// Image decode + cache

internal fun decodeScaled(b64: String, px: Int): Bitmap? {
    if (b64.isEmpty()) return null
    return try {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
        var s = 1
        while (opts.outWidth / (s * 2) > px || opts.outHeight / (s * 2) > px) s *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = s }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, opts2)
    } catch (t: Throwable) { null }
}

@Composable
internal fun rememberDecoded(b64: String, px: Int): Bitmap? {
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    val key = b64.hashCode().toString() + ":" + px
    var bmp by remember(key) { mutableStateOf<Bitmap?>(act?.bmpCache?.get(key)) }
    LaunchedEffect(key) {
        val cached = act?.bmpCache?.get(key)
        if (cached != null) { bmp = cached; return@LaunchedEffect }
        if (b64.isEmpty()) { bmp = null; return@LaunchedEffect }
        val d = withContext(Dispatchers.Default) { decodeScaled(b64, px) }
        if (d != null) act?.bmpCache?.put(key, d)
        bmp = d
    }
    return bmp
}

// LinkText with clickable URLs and @mentions

@Composable
internal fun LinkText(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val urlRe = Regex("https?://\\S+")
    val annotated = buildAnnotatedString {
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
    val ctx = LocalContext.current
    ClickableText(
        text = annotated,
        style = TextStyle(color = color, fontSize = fontSize),
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

// VideoScreen stub (tab 1 is empty now)
@Composable
internal fun VideoScreen() {
    Box(Modifier.background(ic(P.bg)).fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Text("Раздел видео пуст", color = ic(P.textSec), fontSize = 14.sp)
    }
}
