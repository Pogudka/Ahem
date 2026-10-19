package com.pogudka.Ahem

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Msg(val text: String, val ts: Long, val mine: Boolean)
internal data class PeerRow(val uid: String, val nick: String, val preview: String, val avatarB64: String)

internal fun chatPairKey(a: String, b: String): String = if (a <= b) "${a}_$b" else "${b}_$a"

private fun msgText(m: DataSnapshot): String = try {
    var r = ""
    for (k in arrayOf("text","message","body","content","msg","m","value","val")) { val v = m.child(k).getValue(String::class.java); if (!v.isNullOrEmpty()) { r = v; break } }
    if (r.isEmpty()) for (c in m.children) { val v = c.value; if (v is String && v.isNotEmpty()) { r = v; break } }
    if (r.isEmpty()) { val d = m.value; if (d is String && d.isNotEmpty()) r = d }
    r
} catch (e: Exception) { "" }

private fun msgTs(m: DataSnapshot): Long = try {
    var r = 0L
    for (k in arrayOf("ts","timestamp","time","createdAt","created","date","at","sentAt")) { val v = m.child(k).value; if (v is Number) { r = v.toLong(); break }; val s = (v as? String)?.toLongOrNull(); if (s != null) { r = s; break } }
    r
} catch (e: Exception) { 0L }

private fun msgNick(m: DataSnapshot): String = try {
    var r = ""
    for (k in arrayOf("nick","senderNick","authorNick","fromNick","name","user")) { val v = m.child(k).getValue(String::class.java); if (!v.isNullOrEmpty()) { r = v; break } }
    r
} catch (e: Exception) { "" }

private fun msgMine(m: DataSnapshot, myUid: String, myNick: String, def: Boolean): Boolean = try {
    var res: Boolean? = null
    for (k in arrayOf("senderUid","from","uid","sender","author","userId","owner","by")) { val v = m.child(k).getValue(String::class.java) ?: ""; if (v.isNotEmpty()) { res = (v == myUid); break } }
    if (res == null && myNick.isNotEmpty()) { val n = msgNick(m); if (n.isNotEmpty()) res = (n == myNick) }
    if (res == null) { val v = m.child("mine").getValue(Boolean::class.java); if (v != null) res = v }
    res ?: def
} catch (e: Exception) { def }

private fun peersKey(act: MainActivity) = "chat_peers_" + act.currentUserId
private fun loadPeers(act: MainActivity): LinkedHashMap<String, String> {
    val raw = act.getSharedPreferences("ahem", Context.MODE_PRIVATE).getString(peersKey(act), "") ?: ""
    val map = LinkedHashMap<String, String>()
    if (raw.isNotEmpty()) for (p in raw.split("\n")) { val i = p.indexOf('|'); if (i > 0) map[p.substring(0, i)] = p.substring(i + 1) }
    return map
}
private fun savePeer(act: MainActivity, uid: String, nick: String) {
    if (uid.isEmpty() || uid == act.currentUserId) return
    val map = loadPeers(act); map[uid] = nick.ifEmpty { "?" }
    val sb = StringBuilder(); for ((k, v) in map) { if (sb.isNotEmpty()) sb.append("\n"); sb.append(k).append("|").append(v) }
    act.getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().putString(peersKey(act), sb.toString()).apply()
}

internal fun openChat(act: MainActivity, peerUid: String, peerNick: String) {
    if (peerUid.isEmpty() || peerUid == act.currentUserId) return
    savePeer(act, peerUid, peerNick)
    act.chatPeerUid = peerUid; act.chatPeerNick = peerNick; act.chatMsgs.clear(); act.chatOpen.value = true; act.tab.value = 2
}
internal fun closeConversation(act: MainActivity) { act.chatOpen.value = false; act.chatPeerUid = ""; act.chatPeerNick = ""; loadConversations(act) }

@Composable
internal fun ChatScreen(act: MainActivity) {
    val open by act.chatOpen
    if (open) Conversation(act, act.chatPeerUid, act.chatPeerNick) else ConversationList(act)
}

