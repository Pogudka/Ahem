package com.pogudka.Ahem

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.text.Editable
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

internal fun mediaFor(act: MainActivity, postId: String, cb: (String) -> Unit) {
    act.mediaCache[postId]?.let { cb(it); return }
    act.database.child("postMedia").child(postId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val str = snapshot.getValue(String::class.java) ?: ""
            if (str.isNotEmpty()) act.mediaCache[postId] = str
            cb(str)
        }
        override fun onCancelled(error: DatabaseError) { cb("") }
    })
}

internal fun openViewerByPost(act: MainActivity, postId: String, start: Int) {
    mediaFor(act, postId) { str ->
        val images = str.split(",").filter { it.isNotEmpty() }
        if (images.isNotEmpty()) openViewer(act, images, start)
    }
}

internal fun buildFeed(act: MainActivity): View {
    val col = LinearLayout(act)
    col.orientation = LinearLayout.VERTICAL
    col.setBackgroundColor(P.bg)

    act.authBlock = LinearLayout(act)
    act.authBlock.orientation = LinearLayout.VERTICAL
    act.authBlock.setPadding(act.dp(24), act.dp(48), act.dp(24), 0)

    val logo = tv(act, "Ahem", 34f, act.fBold, P.text)
    val tag = tv(act, "social", 13f, act.fMed, P.textSec)
    tag.layoutParams = act.lp(-2, -2, act.dp(32))

    act.feedEmail = field(act, "Email")
    act.feedEmail.layoutParams = act.lp(-1, -2, act.dp(10))
    act.feedNick = field(act, "Ник (мин. 5 символов)")
    act.feedNick.layoutParams = act.lp(-1, -2, act.dp(10))
    act.feedPass = field(act, "Пароль")
    act.feedPass.inputType = 1
    act.feedPass.layoutParams = act.lp(-1, -2, act.dp(16))

    val rowAuth = LinearLayout(act)
    rowAuth.orientation = LinearLayout.HORIZONTAL
    rowAuth.layoutParams = act.lp(-1, -2, act.dp(12))
    act.regBtn = btnAccent(act, "Регистрация")
    act.regBtn.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, act.dp(6))
    act.wt(act.regBtn, 1f)
    act.logBtn = btnOutline(act, "Вход")
    act.logBtn.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.logBtn, 1f)
    rowAuth.addView(act.regBtn)
    rowAuth.addView(act.logBtn)

    act.feedStatus = tv(act, "не залогинен", 13f, act.fReg, P.textSec)

    act.authBlock.addView(logo)
    act.authBlock.addView(tag)
    act.authBlock.addView(act.feedEmail)
    act.authBlock.addView(act.feedNick)
    act.authBlock.addView(act.feedPass)
    act.authBlock.addView(rowAuth)
    act.authBlock.addView(act.feedStatus)

    act.feedBlock = LinearLayout(act)
    act.feedBlock.orientation = LinearLayout.VERTICAL
    act.feedBlock.visibility = View.GONE
    act.feedBlock.setBackgroundColor(P.bg)

    val top = LinearLayout(act)
    top.orientation = LinearLayout.HORIZONTAL
    top.gravity = Gravity.CENTER_VERTICAL
    top.setPadding(act.dp(18), act.dp(14), act.dp(18), act.dp(10))
    val logoS = tv(act, "Ahem", 20f, act.fBold, P.text)
    logoS.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(logoS, 1f)
    top.addView(logoS)

    val composer = LinearLayout(act)
    composer.orientation = LinearLayout.VERTICAL
    composer.background = surf(act, 18, filled = true)
    composer.setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(14))
    val compM = LinearLayout.LayoutParams(-1, -2)
    compM.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
    composer.layoutParams = compM

    act.feedPostText = field(act, "Подпись к посту (до 4 фото)")
    act.feedPostText.layoutParams = act.lp(-1, -2, act.dp(10))

    act.pendingWrap = LinearLayout(act)
    act.pendingWrap.orientation = LinearLayout.VERTICAL
    act.pendingWrap.visibility = View.GONE
    act.pendingWrap.layoutParams = act.lp(-1, -2, act.dp(10))
    val pendHead = LinearLayout(act)
    pendHead.orientation = LinearLayout.HORIZONTAL
    pendHead.gravity = Gravity.CENTER_VERTICAL
    act.pendingInfo = tv(act, "", 12f, act.fMed, P.textSec)
    act.pendingInfo.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.pendingInfo, 1f)
    val clearIv = iconRound(act, R.drawable.ic_close, P.text, act.dp(30))
    pendHead.addView(act.pendingInfo)
    pendHead.addView(clearIv)
    val pendScroll = HorizontalScrollView(act)
    pendScroll.layoutParams = act.lp(-1, -2, 0)
    pendScroll.isHorizontalScrollBarEnabled = false
    act.pendingRow = LinearLayout(act)
    act.pendingRow.orientation = LinearLayout.HORIZONTAL
    pendScroll.addView(act.pendingRow)
    act.pendingWrap.addView(pendHead)
    act.pendingWrap.addView(pendScroll)

    val cRow = LinearLayout(act)
    cRow.orientation = LinearLayout.HORIZONTAL
    cRow.gravity = Gravity.CENTER_VERTICAL
    val camIv = iconRound(act, R.drawable.ic_camera, P.text, act.dp(44))
    val pubBtn = btnAccent(act, "Опубликовать")
    val pubLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    pubLp.marginStart = act.dp(10)
    pubBtn.layoutParams = pubLp
    act.wt(pubBtn, 1f)
    cRow.addView(camIv)
    cRow.addView(pubBtn)

    composer.addView(act.feedPostText)
    composer.addView(act.pendingWrap)
    composer.addView(cRow)

    val searchRow = LinearLayout(act)
    searchRow.orientation = LinearLayout.HORIZONTAL
    searchRow.gravity = Gravity.CENTER_VERTICAL
    val raM = LinearLayout.LayoutParams(-1, -2)
    raM.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
    searchRow.layoutParams = raM
    act.feedSearch = field(act, "Поиск: текст или @ник")
    act.feedSearch.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(act.feedSearch, 1f)
    val searchClear = iconRound(act, R.drawable.ic_close, P.textSec, act.dp(36))
    (searchClear.layoutParams as LinearLayout.LayoutParams).marginStart = act.dp(8)
    searchRow.addView(act.feedSearch)
    searchRow.addView(searchClear)

    act.feedSearch.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            act.feedQuery = s?.toString()?.trim() ?: ""
            renderFeed(act)
        }
    })
    searchClear.setOnClickListener {
        act.feedSearch.setText("")
    }

    act.feedScroll = ScrollView(act)
    act.feedScroll.isVerticalScrollBarEnabled = false
    act.feedScroll.layoutParams = act.lp(-1, 0, 0)
    act.wt(act.feedScroll, 1f)

    act.ptrRoot = LinearLayout(act)
    act.ptrRoot.orientation = LinearLayout.VERTICAL

    act.ptrHeader = FrameLayout(act)
    val phLp = LinearLayout.LayoutParams(-1, 0)
    act.ptrHeader.layoutParams = phLp
    act.ptrHeader.clipChildren = true
    act.ptrSpin = ImageView(act)
    act.ptrSpin.setImageResource(R.drawable.ic_refresh)
    act.ptrSpin.setColorFilter(P.textSec)
    val spinLp = FrameLayout.LayoutParams(act.dp(32), act.dp(32))
    spinLp.gravity = Gravity.CENTER
    act.ptrSpin.layoutParams = spinLp
    act.ptrHeader.addView(act.ptrSpin)

    act.feedBox = LinearLayout(act)
    act.feedBox.orientation = LinearLayout.VERTICAL

    act.ptrRoot.addView(act.ptrHeader)
    act.ptrRoot.addView(act.feedBox)
    act.feedScroll.addView(act.ptrRoot)

    setupPtr(act)

    act.feedBlock.addView(top)
    act.feedBlock.addView(composer)
    act.feedBlock.addView(searchRow)
    act.feedBlock.addView(act.feedScroll)

    act.regBtn.setOnClickListener {
        if (act.isRegistering) return@setOnClickListener
        val email = act.feedEmail.text.toString().trim()
        val nick = act.feedNick.text.toString().trim()
        val pass = act.feedPass.text.toString()
        if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) {
            act.feedStatus.text = "Заполни все поля"
            return@setOnClickListener
        }
        if (nick.length < 5) {
            act.feedStatus.text = "Ник минимум 5 символов"
            return@setOnClickListener
        }
        act.isRegistering = true
        act.regBtn.text = "..."
        val restore = Runnable {
            act.isRegistering = false
            act.regBtn.text = "Регистрация"
        }
        act.uiHandler.postDelayed(restore, 15000)
        isNickTaken(act, nick, "") { taken ->
            if (taken) {
                act.uiHandler.removeCallbacks(restore)
                act.feedStatus.text = "Ник уже занят"
                act.isRegistering = false
                act.regBtn.text = "Регистрация"
                return@isNickTaken
            }
            act.auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                act.uiHandler.removeCallbacks(restore)
                if (task.isSuccessful) {
                    val userId = act.auth.currentUser?.uid ?: return@addOnCompleteListener
                    act.database.child("users").child(userId).child("email").setValue(email)
                    act.database.child("users").child(userId).child("nick").setValue(nick)
                    act.currentUserId = userId
                    act.currentNick = nick
                    act.currentEmail = email
                    act.viewingUserId = userId
                    saveAccount(act, userId, nick, email, pass)
                    loadMyFollowing(act) {
                        act.feedStatus.text = "Зарегистрирован!"
                        enterFeedMode(act)
                    }
                } else {
                    act.feedStatus.text = "Ошибка: ${task.exception?.message}"
                }
                act.isRegistering = false
                act.regBtn.text = "Регистрация"
            }
        }
    }

    act.logBtn.setOnClickListener {
        if (act.isLoggingIn) return@setOnClickListener
        val email = act.feedEmail.text.toString().trim()
        val pass = act.feedPass.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            act.feedStatus.text = "Email + пароль"
            return@setOnClickListener
        }
        act.isLoggingIn = true
        act.logBtn.text = "..."
        val restore = Runnable {
            act.isLoggingIn = false
            act.logBtn.text = "Вход"
        }
        act.uiHandler.postDelayed(restore, 15000)
        act.auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            act.uiHandler.removeCallbacks(restore)
            if (task.isSuccessful) {
                val userId = act.auth.currentUser?.uid ?: return@addOnCompleteListener
                act.currentUserId = userId
                act.currentEmail = email
                act.viewingUserId = userId
                loadMyFollowing(act) {
                    act.database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            act.currentNick = snapshot.child("nick").getValue(String::class.java) ?: "Аноним"
                            act.currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                            saveAccount(act, userId, act.currentNick, email, pass)
                            enterFeedMode(act)
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            } else {
                act.feedStatus.text = "Ошибка: ${task.exception?.message}"
            }
            act.isLoggingIn = false
            act.logBtn.text = "Вход"
        }
    }

    clearIv.setOnClickListener {
        act.pendingImages.clear()
        act.pendingWrap.visibility = View.GONE
    }

    camIv.setOnClickListener {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        act.startActivityForResult(Intent.createChooser(intent, "Выбери фото"), act.PICK_IMAGES)
    }

    pubBtn.setOnClickListener {
        val text = act.feedPostText.text.toString()
        if (text.isEmpty() && act.pendingImages.isEmpty()) return@setOnClickListener
        val postId = act.database.child("posts").push().key ?: return@setOnClickListener
        val post = HashMap<String, Any>()
        post["authorId"] = act.currentUserId
        post["authorNick"] = act.currentNick
        post["text"] = text
        post["timestamp"] = System.currentTimeMillis()
        act.database.child("posts").child(postId).setValue(post)
        if (act.pendingImages.isNotEmpty()) {
            act.database.child("postMedia").child(postId).setValue(act.pendingImages.joinToString(","))
        }
        val stats = HashMap<String, Any>()
        stats["likes"] = 0
        stats["views"] = 0
        stats["likedBy"] = HashMap<String, Boolean>()
        stats["viewedBy"] = HashMap<String, Boolean>()
        act.database.child("postStats").child(postId).setValue(stats)
        act.feedPostText.setText("")
        act.pendingImages.clear()
        act.pendingWrap.visibility = View.GONE
        loadFeed(act)
    }

    col.addView(act.authBlock)
    col.addView(act.feedBlock)
    return col
}

