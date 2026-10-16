package com.pogudka.Ahem

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

internal fun buildProfile(act: MainActivity): View {
    act.profileRoot = LinearLayout(act)
    act.profileRoot.orientation = LinearLayout.VERTICAL
    act.profileRoot.setBackgroundColor(P.bg)
    act.profileScroll = ScrollView(act)
    act.profileScroll.layoutParams = act.lp(-1, -1)
    act.profileScroll.isVerticalScrollBarEnabled = false
    val content = LinearLayout(act)
    content.orientation = LinearLayout.VERTICAL
    content.setPadding(act.dp(18), act.dp(14), act.dp(18), 0)

    val nickRow = LinearLayout(act)
    nickRow.orientation = LinearLayout.HORIZONTAL
    nickRow.gravity = Gravity.CENTER_VERTICAL
    nickRow.layoutParams = act.lp(-1, -2, act.dp(16))
    act.profileNick = tv(act, "@", 22f, act.fBold, P.text)
    act.profileNick.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.profileNick, 1f)
    act.profileSettingsIv = iconRound(act, R.drawable.ic_settings, P.text, act.dp(38))
    act.profileSettingsIv.setOnClickListener { showSettingsDialog(act) }
    act.profileEditIv = iconRound(act, R.drawable.ic_edit, P.text, act.dp(38))
    val peLp = LinearLayout.LayoutParams(act.dp(38), act.dp(38))
    peLp.marginStart = act.dp(8)
    act.profileEditIv.layoutParams = peLp
    nickRow.addView(act.profileNick)
    nickRow.addView(act.profileSettingsIv)
    nickRow.addView(act.profileEditIv)
    content.addView(nickRow)

    val infoRow = LinearLayout(act)
    infoRow.orientation = LinearLayout.HORIZONTAL
    infoRow.gravity = Gravity.CENTER_VERTICAL
    infoRow.layoutParams = act.lp(-1, -2, act.dp(16))
    act.profileAvatar = ImageView(act)
    val paLp = LinearLayout.LayoutParams(act.dp(84), act.dp(84))
    paLp.marginEnd = act.dp(20)
    act.profileAvatar.layoutParams = paLp
    act.profileAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
    act.profilePostsCount = TextView(act)
    act.profileFollowersCount = TextView(act)
    act.profileFollowingCount = TextView(act)
    act.profileLikesCount = TextView(act)
    act.profilePostsCount.text = "0"
    act.profileFollowersCount.text = "0"
    act.profileFollowingCount.text = "0"
    act.profileLikesCount.text = "0"
    infoRow.addView(act.profileAvatar)
    infoRow.addView(statCol(act, act.profilePostsCount, "посты"))
    infoRow.addView(statCol(act, act.profileFollowersCount, "подписчики"))
    infoRow.addView(statCol(act, act.profileFollowingCount, "подписки"))
    infoRow.addView(statCol(act, act.profileLikesCount, "лайки"))
    content.addView(infoRow)

    act.profileFollowersCount.setOnClickListener {
        if (act.currentProfileTargetId.isNotEmpty()) openFollowList(act, act.currentProfileTargetId, true)
    }
    act.profileFollowingCount.setOnClickListener {
        if (act.currentProfileTargetId.isNotEmpty()) openFollowList(act, act.currentProfileTargetId, false)
    }

    act.profileName = tv(act, "", 15f, act.fSemi, P.text)
    act.profileName.layoutParams = act.lp(-1, -2, act.dp(2))
    act.profileBio = tv(act, "", 14f, act.fReg, P.text)
    act.profileBio.layoutParams = act.lp(-1, -2, act.dp(14))
    content.addView(act.profileName)
    content.addView(act.profileBio)

    val btnRow = LinearLayout(act)
    btnRow.orientation = LinearLayout.HORIZONTAL
    btnRow.layoutParams = act.lp(-1, -2, act.dp(16))
    act.profileBtn1 = btnOutline(act, "Редактировать профиль")
    act.profileBtn1.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, act.dp(8))
    act.wt(act.profileBtn1, 1f)
    act.profileBtn2 = btnOutline(act, "Поделиться")
    act.profileBtn2.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.profileBtn2, 1f)
    btnRow.addView(act.profileBtn1)
    btnRow.addView(act.profileBtn2)
    content.addView(btnRow)

    val tabRow = LinearLayout(act)
    tabRow.orientation = LinearLayout.HORIZONTAL
    tabRow.background = surf(act, 14, filled = true)
    tabRow.setPadding(act.dp(4), act.dp(4), act.dp(4), act.dp(4))
    tabRow.layoutParams = act.lp(-1, -2, act.dp(2))
    act.profileTabPosts = icon(act, R.drawable.ic_grid, P.text, act.dp(22))
    act.profileTabPosts.setPadding(act.dp(8), act.dp(8), act.dp(8), act.dp(8))
    act.profileTabPosts.layoutParams = act.lp(0, act.dp(38), 0)
    act.wt(act.profileTabPosts, 1f)
    act.profileTabLikes = icon(act, R.drawable.ic_heart, P.navInactive, act.dp(22))
    act.profileTabLikes.setPadding(act.dp(8), act.dp(8), act.dp(8), act.dp(8))
    act.profileTabLikes.layoutParams = act.lp(0, act.dp(38), 0)
    act.wt(act.profileTabLikes, 1f)
    act.profileTabPosts.setOnClickListener { act.profileMode = 0; refreshProfile(act) }
    act.profileTabLikes.setOnClickListener { act.profileMode = 1; refreshProfile(act) }
    tabRow.addView(act.profileTabPosts)
    tabRow.addView(act.profileTabLikes)
    content.addView(tabRow)

    act.profilePostsGrid = LinearLayout(act)
    act.profilePostsGrid.orientation = LinearLayout.VERTICAL
    act.profilePostsGrid.layoutParams = act.lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    content.addView(act.profilePostsGrid)

    act.profileScroll.addView(content)
    act.profileRoot.addView(act.profileScroll)
    return act.profileRoot
}

