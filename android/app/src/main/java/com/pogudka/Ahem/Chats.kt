package com.pogudka.Ahem

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// детерминированный ключ пары: оба участника вычисляют одинаковый
internal fun chatPairKey(a: String, b: String): String =
    if (a <= b) "${a}_$b" else "${b}_$a"

private fun msgText(m: DataSnapshot): String = try {
    val known = arrayOf("text", "message", "body", "content", "msg", "m", "value", "val")
    var r = ""
    for (k in known) {
        val v = m.child(k).getValue(String::class.java)
        if (!v.isNullOrEmpty()) { r = v; break }
    }
    if (r.isEmpty()) {
        for (c in m.children) {
            val v = c.value
            if (v is String && v.isNotEmpty()) { r = v; break }
        }
    }
    if (r.isEmpty()) {
        val direct = m.value
        if (direct is String && direct.isNotEmpty()) r = direct
    }
    r
} catch (e: Exception) { "" }

private fun msgTs(m: DataSnapshot): Long = try {
    val known = arrayOf("ts", "timestamp", "time", "createdAt", "created", "date", "at", "sentAt")
    var r = 0L
    for (k in known) {
        val v = m.child(k).value
        if (v is Number) { r = v.toLong(); break }
        val s = (v as? String)?.toLongOrNull()
        if (s != null) { r = s; break }
    }
    r
} catch (e: Exception) { 0L }

private fun msgNick(m: DataSnapshot): String = try {
    val known = arrayOf("nick", "senderNick", "authorNick", "fromNick", "name", "user")
    var r = ""
    for (k in known) {
        val v = m.child(k).getValue(String::class.java)
        if (!v.isNullOrEmpty()) { r = v; break }
    }
    r
} catch (e: Exception) { "" }

// senderUid однозначен -> по нему; иначе ник; иначе фолбэк по источнику (inbox = входящие)
private fun msgMine(m: DataSnapshot, myUid: String, myNick: String, inboxFallback: Boolean): Boolean {
    return try {
        val uidKeys = arrayOf("senderUid", "from", "uid", "sender", "author", "userId", "owner", "by")
        var res: Boolean? = null
        for (k in uidKeys) {
            val v = m.child(k).getValue(String::class.java) ?: ""
            if (v.isNotEmpty()) { res = (v == myUid); break }
        }
        if (res == null && myNick.isNotEmpty()) {
            val n = msgNick(m)
            if (n.isNotEmpty()) res = (n == myNick)
        }
        res ?: inboxFallback
    } catch (e: Exception) { inboxFallback }
}

private fun peersKey() = "chat_peers_v5"

private fun loadPeers(act: MainActivity): LinkedHashMap<String, String> {
    val raw = act.getSharedPreferences("ahem", Context.MODE_PRIVATE).getString(peersKey(), "") ?: ""
    val map = LinkedHashMap<String, String>()
    if (raw.isNotEmpty()) {
        for (part in raw.split("\n")) {
            val i = part.indexOf('|')
            if (i > 0) map[part.substring(0, i)] = part.substring(i + 1)
        }
    }
    return map
}

private fun savePeer(act: MainActivity, uid: String, nick: String) {
    if (uid.isEmpty() || uid == act.currentUserId) return
    val map = loadPeers(act)
    map[uid] = nick.ifEmpty { "?" }
    val sb = StringBuilder()
    for ((k, v) in map) { if (sb.isNotEmpty()) sb.append("\n"); sb.append(k).append("|").append(v) }
    act.getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().putString(peersKey(), sb.toString()).apply()
}