internal fun setupPtr(act: MainActivity) {
    val threshold = act.dp(72)
    val maxPull = act.dp(120)
    act.feedScroll.setOnTouchListener { _, ev ->
        if (act.ptrLoading) return@setOnTouchListener false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                act.ptrStartY = ev.rawY
                act.ptrPulling = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - act.ptrStartY
                if (act.feedScroll.scrollY <= 0 && dy > 0) {
                    act.ptrPulling = true
                    val pull = (dy * 0.5f).coerceIn(0f, maxPull.toFloat())
                    val h = act.ptrHeader.layoutParams as LinearLayout.LayoutParams
                    h.height = pull.toInt()
                    act.ptrHeader.layoutParams = h
                    val rot = (pull / maxPull.toFloat()) * 360f
                    act.ptrSpin.rotation = rot
                    act.feedScroll.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (act.ptrPulling) {
                    val h = act.ptrHeader.layoutParams as LinearLayout.LayoutParams
                    if (h.height >= threshold) {
                        startPtrLoading(act, maxPull)
                    } else {
                        collapsePtr(act)
                    }
                    act.feedScroll.parent?.requestDisallowInterceptTouchEvent(false)
                }
                act.ptrPulling = false
            }
        }
        false
    }
}

internal fun startPtrLoading(act: MainActivity, maxPull: Int) {
    act.ptrLoading = true
    val h = act.ptrHeader.layoutParams as LinearLayout.LayoutParams
    h.height = maxPull
    act.ptrHeader.layoutParams = h
    act.ptrAnimator?.cancel()
    act.ptrAnimator = ObjectAnimator.ofFloat(act.ptrSpin, "rotation", act.ptrSpin.rotation, act.ptrSpin.rotation + 360f * 4f).apply {
        duration = 1200
        interpolator = LinearInterpolator()
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.RESTART
        start()
    }
    loadFeed(act) { collapsePtr(act) }
}