internal fun statCol(act: MainActivity, num: TextView, label: String): LinearLayout {
    val c = LinearLayout(act)
    c.orientation = LinearLayout.VERTICAL
    c.gravity = Gravity.CENTER
    c.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(c, 1f)
    num.gravity = Gravity.CENTER
    num.textSize = 18f
    num.typeface = act.fBold
    num.setTextColor(P.text)
    num.isClickable = true
    val l = tv(act, label, 11f, act.fReg, P.textSec)
    l.gravity = Gravity.CENTER
    c.addView(num)
    c.addView(l)
    return c
}

internal fun openUserProfile(act: MainActivity, userId: String, nick: String) {
    act.viewingUserId = userId
    act.viewingNick = nick
    act.profileMode = 0
    act.showTab(3, keep = true)
}

internal fun openFollowList(act: MainActivity, targetUid: String, followers: Boolean) {
    if (targetUid.isEmpty()) return
    val dlg = Dialog(act, android.R.style.Theme_Black_NoTitleBar)
    val outer = LinearLayout(act)
    outer.orientation = LinearLayout.VERTICAL
    outer.setBackgroundColor(P.bg)

    val bar = LinearLayout(act)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(act.dp(8), act.dp(10), act.dp(14), act.dp(10))
    val backIv = iconRound(act, R.drawable.ic_back, P.text, act.dp(38))
    backIv.setOnClickListener { dlg.dismiss() }
    val title = tv(act, if (followers) "Подписчики" else "Подписки", 18f, act.fBold, P.text)
    val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    titleLp.weight = 1f
    titleLp.marginStart = act.dp(12)
    title.layoutParams = titleLp
    bar.addView(backIv)
    bar.addView(title)
    outer.addView(bar)

    val scroll = ScrollView(act)
    scroll.isVerticalScrollBarEnabled = false
    val list = LinearLayout(act)
    list.orientation = LinearLayout.VERTICAL
    list.setPadding(act.dp(14), act.dp(6), act.dp(14), act.dp(20))
    scroll.addView(list)
    val slp = LinearLayout.LayoutParams(-1, 0)
    slp.weight = 1f
    outer.addView(scroll, slp)

    val node = if (followers) "followers" else "following"
    act.database.child(node).child(targetUid).addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                list.removeAllViews()
                val cnt = snapshot.children.count()
                title.text = (if (followers) "Подписчики " else "Подписки ") + cnt
                if (cnt == 0) {
                    val empty = tv(act, "Пусто", 14f, act.fMed, P.textSec)
                    empty.setPadding(act.dp(4), act.dp(20), act.dp(4), act.dp(20))
                    list.addView(empty)
                    return
                }
                for (child in snapshot.children) {
                    val uid = child.key ?: ""
                    val nick = child.child("nick").getValue(String::class.java) ?: "?"
                    if (uid.isEmpty()) continue
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.background = surf(act, 14, filled = true)
                    val rl = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                    rl.bottomMargin = act.dp(6)
                    row.layoutParams = rl
                    row.setPadding(act.dp(12), act.dp(10), act.dp(12), act.dp(10))
                    val av = avatarView(act, null, act.dp(40), nick)
                    row.addView(av)
                    act.database.child("users").child(uid).child("avatar")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(as2: DataSnapshot) {
                                val s = as2.getValue(String::class.java) ?: ""
                                if (s.isNotEmpty()) {
                                    val b = decodeScaled(s, act.dp(40))
                                    if (b != null) av.setImageBitmap(makeCircularBitmap(b, act.dp(40)))
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    val nt = tv(act, "@$nick", 15f, act.fSemi, P.text)
                    val ntLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    ntLp.weight = 1f
                    ntLp.marginStart = act.dp(12)
                    nt.layoutParams = ntLp
                    row.addView(nt)
                    row.setOnClickListener {
                        dlg.dismiss()
                        openUserProfile(act, uid, nick)
                    }
                    list.addView(row)
                }
            } catch (e: Exception) {
            }
        }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    })

    dlg.setContentView(outer)
    dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    dlg.show()
}