internal fun buildChats(act: MainActivity): View {
    val tab = FrameLayout(act)
    tab.setBackgroundColor(P.bg)

    act.chatListRoot = LinearLayout(act)
    act.chatListRoot.orientation = LinearLayout.VERTICAL
    act.chatListRoot.setBackgroundColor(P.bg)

    val head = LinearLayout(act)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    head.setPadding(act.dp(18), act.dp(14), act.dp(18), act.dp(10))
    val title = tv(act, "Сообщения", 22f, act.fBold, P.text)
    title.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(title, 1f)
    head.addView(title)
    act.chatListRoot.addView(head)

    val searchRow = LinearLayout(act)
    searchRow.orientation = LinearLayout.HORIZONTAL
    searchRow.gravity = Gravity.CENTER_VERTICAL
    val srM = LinearLayout.LayoutParams(-1, -2)
    srM.setMargins(act.dp(14), 0, act.dp(14), act.dp(10))
    searchRow.layoutParams = srM
    act.searchField = field(act, "Поиск по @ник")
    act.searchField.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.searchField, 1f)
    searchRow.addView(act.searchField)
    act.chatListRoot.addView(searchRow)

    act.chatStatus = tv(act, "", 13f, act.fReg, P.textSec)
    val csM = LinearLayout.LayoutParams(-1, -2)
    csM.setMargins(act.dp(18), 0, act.dp(18), act.dp(6))
    act.chatStatus.layoutParams = csM
    act.chatListRoot.addView(act.chatStatus)

    val scroll = ScrollView(act)
    scroll.isVerticalScrollBarEnabled = false
    val slp = LinearLayout.LayoutParams(-1, 0)
    slp.weight = 1f
    scroll.layoutParams = slp
    act.chatListRows = LinearLayout(act)
    act.chatListRows.orientation = LinearLayout.VERTICAL
    act.chatListRows.setPadding(act.dp(14), 0, act.dp(14), 0)
    scroll.addView(act.chatListRows)
    act.chatListRoot.addView(scroll)

    act.searchResults = LinearLayout(act)
    act.searchResults.orientation = LinearLayout.VERTICAL
    act.searchResults.visibility = View.GONE
    act.chatListRoot.addView(act.searchResults)

    act.convRoot = LinearLayout(act)
    act.convRoot.orientation = LinearLayout.VERTICAL
    act.convRoot.setBackgroundColor(P.bg)
    act.convRoot.visibility = View.GONE

    val cbar = LinearLayout(act)
    cbar.orientation = LinearLayout.HORIZONTAL
    cbar.gravity = Gravity.CENTER_VERTICAL
    cbar.setPadding(act.dp(8), act.dp(12), act.dp(14), act.dp(10))
    val backIv = iconRound(act, R.drawable.ic_back, P.text, act.dp(38))
    backIv.setOnClickListener { closeConversation(act) }
    act.convTitle = tv(act, "", 18f, act.fBold, P.text)
    val tl = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    tl.weight = 1f
    tl.marginStart = act.dp(12)
    act.convTitle.layoutParams = tl
    cbar.addView(backIv)
    cbar.addView(act.convTitle)
    act.convRoot.addView(cbar)

    act.convScroll = ScrollView(act)
    act.convScroll.isVerticalScrollBarEnabled = false
    val cslp = LinearLayout.LayoutParams(-1, 0)
    cslp.weight = 1f
    act.convScroll.layoutParams = cslp
    act.convBox = LinearLayout(act)
    act.convBox.orientation = LinearLayout.VERTICAL
    act.convBox.setPadding(act.dp(14), act.dp(6), act.dp(14), act.dp(8))
    act.convScroll.addView(act.convBox)
    act.convRoot.addView(act.convScroll)

    val inputBar = LinearLayout(act)
    inputBar.orientation = LinearLayout.HORIZONTAL
    inputBar.gravity = Gravity.CENTER_VERTICAL
    val ibg = GlassDrawable()
    ibg.setLiveBlur(true)
    ibg.setRadius(act.dp(26).toFloat())
    ibg.setBlurRadius(act.dp(24).toFloat())
    ibg.setGlassTint(P.navGlass)
    ibg.setBorder(P.border, act.dp(1).toFloat())
    inputBar.background = ibg
    inputBar.setPadding(act.dp(8), act.dp(6), act.dp(8), act.dp(6))
    val ibLp = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
    ibLp.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
    inputBar.layoutParams = ibLp

    act.msgField = EditText(act)
    act.msgField.hint = "Сообщение..."
    act.msgField.setHintTextColor(P.textSec)
    act.msgField.setTextColor(P.text)
    act.msgField.typeface = act.fReg
    act.msgField.textSize = 15f
    act.msgField.background = null
    act.msgField.setPadding(act.dp(12), act.dp(10), act.dp(12), act.dp(10))
    act.msgField.maxLines = 4
    val mfLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    mfLp.weight = 1f
    act.msgField.layoutParams = mfLp
    inputBar.addView(act.msgField)

    val sendIv = iconRound(act, R.drawable.ic_send, P.text, act.dp(44))
    sendIv.setOnClickListener { sendMessage(act) }
    inputBar.addView(sendIv)
    act.convRoot.addView(inputBar)

    tab.addView(act.chatListRoot, FrameLayout.LayoutParams(-1, -1))
    tab.addView(act.convRoot, FrameLayout.LayoutParams(-1, -1))

    ibg.attach(act.window, act.contentFrame, inputBar)

    return tab
}

