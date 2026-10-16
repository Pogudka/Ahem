package com.pogudka.Ahem

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun updateConversation(act: MainActivity, ownerUid: String, otherUid: String, otherNick: String, lastText: String, ts: Long) {
    if (ownerUid.isEmpty() || otherUid.isEmpty()) return
    val node = HashMap<String, Any>()
    node["nick"] = otherNick
    node["last"] = lastText
    node["ts"] = ts
    act.database.child("conversations").child(ownerUid).child(otherUid).setValue(node)
}

internal fun loadConversations(act: MainActivity) {
    if (act.currentUserId.isEmpty()) return
    act.chatListRows.removeAllViews()
    act.convListener?.let { act.database.child("conversations").child(act.currentUserId).removeEventListener(it) }
    act.convListener = act.database.child("conversations").child(act.currentUserId)
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    act.chatListRows.removeAllViews()
                    val list = ArrayList<DataSnapshot>()
                    for (c in snapshot.children) list.add(c)
                    list.sortByDescending { (it.child("ts").value as? Number)?.toLong() ?: 0L }
                    if (list.isEmpty()) {
                        act.chatListRows.addView(tv(act, "Нет диалогов. Найди человека через поиск.", 14f, act.fMed, P.textSec))
                    }
                    for (c in list) {
                        val otherUid = c.key ?: ""
                        val nick = c.child("nick").getValue(String::class.java) ?: "?"
                        val last = c.child("last").getValue(String::class.java) ?: ""
                        val ts = (c.child("ts").value as? Number)?.toLong() ?: 0L
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                        val row = LinearLayout(act)
                        row.orientation = LinearLayout.HORIZONTAL
                        row.gravity = Gravity.CENTER_VERTICAL
                        row.background = surf(act, 16, filled = true)
                        row.layoutParams = act.lp(-1, -2, act.dp(8))
                        row.setPadding(act.dp(12), act.dp(10), act.dp(14), act.dp(10))
                        row.addView(avatarView(act, null, act.dp(44), nick))
                        val col = LinearLayout(act)
                        col.orientation = LinearLayout.VERTICAL
                        val clp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                        col.layoutParams = clp
                        act.wt(col, 1f)
                        clp.marginStart = act.dp(12)
                        col.addView(tv(act, nick, 15f, act.fSemi, P.text))
                        val lastTv = tv(act, if (last.isEmpty()) "…" else last, 13f, act.fReg, P.textSec)
                        lastTv.maxLines = 1
                        col.addView(lastTv)
                        row.addView(col)
                        row.addView(tv(act, timeStr, 11f, act.fReg, P.textSec))
                        row.setOnClickListener { openChat(act, otherUid, nick) }
                        act.chatListRows.addView(row)
                    }
                } catch (e: Exception) {
                }
            }
            override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
        })
}