internal fun showSettingsDialog(act: MainActivity) {
    val dlg = Dialog(act)
    dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    val root = LinearLayout(act)
    root.orientation = LinearLayout.VERTICAL
    root.background = surf(act, 20, filled = true)
    root.setPadding(act.dp(20), act.dp(18), act.dp(20), act.dp(20))
    val width = (act.resources.displayMetrics.widthPixels * 0.88).toInt()
    val wrap = FrameLayout(act)
    val rlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
    rlp.gravity = Gravity.CENTER
    wrap.addView(root, rlp)

    val head = LinearLayout(act)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    head.layoutParams = act.lp(-1, -2, act.dp(16))
    val title = tv(act, "Настройки", 18f, act.fBold, P.text)
    title.layoutParams = act.lp(0, -2, 0)
    act.wt(title, 1f)
    val closeIv = iconRound(act, R.drawable.ic_close, P.text, act.dp(34))
    closeIv.setOnClickListener { dlg.dismiss() }
    head.addView(title)
    head.addView(closeIv)
    root.addView(head)

    val themeLbl = tv(act, "Тема оформления", 13f, act.fMed, P.textSec)
    themeLbl.layoutParams = act.lp(-1, -2, act.dp(10))
    root.addView(themeLbl)
    val darkNow = isDark(act)
    val lightOpt = themeOption(act, "Светлая", !darkNow)
    lightOpt.setOnClickListener { setThemeAndRestart(act, false); dlg.dismiss() }
    val darkOpt = themeOption(act, "Тёмная", darkNow)
    darkOpt.setOnClickListener { setThemeAndRestart(act, true); dlg.dismiss() }
    root.addView(lightOpt)
    val gap = View(act)
    gap.layoutParams = act.lp(-1, act.dp(8), 0)
    root.addView(gap)
    root.addView(darkOpt)

    val sep = View(act)
    sep.layoutParams = act.lp(-1, act.dp(1), act.dp(16))
    sep.setBackgroundColor(P.border)
    root.addView(sep)

    val accLbl = tv(act, "Аккаунт", 13f, act.fMed, P.textSec)
    accLbl.layoutParams = act.lp(-1, -2, act.dp(10))
    root.addView(accLbl)

    val switchAcc = btnOutline(act, "Сменить аккаунт")
    switchAcc.layoutParams = act.lp(-1, -2, act.dp(8))
    switchAcc.setOnClickListener { dlg.dismiss(); showAccountsDialog(act) }
    root.addView(switchAcc)

    val logout = btnOutline(act, "Выйти из аккаунта")
    logout.layoutParams = act.lp(-1, -2, 0)
    logout.setOnClickListener {
        dlg.dismiss()
        act.viewingUserId = ""
        act.viewingNick = ""
        act.lastProfileTarget = ""
        act.auth.signOut()
        act.showTab(0)
    }
    root.addView(logout)

    dlg.setContentView(wrap)
    dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    dlg.show()
}