internal fun closeConversation(act: MainActivity) {
    try {
        val me = act.currentUserId
        val peer = act.currentChat
        act.convListener?.let { act.database.child("chats").child(chatPairKey(me, peer)).removeEventListener(it) }
        act.convListener2?.let { act.database.child("conversations").child(me).removeEventListener(it) }
    } catch (e: Exception) {}
    act.convListener = null
    act.convListener2 = null
    act.chatSnapChats = null
    act.chatSnapInbox = null
    act.currentChat = ""
    act.currentChatOtherUid = ""
    act.currentChatOtherNick = ""
    act.convRoot.visibility = View.GONE
    act.chatListRoot.visibility = View.VISIBLE
    act.setNavVisible(true)
    loadConversations(act)
}

internal fun openChat(act: MainActivity, peerUid: String, peerNick: String) {
    if (peerUid.isEmpty() || peerUid == act.currentUserId) return
    act.currentChat = peerUid
    act.currentChatOtherUid = peerUid
    act.currentChatOtherNick = peerNick
    act.convTitle.text = "@$peerNick"
    act.chatListRoot.visibility = View.GONE
    act.convRoot.visibility = View.VISIBLE
    act.setNavVisible(false)
    act.convBox.removeAllViews()
    act.chatSnapChats = null
    act.chatSnapInbox = null
    savePeer(act, peerUid, peerNick)

    try {
        val me = act.currentUserId
        act.convListener?.let { act.database.child("chats").child(chatPairKey(me, peerUid)).removeEventListener(it) }
        act.convListener2?.let { act.database.child("conversations").child(me).removeEventListener(it) }
    } catch (e: Exception) {}

    val me = act.currentUserId
    // 1) двусторонний узел пары — читают оба по правилам chats/$chatId
    val refChats = act.database.child("chats").child(chatPairKey(me, peerUid))
    // 2) свой inbox — читаю только я; там входящие + мои дубли
    val refInbox = act.database.child("conversations").child(me)

    val l1 = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { act.chatSnapChats = s; renderMergedChat(act, peerUid) }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    }
    val l2 = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { act.chatSnapInbox = s; renderMergedChat(act, peerUid) }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    }
    act.convListener = l1
    act.convListener2 = l2
    refChats.addValueEventListener(l1)
    refInbox.addValueEventListener(l2)
}

private fun renderMergedChat(act: MainActivity, peerUid: String) {
    val items = ArrayList<Pair<DataSnapshot, Boolean>>()
    val seen = HashSet<String>()
    // chats/<pair>: senderUid рулит, фолбэк = чужое (не знаем чьё без поля)
    act.chatSnapChats?.let { s ->
        for (c in s.children) {
            val key = msgTs(c).toString() + "|" + msgText(c)
            if (key in seen) continue
            seen.add(key)
            items.add(Pair(c, msgMine(c, act.currentUserId, act.currentNick, false)))
        }
    }
    // conversations/<me>: берём ТОЛЬКО подузлы peerUid (иначе примешаем других), фолбэк = моё (я туда дублирую свои)
    act.chatSnapInbox?.child(peerUid)?.let { s ->
        for (c in s.children) {
            val key = msgTs(c).toString() + "|" + msgText(c)
            if (key in seen) continue
            seen.add(key)
            items.add(Pair(c, msgMine(c, act.currentUserId, act.currentNick, true)))
        }
    }
    items.sortBy { msgTs(it.first) }
    renderConversation(act, items)
}