@Composable
private fun ConversationList(act: MainActivity) {
    val me by act.sessionUid
    LaunchedEffect(me) { loadConversations(act) }
    val rows = act.peersList.toList()
    val status by act.chatStatus
    Column(Modifier.fillMaxSize().background(ic(P.bg))) {
        Row(Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Сообщения", color = ic(P.text), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (status.isNotEmpty()) Text(status, color = ic(P.textSec), fontSize = 13.sp, modifier = Modifier.padding(18.dp, 0.dp, 18.dp, 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { row ->
                Row(Modifier.fillMaxWidth().background(ic(P.surface), RoundedCornerShape(16.dp)).border(1.dp, ic(P.border), RoundedCornerShape(16.dp)).clickable { openChat(act, row.uid, row.nick) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(row.avatarB64, row.nick, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${row.nick}", color = ic(P.text), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(row.preview, color = ic(P.textSec), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Conversation(act: MainActivity, peerUid: String, peerNick: String) {
    val listState = rememberLazyListState()
    DisposableEffect(peerUid, act.currentUserId) {
        val me = act.currentUserId
        val l1 = object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { mergeMsgs(act.chatMsgs, s, act, false) }; override fun onCancelled(e: DatabaseError) {} }
        val l2 = object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { mergeMsgs(act.chatMsgs, s, act, true) }; override fun onCancelled(e: DatabaseError) {} }
        val l3 = object : ValueEventListener { override fun onDataChange(s: DataSnapshot) { mergeMsgs(act.chatMsgs, s, act, false) }; override fun onCancelled(e: DatabaseError) {} }
        act.database.child("conversations").child(me).child(peerUid).addValueEventListener(l1)
        act.database.child("conversations").child(peerUid).child(me).addValueEventListener(l2)
        act.database.child("conversations").child(peerUid).addValueEventListener(l3)
        onDispose {
            try {
                act.database.child("conversations").child(me).child(peerUid).removeEventListener(l1)
                act.database.child("conversations").child(peerUid).child(me).removeEventListener(l2)
                act.database.child("conversations").child(peerUid).removeEventListener(l3)
            } catch (e: Exception) {}
        }
    }
    val msgs = act.chatMsgs.toList()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
    val input = remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(ic(P.bg))) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ic_back) { closeConversation(act) }
            Spacer(Modifier.width(12.dp))
            Text("@$peerNick", color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 8.dp), verticalArrangement = Arrangement.Bottom) {
            items(msgs) { m -> Bubble(m) }
        }
        Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp).background(ic(P.surface), RoundedCornerShape(26.dp)).border(1.dp, ic(P.border), RoundedCornerShape(26.dp)).padding(8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(12.dp, 10.dp)) {
                BasicTextField(value = input.value, onValueChange = { input.value = it }, textStyle = TextStyle(color = ic(P.text), fontSize = 15.sp), modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (input.value.isEmpty()) Text("Сообщение...", color = ic(P.textSec), fontSize = 15.sp); inner() })
            }
            Box(Modifier.clickable { sendMsg(act, peerUid, input.value); input.value = "" }, contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_send), contentDescription = null, tint = ic(P.text), modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val df = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.background(if (m.mine) ic(P.accent) else ic(P.surface), RoundedCornerShape(18.dp)).border(1.dp, if (m.mine) Color.Transparent else ic(P.border), RoundedCornerShape(18.dp)).padding(14.dp, 10.dp, 14.dp, 8.dp)) {
            Text(m.text, color = if (m.mine) ic(P.accentText) else ic(P.text), fontSize = 15.sp)
            if (m.ts > 0) Text(df.format(Date(m.ts)), color = if (m.mine) Color(1f,1f,1f,0.6f) else ic(P.textSec), fontSize = 10.sp, modifier = Modifier.align(if (m.mine) Alignment.End else Alignment.Start))
        }
    }
}