internal fun showAccountsDialog(act: MainActivity) {
    val dlg = Dialog(act)
    dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    val root = LinearLayout(act)
    root.orientation = LinearLayout.VERTICAL
    root.background = surf(act, 20, filled = true)
    root.setPadding(act.dp(20), act.dp(18), act.dp(20), act.dp(20))
    val width = (act.resources.displayMetrics.widthPixels * 0.88).toInt()
    val wrap = FrameLayout(act)
    val rlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
    rlp.gravity = Gravity.CENTER
    wrap.addView(root, rlp)

    val head = LinearLayout(act)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    head.layoutParams = act.lp(-1, -2, act.dp(16))
    val backIv = iconRound(act, R.drawable.ic_back, P.text, act.dp(34))
    backIv.setOnClickListener { dlg.dismiss(); showSettingsDialog(act) }
    val title = tv(act, "Аккаунты", 18f, act.fBold, P.text)
    title.layoutParams = act.lp(0, -2, 0)
    act.wt(title, 1f)
    (title.layoutParams as LinearLayout.LayoutParams).marginStart = act.dp(12)
    head.addView(backIv)
    head.addView(title)
    root.addView(head)

    val accounts = loadAccounts(act)
    if (accounts.isEmpty()) {
        root.addView(tv(act, "Нет сохранённых аккаунтов", 14f, act.fMed, P.textSec))
    } else {
        for (acc in accounts) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = if (acc.uid == act.currentUserId) surf(act, 12, filled = true) else surf(act, 12, filled = false)
            row.setPadding(act.dp(14), act.dp(12), act.dp(14), act.dp(12))
            row.layoutParams = act.lp(-1, -2, act.dp(6))
            row.addView(avatarView(act, null, act.dp(36), acc.nick))
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
            bp.weight = 1f
            bp.marginStart = act.dp(12)
            box.layoutParams = bp
            box.addView(tv(act, acc.nick, 15f, act.fSemi, P.text))
            box.addView(tv(act, acc.email, 12f, act.fReg, P.textSec))
            row.addView(box)
            if (acc.uid == act.currentUserId) {
                row.addView(tv(act, "сейчас", 11f, act.fMed, P.textSec))
            }
            row.setOnClickListener {
                dlg.dismiss()
                switchToAccount(act, acc)
            }
            root.addView(row)
        }
    }

    val addBtn = btnAccent(act, "Добавить аккаунт")
    val addLp = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
    addLp.topMargin = act.dp(8)
    addBtn.layoutParams = addLp
    addBtn.setOnClickListener {
        dlg.dismiss()
        switchToManual(act)
    }
    root.addView(addBtn)

    dlg.setContentView(wrap)
    dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    dlg.show()
}