internal fun renderConversation(act: MainActivity, items: List<Pair<DataSnapshot, Boolean>>) {
    act.convBox.removeAllViews()
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    val maxW = (act.resources.displayMetrics.widthPixels * 0.78).toInt()

    val topSpacer = View(act)
    val tsp = LinearLayout.LayoutParams(-1, 0)
    tsp.weight = 1f
    topSpacer.layoutParams = tsp
    act.convBox.addView(topSpacer)

    var drawn = 0
    for ((m, mine) in items) {
        val text = msgText(m)
        if (text.isEmpty()) continue
        val ts = msgTs(m)
        drawn++
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val rl = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
        rl.bottomMargin = act.dp(6)
        row.layoutParams = rl
        if (mine) {
            val sp = View(act)
            val splp = LinearLayout.LayoutParams(0, 1)
            splp.weight = 1f
            row.addView(sp)
        }
        val bubble = LinearLayout(act)
        bubble.orientation = LinearLayout.VERTICAL
        bubble.background = if (mine) accentBg(act, 18) else surf(act, 18, filled = true)
        val blp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bubble.setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(8))
        val tvMsg = TextView(act)
        tvMsg.text = text
        tvMsg.textSize = 15f
        tvMsg.typeface = act.fReg
        tvMsg.setTextColor(if (mine) P.accentText else P.text)
        tvMsg.maxWidth = maxW
        bubble.addView(tvMsg)
        if (ts > 0) {
            val tvTime = TextView(act)
            tvTime.text = df.format(Date(ts))
            tvTime.textSize = 10f
            tvTime.typeface = act.fReg
            tvTime.gravity = if (mine) Gravity.END else Gravity.START
            tvTime.setTextColor(if (mine) 0x99FFFFFF.toInt() else P.textSec)
            bubble.addView(tvTime)
        }
        row.addView(bubble, blp)
        if (!mine) {
            val sp = View(act)
            val splp = LinearLayout.LayoutParams(0, 1)
            splp.weight = 1f
            row.addView(sp)
        }
        act.convBox.addView(row)
    }
    if (drawn == 0 && act.chatSnapChats != null && act.chatSnapInbox != null) {
        val empty = tv(act, "Нет сообщений", 14f, act.fMed, P.textSec)
        empty.gravity = Gravity.CENTER
        empty.setPadding(0, act.dp(20), 0, act.dp(20))
        act.convBox.addView(empty)
    }
    act.convScroll.post { act.convScroll.fullScroll(View.FOCUS_DOWN) }
}

internal fun sendMessage(act: MainActivity) {
    if (act.currentUserId.isEmpty() || act.currentChat.isEmpty()) return
    val text = act.msgField.text.toString().trim()
    if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    val me = act.currentUserId
    val peer = act.currentChat

    val payload = HashMap<String, Any>()
    payload["text"] = text
    payload["message"] = text
    payload["body"] = text
    payload["ts"] = now
    payload["timestamp"] = now
    payload["createdAt"] = now
    payload["senderUid"] = me
    payload["uid"] = me
    payload["from"] = me
    payload["nick"] = act.currentNick

    // 1) двусторонний узел пары (основной, читают оба)
    val rc = act.database.child("chats").child(chatPairKey(me, peer))
    val kc = rc.push().key
    if (kc != null) { val m = HashMap(payload); m["mine"] = true; rc.child(kc).setValue(m) }
    // 2) свой inbox (чтобы у себя отобразилось и в списке)
    val ri = act.database.child("conversations").child(me).child(peer)
    val ki = ri.push().key
    if (ki != null) { val m = HashMap(payload); m["mine"] = true; ri.child(ki).setValue(m) }
    // 3) inbox собеседника (для старой сборки, читает свой conversations/<self>)
    val rp = act.database.child("conversations").child(peer).child(me)
    val kp = rp.push().key
    if (kp != null) { val m = HashMap(payload); m["mine"] = false; rp.child(kp).setValue(m) }

    savePeer(act, peer, act.currentChatOtherNick)
    act.msgField.setText("")
}

