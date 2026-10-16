package com.pogudka.Ahem

import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    ibg.setRadius(act.dp(26).toFloat())
    ibg.setBlurRadius(act.dp(22).toFloat())
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

    ibg.attach(act.rootFrame, inputBar)

    return tab
}

internal fun closeConversation(act: MainActivity) {
    act.convListener?.let {
        act.database.child("conversations").child(act.currentUserId).child(act.currentChat)
            .removeEventListener(it)
    }
    act.convListener = null
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
    act.convListener?.let {
        act.database.child("conversations").child(act.currentUserId).child(act.currentChat)
            .removeEventListener(it)
    }
    act.convListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            renderConversation(act, snapshot)
        }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    }
    act.database.child("conversations").child(act.currentUserId).child(peerUid)
        .addValueEventListener(act.convListener!!)
}

internal fun renderConversation(act: MainActivity, snapshot: DataSnapshot) {
    act.convBox.removeAllViews()
    val list = ArrayList<DataSnapshot>()
    for (c in snapshot.children) list.add(c)
    list.sortBy { getTs(it) }
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    for (m in list) {
        val text = m.child("text").getValue(String::class.java) ?: ""
        val ts = getTs(m)
        val mine = m.child("mine").getValue(Boolean::class.java) ?: false
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
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        blp.width = 0
        blp.weight = 0f
        val maxW = (act.resources.displayMetrics.widthPixels * 0.78).toInt()
        bubble.maximumWidth = maxW
        bubble.setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(8))
        val tvMsg = TextView(act)
        tvMsg.text = text
        tvMsg.textSize = 15f
        tvMsg.typeface = act.fReg
        tvMsg.setTextColor(if (mine) P.accentText else P.text)
        bubble.addView(tvMsg)
        val tvTime = TextView(act)
        tvTime.text = df.format(Date(ts))
        tvTime.textSize = 10f
        tvTime.typeface = act.fReg
        tvTime.gravity = if (mine) Gravity.END else Gravity.START
        tvTime.setTextColor(if (mine) 0x99FFFFFF else P.textSec)
        bubble.addView(tvTime)
        val wrapLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(bubble, wrapLp)
        if (!mine) {
            val sp = View(act)
            val splp = LinearLayout.LayoutParams(0, 1)
            splp.weight = 1f
            row.addView(sp)
        }
        act.convBox.addView(row)
    }
    act.convScroll.post { act.convScroll.fullScroll(View.FOCUS_DOWN) }
}

internal fun sendMessage(act: MainActivity) {
    if (act.currentUserId.isEmpty() || act.currentChat.isEmpty()) return
    val text = act.msgField.text.toString().trim()
    if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    val key1 = act.database.child("conversations").child(act.currentUserId).child(act.currentChat).push().key ?: return
    val m1 = HashMap<String, Any>()
    m1["text"] = text
    m1["ts"] = now
    m1["mine"] = true
    m1["nick"] = act.currentNick
    act.database.child("conversations").child(act.currentUserId).child(act.currentChat).child(key1).setValue(m1)
    val key2 = act.database.child("conversations").child(act.currentChat).child(act.currentUserId).push().key ?: return
    val m2 = HashMap<String, Any>()
    m2["text"] = text
    m2["ts"] = now
    m2["mine"] = false
    m2["nick"] = act.currentNick
    act.database.child("conversations").child(act.currentChat).child(act.currentUserId).child(key2).setValue(m2)
    act.msgField.setText("")
}

internal fun loadConversations(act: MainActivity) {
    if (act.currentUserId.isEmpty()) {
        act.chatStatus.text = "не залогинен"
        return
    }
    act.chatStatus.text = "загрузка..."
    act.database.child("conversations").child(act.currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                act.chatListRows.removeAllViews()
                act.searchResults.removeAllViews()
                val peers = HashMap<String, Pair<String, Long>>()
                for (p in snapshot.children) {
                    val uid = p.key ?: continue
                    var lastText = ""
                    var lastTs = 0L
                    var nick = "?"
                    for (m in p.children) {
                        val t = m.child("text").getValue(String::class.java) ?: ""
                        val ts = getTs(m)
                        val n = m.child("nick").getValue(String::class.java) ?: "?"
                        if (ts >= lastTs) { lastTs = ts; lastText = t; nick = n }
                    }
                    peers[uid] = Pair(nick, lastTs)
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.background = surf(act, 16, filled = true)
                    val rl = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rl.bottomMargin = act.dp(8)
                    row.layoutParams = rl
                    row.setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12))
                    row.addView(avatarView(act, null, act.dp(48), nick))
                    val box = LinearLayout(act)
                    box.orientation = LinearLayout.VERTICAL
                    val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    bp.weight = 1f
                    bp.marginStart = act.dp(12)
                    box.layoutParams = bp
                    box.addView(tv(act, "@$nick", 15f, act.fSemi, P.text))
                    box.addView(tv(act, lastText, 13f, act.fReg, P.textSec))
                    row.addView(box)
                    row.setOnClickListener { openChat(act, uid, nick) }
                    act.chatListRows.addView(row)
                }
                act.chatStatus.text = if (peers.isEmpty()) "Нет диалогов" else ""
            } catch (e: Exception) {
            }
        }
        override fun onCancelled(error: DatabaseError) {
            act.chatStatus.text = "ошибка загрузки"
            dbDenied(act, error)
        }
    })
}