internal fun switchToAccount(act: MainActivity, acc: Acc) {
    act.viewingUserId = ""
    act.viewingNick = ""
    act.lastProfileTarget = ""
    act.currentUserId = ""
    act.currentNick = ""
    act.currentEmail = ""
    act.currentAvatar = ""
    act.myFollowingIds.clear()
    act.imageCache.clear()
    act.showTab(0)
    act.feedBlock.visibility = View.GONE
    act.authBlock.visibility = View.VISIBLE
    act.isLoggingIn = true
    act.logBtn.text = "..."
    val restore = Runnable {
        act.isLoggingIn = false
        act.logBtn.text = "Вход"
    }
    act.uiHandler.postDelayed(restore, 15000)
    act.auth.signInWithEmailAndPassword(acc.email, acc.pass).addOnCompleteListener { task ->
        act.uiHandler.removeCallbacks(restore)
        act.isLoggingIn = false
        act.logBtn.text = "Вход"
        if (task.isSuccessful) {
            val uid = act.auth.currentUser?.uid ?: ""
            act.currentUserId = uid
            act.currentEmail = acc.email
            act.viewingUserId = uid
            enterFeedMode(act)
            loadMyFollowing(act) { renderFeed(act) }
            act.database.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    act.currentNick = snapshot.child("nick").getValue(String::class.java) ?: acc.nick
                    act.currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                    act.saveSessionPrefs()
                    renderFeed(act)
                    refreshProfile(act)
                }
                override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
            })
        } else {
            act.feedEmail.setText(acc.email)
            act.feedNick.setText("")
            act.feedPass.setText("")
            act.feedStatus.text = "Введите пароль для ${acc.email}"
        }
    }
}

internal fun switchToManual(act: MainActivity) {
    act.viewingUserId = ""
    act.viewingNick = ""
    act.lastProfileTarget = ""
    act.currentUserId = ""
    act.currentNick = ""
    act.currentEmail = ""
    act.currentAvatar = ""
    act.myFollowingIds.clear()
    act.imageCache.clear()
    act.showTab(0)
    act.feedBlock.visibility = View.GONE
    act.authBlock.visibility = View.VISIBLE
    act.feedStatus.text = "вход / регистрация"
    act.feedEmail.setText("")
    act.feedNick.setText("")
    act.feedPass.setText("")
}

internal fun themeOption(act: MainActivity, label: String, selected: Boolean): LinearLayout {
    val row = LinearLayout(act)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.background = if (selected) surf(act, 12, filled = true) else surf(act, 12, filled = false)
    row.setPadding(act.dp(16), act.dp(14), act.dp(16), act.dp(14))
    row.layoutParams = act.lp(-1, -2, 0)
    val dot = View(act)
    dot.layoutParams = act.lp(act.dp(12), act.dp(12), 0)
    val dg = GradientDrawable()
    dg.shape = GradientDrawable.OVAL
    dg.setColor(if (selected) P.accent else P.border)
    dot.background = dg
    (dot.layoutParams as LinearLayout.LayoutParams).marginEnd = act.dp(12)
    val t = tv(act, label, 15f, if (selected) act.fSemi else act.fReg, P.text)
    row.addView(dot)
    row.addView(t)
    return row
}

internal fun setThemeAndRestart(act: MainActivity, dark: Boolean) {
    act.getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
    act.recreate()
}