internal fun buildChats(act: MainActivity): View {
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL
    col.setBackgroundColor(P.bg)
    col.setPadding(act.dp(18), act.dp(14), act.dp(18), 0)

    act.chatListRoot = LinearLayout(act)
    act.chatListRoot.orientation = LinearLayout.VERTICAL
    act.chatListRoot.layoutParams = act.lp(-1, 0, 0)
    act.wt(act.chatListRoot, 1f)
    val head = tv(act, "Сообщения", 24f, act.fBold, P.text)
    head.layoutParams = act.lp(-1, -2, act.dp(6))
    act.chatStatus = tv(act, "подключено", 12f, act.fReg, P.textSec)
    act.chatStatus.layoutParams = act.lp(-1, -2, act.dp(14))
    val rowSearch = LinearLayout(act)
    rowSearch.orientation = LinearLayout.HORIZONTAL
    rowSearch.layoutParams = act.lp(-1, -2, act.dp(14))
    act.searchField = field(act, "Поиск по нику")
    act.searchField.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.searchField, 2f)
    val searchIv = iconRound(act, R.drawable.ic_search, P.text, act.dp(46))
    val siLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    siLp.weight = 1f
    siLp.marginStart = act.dp(8)
    searchIv.layoutParams = siLp
    rowSearch.addView(act.searchField)
    rowSearch.addView(searchIv)
    act.searchResults = LinearLayout(act)
    act.searchResults.orientation = LinearLayout.VERTICAL
    act.chatListRows = LinearLayout(act)
    act.chatListRows.orientation = LinearLayout.VERTICAL
    val listScroll = ScrollView(act)
    listScroll.addView(act.chatListRows)
    listScroll.layoutParams = act.lp(-1, 0, 0)
    act.wt(listScroll, 1f)
    listScroll.isVerticalScrollBarEnabled = false
    act.chatListRoot.addView(head)
    act.chatListRoot.addView(act.chatStatus)
    act.chatListRoot.addView(rowSearch)
    act.chatListRoot.addView(act.searchResults)
    act.chatListRoot.addView(listScroll)

    act.convRoot = LinearLayout(act)
    act.convRoot.orientation = LinearLayout.VERTICAL
    act.convRoot.visibility = View.GONE
    act.convRoot.setBackgroundColor(P.bg)
    act.convRoot.layoutParams = act.lp(-1, 0, 0)
    act.wt(act.convRoot, 1f)
    val top = LinearLayout(act)
    top.orientation = LinearLayout.HORIZONTAL
    top.gravity = Gravity.CENTER_VERTICAL
    top.setPadding(act.dp(4), act.dp(4), act.dp(4), act.dp(10))
    top.layoutParams = act.lp(-1, -2, 0)
    val backIv = iconRound(act, R.drawable.ic_back, P.text, act.dp(38))
    act.convTitle = tv(act, "", 17f, act.fSemi, P.text)
    val ctLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    ctLp.weight = 1f
    ctLp.marginStart = act.dp(12)
    act.convTitle.layoutParams = ctLp
    top.addView(backIv)
    top.addView(act.convTitle)
    act.convBox = LinearLayout(act)
    act.convBox.orientation = LinearLayout.VERTICAL
    act.convScroll = ScrollView(act)
    act.convScroll.addView(act.convBox)
    act.convScroll.layoutParams = act.lp(-1, 0, 0)
    act.wt(act.convScroll, 1f)
    act.convScroll.isVerticalScrollBarEnabled = false
    act.msgField = field(act, "Сообщение...")
    val sendIv = ImageView(act)
    sendIv.setImageResource(R.drawable.ic_send)
    sendIv.setColorFilter(P.accentText)
    sendIv.setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12))
    sendIv.layoutParams = act.lp(act.dp(46), act.dp(46), 0)
    sendIv.background = accentBg(act, 14)
    val inputRow = LinearLayout(act)
    inputRow.orientation = LinearLayout.HORIZONTAL
    inputRow.gravity = Gravity.CENTER_VERTICAL
    inputRow.layoutParams = act.lp(-1, -2, act.dp(10))
    act.msgField.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.msgField, 1f)
    val sendLp = LinearLayout.LayoutParams(act.dp(46), act.dp(46))
    sendLp.marginStart = act.dp(8)
    sendIv.layoutParams = sendLp
    inputRow.addView(act.msgField)
    inputRow.addView(sendIv)
    act.convRoot.addView(top)
    act.convRoot.addView(act.convScroll)
    act.convRoot.addView(inputRow)

    searchIv.setOnClickListener {
        val q = act.searchField.text.toString().trim().lowercase().removePrefix("@")
        if (q.isEmpty()) return@setOnClickListener
        act.searchResults.removeAllViews()
        act.database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    for (u in snapshot.children) {
                        val uid = u.key ?: ""
                        val nick = u.child("nick").getValue(String::class.java) ?: ""
                        if (uid == act.currentUserId) continue
                        if (nick.lowercase().contains(q)) {
                            val row = LinearLayout(act)
                            row.orientation = LinearLayout.HORIZONTAL
                            row.gravity = Gravity.CENTER_VERTICAL
                            row.background = surf(act, 14, filled = true)
                            row.layoutParams = act.lp(-1, -2, act.dp(6))
                            row.setPadding(act.dp(12), act.dp(10), act.dp(12), act.dp(10))
                            row.addView(avatarView(act, null, act.dp(34), nick))
                            val nt = tv(act, nick, 15f, act.fMed, P.text)
                            val ntLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                            ntLp.weight = 1f
                            ntLp.marginStart = act.dp(12)
                            nt.layoutParams = ntLp
                            row.addView(nt)
                            row.setOnClickListener {
                                act.searchResults.removeAllViews()
                                openChat(act, uid, nick)
                            }
                            act.searchResults.addView(row)
                        }
                    }
                    if (act.searchResults.childCount == 0) act.searchResults.addView(tv(act, "Никого не нашли", 14f, act.fMed, P.textSec))
                } catch (e: Exception) {
                }
            }
            override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
        })
    }

    backIv.setOnClickListener {
        act.currentChat = ""
        act.convRoot.visibility = View.GONE
        act.chatListRoot.visibility = View.VISIBLE
        loadConversations(act)
    }

    sendIv.setOnClickListener {
        val text = act.msgField.text.toString()
        if (text.isEmpty() || act.currentChat.isEmpty()) return@setOnClickListener
        val msgId = act.database.child("chats").child(act.currentChat).child("messages").push().key ?: return@setOnClickListener
        val now = System.currentTimeMillis()
        val msg = HashMap<String, Any>()
        msg["senderId"] = act.currentUserId
        msg["senderNick"] = act.currentNick
        msg["text"] = text
        msg["timestamp"] = now
        act.database.child("chats").child(act.currentChat).child("messages").child(msgId).setValue(msg)
        updateConversation(act, act.currentUserId, act.currentChatOtherUid, act.currentChatOtherNick, text, now)
        updateConversation(act, act.currentChatOtherUid, act.currentUserId, act.currentNick, text, now)
        act.msgField.setText("")
    }

    col.addView(act.chatListRoot)
    col.addView(act.convRoot)
    return col
}