private fun mergeMsgs(target: MutableList<Msg>, s: DataSnapshot, act: MainActivity, def: Boolean) {
    val existing = HashSet<String>(); for (m in target) existing.add(m.ts.toString() + "|" + m.text)
    var changed = false
    for (c in s.children) {
        val t = msgText(c); if (t.isEmpty()) continue
        val key = msgTs(c).toString() + "|" + t
        if (existing.add(key)) { target.add(Msg(t, msgTs(c), msgMine(c, act.currentUserId, act.currentNick, def))); changed = true }
    }
    if (changed) { val sorted = target.sortedBy { it.ts }; target.clear(); target.addAll(sorted) }
}

internal fun sendMsg(act: MainActivity, peer: String, raw: String) {
    val me = act.currentUserId
    if (me.isEmpty() || peer.isEmpty() || peer == me) return
    val text = raw.trim(); if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    val payload = HashMap<String, Any>()
    payload["text"] = text; payload["message"] = text; payload["body"] = text; payload["ts"] = now; payload["timestamp"] = now; payload["createdAt"] = now; payload["senderUid"] = me; payload["uid"] = me; payload["from"] = me; payload["nick"] = act.currentNick
    val ri = act.database.child("conversations").child(me).child(peer); val ki = ri.push().key; if (ki != null) { val m = HashMap(payload); m["mine"] = true; ri.child(ki).setValue(m) }
    val rp = act.database.child("conversations").child(peer).child(me); val kp = rp.push().key; if (kp != null) { val m = HashMap(payload); m["mine"] = false; rp.child(kp).setValue(m) }
    val rf = act.database.child("conversations").child(peer); val kf = rf.push().key; if (kf != null) { val m = HashMap(payload); m["mine"] = true; rf.child(kf).setValue(m) }
    savePeer(act, peer, act.chatPeerNick)
}

internal fun loadConversations(act: MainActivity) {
    val me = act.currentUserId
    if (me.isEmpty()) { act.chatStatus.value = "не залогинен"; act.peersList.clear(); return }
    act.chatStatus.value = "загрузка..."
    act.database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(uSnap: DataSnapshot) {
            val nickMap = HashMap<String, String>(); val avatarMap = HashMap<String, String>()
            try { for (u in uSnap.children) { val uid = u.key ?: continue; nickMap[uid] = u.child("nick").getValue(String::class.java) ?: "?"; avatarMap[uid] = u.child("avatar").getValue(String::class.java) ?: "" } } catch (e: Exception) {}
            act.database.child("conversations").child(me).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val peers = loadPeers(act)
                    try { for (c in s.children) { val uid = c.key ?: continue; if (uid == me) continue; if (!peers.containsKey(uid)) peers[uid] = "?" } } catch (e: Exception) {}
                    val lastTs = HashMap<String, Long>(); val lastTxt = HashMap<String, String>()
                    try { for (c in s.children) { val uid = c.key ?: continue; if (uid == me) continue; var t = 0L; var txt = ""; for (m in c.children) { val ts = msgTs(m); val tx = msgText(m); if (tx.isNotEmpty() && ts >= t) { t = ts; txt = tx } }; lastTs[uid] = t; lastTxt[uid] = txt } } catch (e: Exception) {}
                    act.peersList.clear()
                    for (uid in peers.keys.sortedByDescending { lastTs[it] ?: 0L }) {
                        if (uid == me) continue
                        val nick = nickMap[uid]?.takeIf { it != "?" } ?: (peers[uid] ?: "?")
                        act.peersList.add(PeerRow(uid, nick, lastTxt[uid] ?: "", avatarMap[uid] ?: ""))
                    }
                    act.chatStatus.value = if (act.peersList.isEmpty()) "Нет диалогов. Напиши кому-нибудь из ленты." else ""
                }
                override fun onCancelled(e: DatabaseError) { act.chatStatus.value = "" }
            })
        }
        override fun onCancelled(e: DatabaseError) { act.chatStatus.value = "" }
    })
}
