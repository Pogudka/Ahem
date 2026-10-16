package com.pogudka.Ahem

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

private fun msgText(m: DataSnapshot): String {
    val known = arrayOf("text", "message", "body", "content", "msg", "m", "value", "val")
    for (k in known) {
        val v = m.child(k).getValue(String::class.java)
        if (!v.isNullOrEmpty()) return v
    }
    for (c in m.children) {
        val v = c.value
        if (v is String && v.isNotEmpty()) return v
    }
    val direct = m.value
    if (direct is String && direct.isNotEmpty()) return direct
    return ""
}

private fun msgTs(m: DataSnapshot): Long {
    val known = arrayOf("ts", "timestamp", "time", "createdAt", "created", "date", "at", "sentAt")
    for (k in known) {
        val v = m.child(k).value
        if (v is Number) return v.toLong()
        val s = (v as? String)?.toLongOrNull()
        if (s != null) return s
    }
    for (c in m.children) {
        val v = c.value
        if (v is Number && v.toLong() > 1_000_000_000_000L) return v.toLong()
        val s = (v as? String)?.toLongOrNull()
        if (s != null && s > 1_000_000_000_000L) return s
    }
    return 0L
}

private fun msgNick(m: DataSnapshot): String {
    val known = arrayOf("nick", "senderNick", "authorNick", "fromNick", "name", "user")
    for (k in known) {
        val v = m.child(k).getValue(String::class.java)
        if (!v.isNullOrEmpty()) return v
    }
    return ""
}

private fun msgMine(m: DataSnapshot, myUid: String, boxIsMine: Boolean): Boolean {
    val uidKeys = arrayOf("senderUid", "from", "uid", "sender", "author", "userId", "owner", "by")
    for (k in uidKeys) {
        val v = m.child(k).getValue(String::class.java) ?: ""
        if (v.isNotEmpty()) return v == myUid
    }
    val boolKeys = arrayOf("mine", "isMine", "self", "own")
    for (k in boolKeys) {
        val v = m.child(k).getValue(Boolean::class.java)
        if (v != null) return v
    }
    return boxIsMine
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
    ibg.setBlurRadius(act.dp(18).toFloat())
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

    ibg.attach(act.contentFrame, inputBar)

    return tab
}

internal fun closeConversation(act: MainActivity) {
    act.convListener?.let {
        act.database.child("conversations").child(act.currentUserId).child(act.currentChat)
            .removeEventListener(it)
    }
    act.convListener2?.let {
        act.database.child("conversations").child(act.currentChat).child(act.currentUserId)
            .removeEventListener(it)
    }
    act.convLegacyListeners?.forEach { lv ->
        // снять нельзя без ссылки на ref; храним пары ref+listener ниже
    }
    act.convLegacyRefs?.let { refs ->
        act.convLegacyListeners?.forEachIndexed { i, lv -> refs.getOrNull(i)?.removeEventListener(lv) }
    }
    act.convListener = null
    act.convListener2 = null
    act.convLegacyListeners = null
    act.convLegacyRefs = null
    act.chatSnapMine = null
    act.chatSnapPeer = null
    act.chatLegacySnaps = HashMap()
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
    act.chatSnapMine = null
    act.chatSnapPeer = null
    act.chatLegacySnaps = HashMap()

    act.convListener?.let {
        act.database.child("conversations").child(act.currentUserId).child(peerUid).removeEventListener(it)
    }
    act.convListener2?.let {
        act.database.child("conversations").child(peerUid).child(act.currentUserId).removeEventListener(it)
    }

    val boxMine = act.database.child("conversations").child(act.currentUserId).child(peerUid)
    val boxPeer = act.database.child("conversations").child(peerUid).child(act.currentUserId)

    val l1 = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { act.chatSnapMine = s; renderMergedChat(act) }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    }
    val l2 = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { act.chatSnapPeer = s; renderMergedChat(act) }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    }
    act.convListener = l1
    act.convListener2 = l2
    boxMine.addValueEventListener(l1)
    boxPeer.addValueEventListener(l2)

    // фолбэк: плоский общий узел пары (старая схема). пробуем разные разделители/порядок.
    val me = act.currentUserId
    val seps = listOf("_", "", "-", ".", ":")
    val keys = LinkedHashSet<String>()
    for (s in seps) { keys.add("$me$s$peerUid"); keys.add("$peerUid$s$me") }
    val legacyListeners = ArrayList<ValueEventListener>()
    val legacyRefs = ArrayList<com.google.firebase.database.DatabaseReference>()
    for (k in keys) {
        val ref = act.database.child("conversations").child(k)
        val lv = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (s.exists()) { act.chatLegacySnaps[k] = s; renderMergedChat(act) }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        legacyListeners.add(lv); legacyRefs.add(ref)
        ref.addListenerForSingleValueEvent(lv)
    }
    act.convLegacyListeners = legacyListeners
    act.convLegacyRefs = legacyRefs
}