internal fun collapsePtr(act: MainActivity) {
    act.ptrAnimator?.cancel()
    act.ptrAnimator = null
    val h = act.ptrHeader.layoutParams as LinearLayout.LayoutParams
    h.height = 0
    act.ptrHeader.layoutParams = h
    act.ptrSpin.rotation = 0f
    act.ptrLoading = false
}

internal fun enterFeedMode(act: MainActivity) {
    act.authBlock.visibility = View.GONE
    act.feedBlock.visibility = View.VISIBLE
    migrateStats(act) { loadFeed(act) }
}

internal fun loadFeed(act: MainActivity, onDone: (() -> Unit)? = null) {
    act.feedGen += 1
    val gen = act.feedGen
    act.statsMap.clear()
    var postsSnap: DataSnapshot? = null
    var statsSnap: DataSnapshot? = null
    var done = false
    fun finish() {
        if (gen != act.feedGen) return
        if (done) return
        done = true
        if (statsSnap != null) for (s in statsSnap!!.children) s.key?.let { act.statsMap[it] = s }
        act.lastPosts.clear()
        if (postsSnap != null) for (c in postsSnap!!.children) act.lastPosts.add(c)
        renderFeed(act)
        onDone?.invoke()
    }
    act.database.child("posts").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { postsSnap = snapshot; finish() }
        override fun onCancelled(error: DatabaseError) {
            postsSnap = null
            dbDenied(act, error)
            finish()
        }
    })
    act.database.child("postStats").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { statsSnap = snapshot; finish() }
        override fun onCancelled(error: DatabaseError) {
            statsSnap = null
            dbDenied(act, error)
            finish()
        }
    })
}