internal fun openChat(act: MainActivity, otherUserId: String, otherNick: String) {
    if (otherUserId.isEmpty()) return
    if (otherUserId == act.currentUserId) {
        android.widget.Toast.makeText(act, "Нельзя написать самому себе", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    act.currentChatOtherUid = otherUserId
    act.currentChatOtherNick = otherNick
    act.currentChat = if (act.currentUserId < otherUserId) "${act.currentUserId}_$otherUserId" else "${otherUserId}_${act.currentUserId}"
    act.convTitle.text = otherNick
    act.chatListRoot.visibility = View.GONE
    act.convRoot.visibility = View.VISIBLE
    act.convBox.removeAllViews()
    act.database.child("conversations").child(act.currentUserId).child(otherUserId)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    updateConversation(act, act.currentUserId, otherUserId, otherNick, "", System.currentTimeMillis())
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    loadMessages(act)
}

internal fun loadMessages(act: MainActivity) {
    act.database.child("chats").child(act.currentChat).child("messages").orderByChild("timestamp")
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                act.convBox.removeAllViews()
                var lastText = ""
                var lastTs = 0L
                var lastMine = true
                for (msg in snapshot.children) {
                    val text = msg.child("text").getValue(String::class.java) ?: ""
                    val ts = msg.child("timestamp").getValue(Long::class.java) ?: 0
                    val isMe = msg.child("senderId").getValue(String::class.java) == act.currentUserId
                    lastText = text
                    lastTs = ts
                    lastMine = isMe
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = if (isMe) Gravity.END else Gravity.START
                    row.layoutParams = act.lp(-1, -2, act.dp(4))
                    val bubble = LinearLayout(act)
                    bubble.orientation = LinearLayout.VERTICAL
                    if (isMe) {
                        bubble.background = accentBg(act, 18)
                    } else {
                        bubble.background = surf(act, 18, filled = true)
                    }
                    bubble.setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(6))
                    val mt = tv(act, text, 15f, act.fReg, if (isMe) P.accentText else P.text)
                    val tt = tv(act, timeStr, 10f, act.fReg, if (isMe) P.accentText else P.textSec)
                    tt.gravity = Gravity.END
                    bubble.addView(mt)
                    bubble.addView(tt)
                    row.addView(bubble)
                    act.convBox.addView(row)
                }
                if (!lastMine && lastText.isNotEmpty()) {
                    updateConversation(act, act.currentUserId, act.currentChatOtherUid, act.currentChatOtherNick, lastText, lastTs)
                }
                act.convScroll.post { act.convScroll.fullScroll(View.FOCUS_DOWN) }
            }
            override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
        })
}