internal fun showEditProfileDialog(act: MainActivity) {
    if (act.viewingUserId != act.currentUserId) return
    val dlg = Dialog(act)
    dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    val layout = LinearLayout(act)
    layout.orientation = LinearLayout.VERTICAL
    layout.background = surf(act, 20, filled = true)
    layout.setPadding(act.dp(20), act.dp(18), act.dp(20), act.dp(20))
    val width = (act.resources.displayMetrics.widthPixels * 0.9).toInt()
    val wrap = FrameLayout(act)
    val wlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
    wlp.gravity = Gravity.CENTER
    wrap.addView(layout, wlp)

    val headerLayout = LinearLayout(act)
    headerLayout.orientation = LinearLayout.HORIZONTAL
    headerLayout.gravity = Gravity.CENTER_VERTICAL
    headerLayout.layoutParams = act.lp(-1, -2, act.dp(16))
    val backIv = iconRound(act, R.drawable.ic_close, P.text, act.dp(36))
    backIv.setOnClickListener { dlg.dismiss() }
    val title = tv(act, "Редактировать", 17f, act.fBold, P.text)
    title.layoutParams = act.lp(0, -2, 0)
    act.wt(title, 1f)
    (title.layoutParams as LinearLayout.LayoutParams).marginStart = act.dp(12)
    headerLayout.addView(backIv)
    headerLayout.addView(title)

    val avatarLayout = LinearLayout(act)
    avatarLayout.orientation = LinearLayout.VERTICAL
    avatarLayout.gravity = Gravity.CENTER_HORIZONTAL
    avatarLayout.layoutParams = act.lp(-1, -2, act.dp(16))
    act.editAvatarView = ImageView(act)
    val alp = LinearLayout.LayoutParams(act.dp(96), act.dp(96))
    alp.bottomMargin = act.dp(12)
    alp.gravity = Gravity.CENTER_HORIZONTAL
    act.editAvatarView!!.layoutParams = alp
    act.editAvatarView!!.scaleType = ImageView.ScaleType.FIT_CENTER
    if (act.currentAvatar.isNotEmpty()) {
        val b = decodeScaled(act.currentAvatar, act.dp(96))
        act.editAvatarView!!.setImageBitmap(if (b != null) makeCircularBitmap(b, act.dp(96)) else makeCircularBitmap(createAvatarBitmap(act, act.currentNick), act.dp(96)))
    } else {
        act.editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(act, act.currentNick), act.dp(96)))
    }
    val changeAvatarBtn = btnOutline(act, "Сменить аватар")
    changeAvatarBtn.layoutParams = act.lp(-1, -2, 0)
    avatarLayout.addView(act.editAvatarView)
    avatarLayout.addView(changeAvatarBtn)

    val nickEdit = field(act, "Ник (мин. 5 символов)")
    nickEdit.setText(act.currentNick)
    nickEdit.layoutParams = act.lp(-1, -2, act.dp(10))
    val bioEdit = field(act, "Био")
    bioEdit.setText(act.profileBio.text.toString())
    bioEdit.layoutParams = act.lp(-1, -2, act.dp(14))
    bioEdit.gravity = Gravity.TOP
    bioEdit.minLines = 3
    bioEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    val saveBtn = btnAccent(act, "Сохранить")
    saveBtn.layoutParams = act.lp(-1, -2, 0)

    changeAvatarBtn.setOnClickListener {
        act.startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), act.PICK_AVATAR)
    }
    saveBtn.setOnClickListener {
        val newNick = nickEdit.text.toString().trim()
        val newBio = bioEdit.text.toString().trim()
        if (newNick.length < 5) {
            Toast.makeText(act, "Ник минимум 5 символов", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        isNickTaken(act, newNick, act.currentUserId) { taken ->
            if (taken) {
                Toast.makeText(act, "Ник уже занят", Toast.LENGTH_SHORT).show()
                return@isNickTaken
            }
            if (newNick != act.currentNick) {
                act.database.child("users").child(act.currentUserId).child("nick").setValue(newNick)
                act.currentNick = newNick
                saveAccount(act, act.currentUserId, newNick, act.currentEmail, act.feedPass.text.toString())
                updatePostsNick(act, newNick)
            }
            act.database.child("users").child(act.currentUserId).child("bio").setValue(newBio)
            dlg.dismiss()
            refreshProfile(act)
            loadFeed(act)
        }
    }

    layout.addView(headerLayout)
    layout.addView(avatarLayout)
    layout.addView(nickEdit)
    layout.addView(bioEdit)
    layout.addView(saveBtn)
    dlg.setContentView(wrap)
    dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    dlg.show()
}

internal fun refreshProfile(act: MainActivity) {
    act.profileGen += 1
    val targetId = if (act.viewingUserId.isNotEmpty()) act.viewingUserId else act.currentUserId
    act.currentProfileTargetId = targetId
    if (targetId.isEmpty()) return
    if (act.lastProfileTarget != targetId) {
        act.lastProfileTarget = targetId
        act.profileNick.text = "@"
        act.profileName.text = ""
        act.profileBio.text = ""
        act.profilePostsCount.text = "0"
        act.profileLikesCount.text = "0"
        act.profileFollowersCount.text = "0"
        act.profileFollowingCount.text = "0"
        act.profilePostsGrid.removeAllViews()
        act.profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(act, ""), act.dp(84)))
    }
    act.profileTabPosts.setColorFilter(if (act.profileMode == 0) P.text else P.navInactive)
    act.profileTabLikes.setColorFilter(if (act.profileMode == 1) P.accent else P.navInactive)
    val key = "$targetId|${act.profileMode}"
    act.gridCache[key]?.let { renderGrid(act, it) }
    act.gridStatsCache[key]?.let {
        act.profilePostsCount.text = fmtNum(it.first)
        act.profileLikesCount.text = fmtNum(it.second)
    }
    scheduleMigration(act)
    refreshProfileData(act)
}