internal fun loadConversations(act: MainActivity) {
    if (act.currentUserId.isEmpty()) {
        act.chatStatus.text = "не залогинен"
        return
    }
    act.chatStatus.text = "загрузка..."
    act.database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(uSnap: DataSnapshot) {
            val nickMap = HashMap<String, String>()
            val avatarMap = HashMap<String, String>()
            try {
                for (u in uSnap.children) {
                    val uid = u.key ?: continue
                    nickMap[uid] = u.child("nick").getValue(String::class.java) ?: "?"
                    avatarMap[uid] = u.child("avatar").getValue(String::class.java) ?: ""
                }
            } catch (e: Exception) {}
            // перечень = дети своего inbox (легально) UNION локальный реестр
            act.database.child("conversations").child(act.currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val peers = loadPeers(act)
                    try {
                        for (c in s.children) {
                            val uid = c.key ?: continue
                            if (uid == act.currentUserId) continue
                            if (!peers.containsKey(uid)) peers[uid] = "?"
                        }
                    } catch (e: Exception) {}
                    renderPeerRows(act, peers, nickMap, avatarMap, s)
                }
                override fun onCancelled(error: DatabaseError) {
                    renderPeerRows(act, loadPeers(act), nickMap, avatarMap, null)
                }
            })
        }
        override fun onCancelled(error: DatabaseError) {
            act.chatStatus.text = ""
            renderPeerRows(act, loadPeers(act), HashMap(), HashMap(), null)
        }
    })
}

private fun renderPeerRows(
    act: MainActivity,
    peers: LinkedHashMap<String, String>,
    nickMap: HashMap<String, String>,
    avatarMap: HashMap<String, String>,
    inboxSnap: DataSnapshot?
) {
    act.chatListRows.removeAllViews()
    act.searchResults.removeAllViews()
    if (peers.isEmpty()) {
        act.chatStatus.text = "Нет диалогов. Напиши кому-нибудь из ленты."
        return
    }
    act.chatStatus.text = ""
    // сортировка по последнему ts из inbox
    val lastTs = HashMap<String, Long>()
    inboxSnap?.let { s ->
        for (c in s.children) {
            val uid = c.key ?: continue
            var t = 0L
            for (m in c.children) { val ts = msgTs(m); if (ts > t) t = ts }
            lastTs[uid] = t
        }
    }
    val order = peers.keys.sortedByDescending { lastTs[it] ?: 0L }
    for (uid in order) {
        val fallbackNick = peers[uid] ?: "?"
        val nick = nickMap[uid]?.takeIf { it != "?" } ?: fallbackNick
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = surf(act, 16, filled = true)
        val rlp = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
        rlp.bottomMargin = act.dp(8)
        row.layoutParams = rlp
        row.setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12))
        val av = avatarView(act, null, act.dp(48), nick)
        val avB64 = avatarMap[uid] ?: ""
        if (avB64.isNotEmpty()) {
            val b = decodeScaled(avB64, act.dp(48))
            if (b != null) av.setImageBitmap(makeCircularBitmap(b, act.dp(48)))
        }
        row.addView(av)
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
        bp.weight = 1f
        bp.marginStart = act.dp(12)
        box.layoutParams = bp
        box.addView(tv(act, "@$nick", 15f, act.fSemi, P.text))
        val prev = tv(act, "", 13f, act.fReg, P.textSec)
        box.addView(prev)
        row.addView(box)
        // превью из уже загруженного inbox-снимка (без доп. запросов)
        var lastText = ""
        var lt = 0L
        inboxSnap?.child(uid)?.let { c ->
            for (m in c.children) {
                val t = msgText(m); val ts = msgTs(m)
                if (t.isNotEmpty() && ts >= lt) { lt = ts; lastText = t }
            }
        }
        prev.text = lastText
        row.setOnClickListener { openChat(act, uid, nick) }
        act.chatListRows.addView(row)
    }
}