private fun renderMergedChat(act: MainActivity) {
    val items = ArrayList<Pair<DataSnapshot, Boolean>>()
    val seen = HashSet<String>()
    fun add(s: DataSnapshot?, boxIsMine: Boolean) {
        if (s == null) return
        for (c in s.children) {
            val key = msgTs(c).toString() + "|" + msgText(c)
            if (key in seen) continue
            seen.add(key)
            items.add(Pair(c, msgMine(c, act.currentUserId, boxIsMine)))
        }
    }
    add(act.chatSnapMine, true)
    add(act.chatSnapPeer, false)
    if (items.isEmpty() && act.chatSnapMine != null && act.chatSnapPeer != null) {
        for ((_, s) in act.chatLegacySnaps) add(s, false)
    }
    items.sortBy { msgTs(it.first) }
    renderConversation(act, items)
}

internal fun renderConversation(act: MainActivity, items: List<Pair<DataSnapshot, Boolean>>) {
    act.convBox.removeAllViews()
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    val maxW = (act.resources.displayMetrics.widthPixels * 0.78).toInt()
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
    if (drawn == 0 && act.chatSnapMine != null && act.chatSnapPeer != null) {
        val empty = tv(act, "Нет сообщений", 14f, act.fMed, P.textSec)
        empty.gravity = Gravity.CENTER
        empty.setPadding(0, act.dp(40), 0, act.dp(40))
        act.convBox.addView(empty)
    }
    act.convScroll.post { act.convScroll.fullScroll(View.FOCUS_DOWN) }
}

internal fun sendMessage(act: MainActivity) {
    if (act.currentUserId.isEmpty() || act.currentChat.isEmpty()) return
    val text = act.msgField.text.toString().trim()
    if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    val peer = act.currentChat
    val base = act.database.child("conversations")
    val key1 = base.child(act.currentUserId).child(peer).push().key ?: return
    val m1 = HashMap<String, Any>()
    m1["text"] = text; m1["message"] = text; m1["body"] = text
    m1["ts"] = now; m1["timestamp"] = now; m1["createdAt"] = now
    m1["mine"] = true; m1["senderUid"] = act.currentUserId; m1["uid"] = act.currentUserId
    m1["from"] = act.currentUserId; m1["nick"] = act.currentNick
    base.child(act.currentUserId).child(peer).child(key1).setValue(m1)
    val key2 = base.child(peer).child(act.currentUserId).push().key ?: return
    val m2 = HashMap<String, Any>()
    m2["text"] = text; m2["message"] = text; m2["body"] = text
    m2["ts"] = now; m2["timestamp"] = now; m2["createdAt"] = now
    m2["mine"] = false; m2["senderUid"] = act.currentUserId; m2["uid"] = act.currentUserId
    m2["from"] = act.currentUserId; m2["nick"] = act.currentNick
    base.child(peer).child(act.currentUserId).child(key2).setValue(m2)
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
            for (u in uSnap.children) {
                val uid = u.key ?: continue
                nickMap[uid] = u.child("nick").getValue(String::class.java) ?: "?"
                avatarMap[uid] = u.child("avatar").getValue(String::class.java) ?: ""
            }
            loadConvRows(act, nickMap, avatarMap)
        }
        override fun onCancelled(error: DatabaseError) {
            loadConvRows(act, HashMap(), HashMap())
        }
    })
}

private fun loadConvRows(act: MainActivity, nickMap: HashMap<String, String>, avatarMap: HashMap<String, String>) {
    act.database.child("conversations").child(act.currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                act.chatListRows.removeAllViews()
                act.searchResults.removeAllViews()
                val uids = ArrayList<String>()
                val previews = HashMap<String, String>()
                val times = HashMap<String, Long>()
                val msgNicks = HashMap<String, String>()
                for (p in snapshot.children) {
                    val uid = p.key ?: continue
                    if (uid == act.currentUserId) continue
                    var lastText = ""
                    var lastTs = 0L
                    var lastNick = ""
                    for (m in p.children) {
                        val t = msgText(m)
                        val ts = msgTs(m)
                        if (t.isNotEmpty() && ts >= lastTs) { lastTs = ts; lastText = t; lastNick = msgNick(m) }
                    }
                    uids.add(uid)
                    previews[uid] = lastText
                    times[uid] = lastTs
                    if (lastNick.isNotEmpty()) msgNicks[uid] = lastNick
                }
                uids.sortByDescending { times[it] ?: 0L }
                if (uids.isEmpty()) {
                    act.chatStatus.text = "Нет диалогов. Найди кого-нибудь через поиск в ленте."
                } else {
                    act.chatStatus.text = ""
                }
                for (uid in uids) {
                    val nick = nickMap[uid]?.takeIf { it != "?" } ?: msgNicks[uid] ?: "?"
                    val preview = previews[uid] ?: ""
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
                    box.addView(tv(act, preview, 13f, act.fReg, P.textSec))
                    row.addView(box)
                    row.setOnClickListener { openChat(act, uid, nick) }
                    act.chatListRows.addView(row)
                }
            } catch (e: Exception) {
            }
        }
        override fun onCancelled(error: DatabaseError) {
            act.chatStatus.text = "ошибка загрузки"
            dbDenied(act, error)
        }
    })
}