internal fun renderGrid(act: MainActivity, grid: List<DataSnapshot>) {
    act.profilePostsGrid.removeAllViews()
    if (grid.isEmpty()) {
        val empty = tv(act, if (act.profileMode == 0) "Нет постов" else "Нет лайков", 14f, act.fMed, P.textSec)
        empty.gravity = Gravity.CENTER
        empty.setPadding(0, act.dp(40), 0, act.dp(40))
        act.profilePostsGrid.addView(empty)
        return
    }
    val sortedGrid = grid.sortedByDescending { getTs(it) }
    val cell = (act.resources.displayMetrics.widthPixels - act.dp(36) - act.dp(4)) / 3
    var i = 0
    while (i < sortedGrid.size) {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = act.lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        for (c in 0 until 3) {
            val idx = i + c
            val box = FrameLayout(act)
            val p2 = LinearLayout.LayoutParams(cell, cell)
            p2.setMargins(0, 0, act.dp(2), act.dp(2))
            box.layoutParams = p2
            if (idx < sortedGrid.size) {
                try {
                    val p = sortedGrid[idx]
                    val postId = p.key ?: ""
                    val txt = p.child("text").getValue(String::class.java) ?: ""
                    val lc = (act.statsMap[postId]?.child("likes")?.value as? Number)?.toLong() ?: 0L
                    box.background = surf(act, 6, filled = true)
                    val tt = tv(act, txt, 11f, act.fMed, P.text)
                    tt.maxLines = 6
                    tt.setPadding(act.dp(6), act.dp(6), act.dp(6), act.dp(18))
                    box.addView(tt)
                    mediaFor(act, postId) { str ->
                        try {
                            val first = str.split(",").firstOrNull { it.isNotEmpty() }
                            if (first != null) {
                                val bb = decodeCached(act, first, cell, "pg$postId")
                                if (bb != null) {
                                    box.removeAllViews()
                                    val iv = ImageView(act)
                                    iv.setImageBitmap(bb)
                                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                    iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                    box.addView(iv)
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                    box.setOnClickListener { openPostDetail(act, p) }
                    val liked = act.statsMap[postId]?.child("likedBy")?.child(act.currentUserId)?.value != null
                    val heartQuick = icon(act, R.drawable.ic_heart_outline, P.textSec, act.dp(26))
                    heartQuick.setPadding(act.dp(4), act.dp(4), act.dp(4), act.dp(4))
                    val hp = FrameLayout.LayoutParams(act.dp(26), act.dp(26))
                    hp.gravity = Gravity.TOP or Gravity.END
                    hp.setMargins(0, act.dp(6), act.dp(6), 0)
                    box.addView(heartQuick, hp)
                    val cntHidden = tv(act, "♥ " + fmtNum(lc), 12f, act.fSemi, P.text)
                    attachLike(act, heartQuick, cntHidden, heartQuick, postId, liked)
                } catch (e: Exception) {
                }
            }
            row.addView(box)
        }
        act.profilePostsGrid.addView(row)
        i += 3
    }
}

internal fun refreshProfileData(act: MainActivity) {
    val gen = act.profileGen
    val targetId = act.currentProfileTargetId
    if (targetId.isEmpty()) return

    act.database.child("followers").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (gen != act.profileGen) return
            try { act.profileFollowersCount.text = fmtNum(snapshot.children.count().toLong()) } catch (e: Exception) {}
        }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    })
    act.database.child("following").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (gen != act.profileGen) return
            try { act.profileFollowingCount.text = fmtNum(snapshot.children.count().toLong()) } catch (e: Exception) {}
        }
        override fun onCancelled(error: DatabaseError) { dbDenied(act, error) }
    })

    act.database.child("users").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (gen != act.profileGen) return
            try {
                val nick = snapshot.child("nick").getValue(String::class.java) ?: "?"
                val avatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                val bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                val b = if (avatar.isNotEmpty()) decodeScaled(avatar, act.dp(84)) else null
                act.profileAvatar.setImageBitmap(if (b != null) makeCircularBitmap(b, act.dp(84)) else makeCircularBitmap(createAvatarBitmap(act, nick), act.dp(84)))
                act.profileNick.text = "@$nick"
                act.profileName.text = nick
                act.profileBio.text = bio
                applyProfileButtons(act, targetId, nick)
                loadProfileGrid(act, gen, targetId)
            } catch (e: Exception) {
            }
        }
        override fun onCancelled(error: DatabaseError) {
            if (gen != act.profileGen) return
            val fallbackNick = if (act.currentNick.isNotEmpty()) act.currentNick else "?"
            act.profileNick.text = "@$fallbackNick"
            act.profileName.text = fallbackNick
            act.profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(act, fallbackNick), act.dp(84)))
            applyProfileButtons(act, targetId, fallbackNick)
            loadProfileGrid(act, gen, targetId)
        }
    })
}