internal fun renderFeed(act: MainActivity) {
    act.feedBox.removeAllViews()
    val q = act.feedQuery.lowercase()
    val sorted = act.lastPosts.sortedByDescending { getTs(it) }
    var shown = 0
    for (post in sorted) {
        if (shown >= act.FEED_LIMIT) break
        if (q.isNotEmpty()) {
            val text = (post.child("text").getValue(String::class.java) ?: "").lowercase()
            val nick = (post.child("authorNick").getValue(String::class.java) ?: "").lowercase()
            val qq = q.removePrefix("@")
            if (!text.contains(qq) && !nick.contains(qq)) continue
        }
        try {
            act.feedBox.addView(buildPostCard(act, post))
            shown++
        } catch (e: Exception) {
        }
    }
    if (shown == 0) {
        act.feedBox.addView(tv(act, if (q.isEmpty()) "Лента пуста" else "Ничего не найдено", 15f, act.fMed, P.textSec))
    }
}

internal fun buildPostCard(act: MainActivity, post: DataSnapshot, onViewsReady: ((TextView) -> Unit)? = null): View {
    val postId = post.key ?: ""
    val authorId = post.child("authorId").getValue(String::class.java) ?: ""
    val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
    val text = post.child("text").getValue(String::class.java) ?: ""
    val ts = getTs(post)
    val stats = act.statsMap[postId]
    val likeCount = (stats?.child("likes")?.value as? Number)?.toLong() ?: 0L
    val viewCount = (stats?.child("views")?.value as? Number)?.toLong() ?: 0L
    val liked = stats?.child("likedBy")?.child(act.currentUserId)?.value != null
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val imgW = act.resources.displayMetrics.widthPixels - act.dp(28)

    val card = LinearLayout(act)
    card.orientation = LinearLayout.VERTICAL
    card.background = surf(act, 18, filled = true)
    val cm2 = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
    cm2.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
    card.layoutParams = cm2

    val header = LinearLayout(act)
    header.orientation = LinearLayout.HORIZONTAL
    header.setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(10))
    header.gravity = Gravity.CENTER_VERTICAL
    val avatarSize = act.dp(40)
    val avatarImg = ImageView(act)
    val avParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
    avParams.marginEnd = act.dp(12)
    avatarImg.layoutParams = avParams
    avatarImg.scaleType = ImageView.ScaleType.FIT_CENTER
    if (act.avatarCache.containsKey(authorId)) {
        avatarImg.setImageBitmap(act.avatarCache[authorId])
    } else {
        avatarImg.setImageBitmap(makeCircularBitmap(createAvatarBitmap(act, authorNick), avatarSize))
        act.database.child("users").child(authorId).child("avatar")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(avatarSnapshot: DataSnapshot) {
                    val s = avatarSnapshot.getValue(String::class.java) ?: ""
                    if (s.isNotEmpty()) {
                        val b = decodeScaled(s, avatarSize)
                        if (b != null) {
                            val circ = makeCircularBitmap(b, avatarSize)
                            act.avatarCache[authorId] = circ
                            avatarImg.setImageBitmap(circ)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    avatarImg.setOnClickListener { openUserProfile(act, authorId, authorNick) }
    val nickTime = LinearLayout(act)
    nickTime.orientation = LinearLayout.VERTICAL
    val np = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    np.weight = 1f
    nickTime.layoutParams = np
    val nickView = tv(act, authorNick, 15f, act.fSemi, P.text)
    nickView.setOnClickListener { openUserProfile(act, authorId, authorNick) }
    val timeView = tv(act, timeStr, 12f, act.fReg, P.textSec)
    nickTime.addView(nickView)
    nickTime.addView(timeView)
    header.addView(avatarImg)
    header.addView(nickTime)
    card.addView(header)

    if (text.isNotEmpty()) {
        val bt = TextView(act)
        bt.typeface = act.fReg
        bt.textSize = 15f
        bt.setTextColor(P.text)
        bt.setPadding(act.dp(14), 0, act.dp(14), act.dp(12))
        bt.layoutParams = act.lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        Linkify.addLinks(bt, Linkify.WEB_URLS)
        bt.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        bt.setLinkTextColor(P.text)
        bt.text = text
        card.addView(bt)
    }

    val mediaContainer = LinearLayout(act)
    mediaContainer.orientation = LinearLayout.VERTICAL
    val mcLp = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
    mediaContainer.layoutParams = mcLp
    card.addView(mediaContainer)
    mediaFor(act, postId) { str ->
        try {
            fillMedia(act, mediaContainer, str, postId, imgW)
        } catch (e: Exception) {
        }
    }

    val actRow = LinearLayout(act)
    actRow.orientation = LinearLayout.HORIZONTAL
    actRow.setPadding(act.dp(14), act.dp(4), act.dp(14), act.dp(12))
    actRow.gravity = Gravity.CENTER_VERTICAL

    val heartIv = icon(act, R.drawable.ic_heart_outline, P.textSec, act.dp(22))
    val countTv = tv(act, "♥ " + fmtNum(likeCount), 14f, act.fSemi, P.text)
    val countLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    countLp.marginStart = act.dp(6)
    countTv.layoutParams = countLp
    val likeBox = LinearLayout(act)
    likeBox.orientation = LinearLayout.HORIZONTAL
    likeBox.gravity = Gravity.CENTER_VERTICAL
    likeBox.addView(heartIv)
    likeBox.addView(countTv)
    attachLike(act, likeBox, countTv, heartIv, postId, liked)
    actRow.addView(likeBox)

    val eyeIv = icon(act, R.drawable.ic_eye, P.textSec, act.dp(20))
    val viewsTv = tv(act, fmtNum(viewCount), 13f, act.fMed, P.textSec)
    val viewsTvLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    viewsTvLp.marginStart = act.dp(6)
    viewsTvLp.marginEnd = act.dp(6)
    viewsTv.layoutParams = viewsTvLp
    val viewsBox = LinearLayout(act)
    viewsBox.orientation = LinearLayout.HORIZONTAL
    viewsBox.gravity = Gravity.CENTER_VERTICAL
    val viewsBoxLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    viewsBoxLp.marginStart = act.dp(16)
    viewsBox.layoutParams = viewsBoxLp
    viewsBox.addView(eyeIv)
    viewsBox.addView(viewsTv)
    actRow.addView(viewsBox)

    if (authorId != act.currentUserId && authorId.isNotEmpty() && authorId !in act.myFollowingIds) {
        val fb = btnOutline(act, "+ @$authorNick")
        fb.textSize = 12f
        val fbLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        fbLp.marginStart = act.dp(12)
        fb.layoutParams = fbLp
        fb.setOnClickListener { followUser(act, authorId, authorNick); renderFeed(act) }
        actRow.addView(fb)
    }
    card.addView(actRow)
    onViewsReady?.invoke(viewsTv)
    return card
}

internal fun fillMedia(act: MainActivity, container: LinearLayout, imagesStr: String, postId: String, imgW: Int) {
    container.removeAllViews()
    if (imagesStr.isEmpty()) return
    val images = imagesStr.split(",").filter { it.isNotEmpty() }
    if (images.isEmpty()) return
    if (images.size == 1) {
        val b = decodeCached(act, images[0], imgW, "fb$postId")
        if (b != null) {
            val box = FrameLayout(act)
            val bm = LinearLayout.LayoutParams(-1, imgW)
            bm.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
            box.layoutParams = bm
            box.background = surf(act, 14, filled = true)
            box.clipToOutline = true
            val iv = ImageView(act)
            iv.setImageBitmap(b)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
            iv.setOnClickListener { openViewer(act, images, 0) }
            box.addView(iv)
            container.addView(box)
        }
    } else {
        val grid = LinearLayout(act)
        grid.orientation = LinearLayout.VERTICAL
        val gm = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
        gm.setMargins(act.dp(14), 0, act.dp(14), act.dp(12))
        grid.layoutParams = gm
        val cell = (imgW - act.dp(4)) / 2
        var ri = 0
        while (ri < images.size) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = act.lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, act.dp(4))
            for (ci in 0 until 2) {
                val idx = ri + ci
                if (idx < images.size) {
                    val b = decodeCached(act, images[idx], cell, "fg${postId}_$idx")
                    if (b != null) {
                        val box = FrameLayout(act)
                        val boxLp = LinearLayout.LayoutParams(0, cell)
                        boxLp.weight = 1f
                        if (ci == 0) boxLp.marginEnd = act.dp(4)
                        box.layoutParams = boxLp
                        box.background = surf(act, 12, filled = true)
                        box.clipToOutline = true
                        val iv = ImageView(act)
                        iv.setImageBitmap(b)
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                        val kk = idx
                        iv.setOnClickListener { openViewer(act, images, kk) }
                        box.addView(iv)
                        row.addView(box)
                    } else {
                        val empty = View(act)
                        val eLp = LinearLayout.LayoutParams(0, cell)
                        eLp.weight = 1f
                        if (ci == 0) eLp.marginEnd = act.dp(4)
                        empty.layoutParams = eLp
                        row.addView(empty)
                    }
                } else {
                    val empty = View(act)
                    val eLp = LinearLayout.LayoutParams(0, cell)
                    eLp.weight = 1f
                    if (ci == 0) eLp.marginEnd = act.dp(4)
                    empty.layoutParams = eLp
                    row.addView(empty)
                }
            }
            grid.addView(row)
            ri += 2
        }
        container.addView(grid)
    }
}

internal fun openPostDetail(act: MainActivity, post: DataSnapshot) {
    val postId = post.key ?: ""
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
    val title = tv(act, "Пост", 17f, act.fBold, P.text)
    val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
    titleLp.weight = 1f
    titleLp.marginStart = act.dp(12)
    title.layoutParams = titleLp
    bar.addView(backIv)
    bar.addView(title)
    outer.addView(bar)

    val scroll = ScrollView(act)
    scroll.isVerticalScrollBarEnabled = false
    val holder = LinearLayout(act)
    holder.orientation = LinearLayout.VERTICAL
    holder.setPadding(0, act.dp(6), 0, act.dp(20))
    try {
        holder.addView(buildPostCard(act, post) { vt ->
            incrementViews(act, postId) { nv -> vt.text = fmtNum(nv) }
        })
    } catch (e: Exception) {
        holder.addView(tv(act, "Не удалось загрузить пост", 14f, act.fMed, P.textSec))
    }
    scroll.addView(holder)
    val slp = LinearLayout.LayoutParams(-1, 0)
    slp.weight = 1f
    outer.addView(scroll, slp)

    dlg.setContentView(outer)
    dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    dlg.show()
}

internal fun openViewer(act: MainActivity, images: List<String>, start: Int) {
    if (images.isEmpty()) return
    val dlg = Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val frame = FrameLayout(act)
    frame.setBackgroundColor(0xFF000000.toInt())
    val img = ImageView(act)
    img.scaleType = ImageView.ScaleType.MATRIX
    frame.addView(img, FrameLayout.LayoutParams(-1, -1))

    val back = TextView(act)
    back.text = "←"
    back.setTextColor(0xFFFFFFFF.toInt())
    back.textSize = 28f
    back.typeface = act.fBold
    back.setPadding(act.dp(16), act.dp(10), act.dp(16), act.dp(10))
    back.setBackgroundColor(P.scrim)
    val blp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    blp.gravity = Gravity.TOP or Gravity.START
    frame.addView(back, blp)
    back.setOnClickListener { dlg.dismiss() }

    val bar = LinearLayout(act)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.setBackgroundColor(P.scrim)
    val prevB = Button(act)
    prevB.text = "◀"
    prevB.setTextColor(0xFFFFFFFF.toInt())
    prevB.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(prevB, 1f)
    val cnt = TextView(act)
    cnt.setTextColor(0xFFFFFFFF.toInt())
    cnt.gravity = Gravity.CENTER
    cnt.typeface = act.fSemi
    cnt.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(cnt, 2f)
    val nextB = Button(act)
    nextB.text = "▶"
    nextB.setTextColor(0xFFFFFFFF.toInt())
    nextB.layoutParams = act.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
    act.wt(nextB, 1f)
    bar.addView(prevB)
    bar.addView(cnt)
    bar.addView(nextB)
    val brp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    brp.gravity = Gravity.BOTTOM
    frame.addView(bar, brp)

    dlg.setContentView(frame)
    dlg.show()

    var idx = start.coerceIn(0, images.size - 1)
    var scale = 1f
    var minS = 1f
    val maxS = act.ZOOM_MAX
    var panX = 0f
    var panY = 0f
    var bmp: Bitmap? = null

    fun clampPan(vw: Int, vh: Int) {
        val b = bmp ?: return
        val mx = maxOf(0f, (b.width * scale - vw) / 2f)
        val my = maxOf(0f, (b.height * scale - vh) / 2f)
        panX = panX.coerceIn(-mx, mx)
        panY = panY.coerceIn(-my, my)
    }

    fun applyM() {
        val b = bmp ?: return
        val vw = img.width
        val vh = img.height
        if (vw == 0 || vh == 0) return
        clampPan(vw, vh)
        val m = android.graphics.Matrix()
        m.postScale(scale, scale)
        m.postTranslate((vw - b.width * scale) / 2f + panX, (vh - b.height * scale) / 2f + panY)
        img.imageMatrix = m
    }

    fun setup(b: Bitmap) {
        bmp = b
        val vw = img.width
        val vh = img.height
        if (vw == 0 || vh == 0) {
            img.post { setup(b) }
            return
        }
        val fit = minOf(vw.toFloat() / b.width, vh.toFloat() / b.height)
        minS = minOf(fit, 1f)
        scale = minOf(fit, 1f)
        panX = 0f
        panY = 0f
        applyM()
    }

    fun show() {
        val req = maxOf(act.resources.displayMetrics.widthPixels, act.resources.displayMetrics.heightPixels)
        val b = decodeScaled(images[idx], req)
        if (b != null) {
            img.setImageBitmap(b)
            img.post { setup(b) }
        }
        cnt.text = "${idx + 1} / ${images.size}"
        prevB.isEnabled = idx > 0
        nextB.isEnabled = idx < images.size - 1
    }

    prevB.setOnClickListener {
        if (idx > 0) {
            idx--
            show()
        }
    }
    nextB.setOnClickListener {
        if (idx < images.size - 1) {
            idx++
            show()
        }
    }

    val sgd = ScaleGestureDetector(act, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            scale = (scale * d.scaleFactor).coerceIn(minS, maxS)
            applyM()
            return true
        }
    })

    val gd = GestureDetector(act, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale > minS + 0.001f) {
                panX -= dx
                panY -= dy
                applyM()
            }
            return true
        }
    })

    img.setOnTouchListener { _, ev ->
        sgd.onTouchEvent(ev)
        gd.onTouchEvent(ev)
        true
    }
    show()
}