internal fun applyProfileButtons(act: MainActivity, targetId: String, nick: String) {
    if (targetId == act.currentUserId) {
        act.profileBtn1.text = "Редактировать профиль"
        act.profileBtn1.setOnClickListener { showEditProfileDialog(act) }
        act.profileEditIv.visibility = View.VISIBLE
        act.profileEditIv.setOnClickListener { showEditProfileDialog(act) }
        act.profileSettingsIv.visibility = View.VISIBLE
    } else {
        act.profileBtn1.text = "Написать"
        act.profileBtn1.setOnClickListener { openChat(act, targetId, nick) }
        act.profileEditIv.visibility = View.GONE
        act.profileSettingsIv.visibility = View.GONE
    }
    act.profileBtn2.setOnClickListener {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nick", "@$nick"))
        Toast.makeText(act, "Ник скопирован", Toast.LENGTH_SHORT).show()
    }
}

internal fun loadProfileGrid(act: MainActivity, gen: Int, targetId: String) {
    val postsRef = act.database.child("posts")
    val statsRef = act.database.child("postStats")
    var psSnap: DataSnapshot? = null
    var stSnap: DataSnapshot? = null
    var done = false
    fun finish() {
        if (gen != act.profileGen) return
        if (done) return
        done = true
        act.statsMap.clear()
        if (stSnap != null) for (s in stSnap!!.children) s.key?.let { act.statsMap[it] = s }
        var ownCount = 0L
        var sumLikes = 0L
        val grid = ArrayList<DataSnapshot>()
        if (psSnap != null) {
            for (p in psSnap!!.children) {
                val isMine = p.child("authorId").getValue(String::class.java) == targetId
                val liked = act.statsMap[p.key ?: ""]?.child("likedBy")?.child(targetId)?.value != null
                if (isMine) {
                    ownCount++
                    sumLikes += (act.statsMap[p.key ?: ""]?.child("likes")?.value as? Number)?.toLong() ?: 0L
                }
                if (act.profileMode == 0 && isMine) grid.add(p)
                if (act.profileMode == 1 && liked) grid.add(p)
            }
        }
        val key = "$targetId|${act.profileMode}"
        act.gridCache[key] = grid
        act.gridStatsCache[key] = Pair(ownCount, sumLikes)
        act.profilePostsCount.text = fmtNum(ownCount)
        act.profileLikesCount.text = fmtNum(sumLikes)
        renderGrid(act, grid)
    }
    postsRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { psSnap = snapshot; finish() }
        override fun onCancelled(error: DatabaseError) {
            psSnap = null
            dbDenied(act, error)
            val key = "$targetId|${act.profileMode}"
            act.gridCache[key]?.let { renderGrid(act, it) }
        }
    })
    statsRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { stSnap = snapshot; finish() }
        override fun onCancelled(error: DatabaseError) { stSnap = null; finish() }
    })
}
