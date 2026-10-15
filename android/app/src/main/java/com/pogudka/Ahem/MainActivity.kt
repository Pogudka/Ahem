package com.pogudka.Ahem

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Bundle
import android.provider.MediaStore
import android.text.util.Linkify
import android.util.Base64
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    
    private lateinit var tabs: Array<View>
    private lateinit var tabBtns: Array<Button>
    
    // ЛЕНТА
    private lateinit var authBlock: LinearLayout
    private lateinit var feedBlock: LinearLayout
    private lateinit var feedHeader: TextView
    private lateinit var feedEmail: EditText
    private lateinit var feedNick: EditText
    private lateinit var feedPass: EditText
    private lateinit var feedStatus: TextView
    private lateinit var pendingWrap: LinearLayout
    private lateinit var pendingInfo: TextView
    private lateinit var pendingRow: LinearLayout
    private lateinit var feedPostText: EditText
    private lateinit var feedFollowNick: EditText
    private lateinit var feedBox: LinearLayout
    private lateinit var feedScroll: ScrollView
    private lateinit var regBtn: Button
    private lateinit var logBtn: Button
    private var currentUserId = ""
    private var currentNick = ""
    private var currentAvatar = ""
    private val pendingImages = ArrayList<String>()
    private var isRegistering = false
    private var isLoggingIn = false
    private var lastFeedSig = ""
    
    // ЧАТЫ
    private lateinit var chatListRoot: LinearLayout
    private lateinit var convRoot: LinearLayout
    private lateinit var chatListRows: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var chatStatus: TextView
    private lateinit var convTitle: TextView
    private lateinit var convBox: LinearLayout
    private lateinit var convScroll: ScrollView
    private lateinit var msgField: EditText
    private var currentChat = ""
    private val knownNames = LinkedHashSet<String>()
    
    // ПРОФИЛЬ
    private lateinit var profileRoot: LinearLayout
    private lateinit var profileAvatar: ImageView
    private lateinit var profileNick: TextView
    private lateinit var profilePostsCount: TextView
    private lateinit var profileFollowersCount: TextView
    private lateinit var profileLikesCount: TextView
    private lateinit var profileBio: TextView
    private lateinit var profileEditBtn: Button
    private lateinit var profilePostsGrid: LinearLayout
    private lateinit var profileScroll: ScrollView
    private var viewingUserId = ""
    private var viewingNick = ""
    private var editAvatarView: ImageView? = null
    
    private val PICK_IMAGE = 1001
    private val PICK_IMAGES = 1002
    private val PICK_AVATAR = 1003
    private val ZOOM_MAX = 3f
    
    private var feedListener: ValueEventListener? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    
    // Кэш аватарок
    private val avatarCache = HashMap<String, Bitmap>()
    
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }
    private fun wt(v: View, w: Float) { 
        (v.layoutParams as LinearLayout.LayoutParams).weight = w 
    }
    private fun me() = currentNick.ifEmpty { "Аноним" }
    private fun addKnown(n: String) { if (n.isNotEmpty() && n != me()) knownNames.add(n) }

    // Круглая аватарка: центр-кроп в квадрат -> масштаб -> круг с отступом 1px
    private fun makeCircularBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val s = minOf(source.width, source.height)
        val sq = Bitmap.createBitmap(source, (source.width - s) / 2, (source.height - s) / 2, s, s)
        val scaled = Bitmap.createScaledBitmap(sq, targetSize, targetSize, true)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f - 1f, paint)
        return out
    }
    
    // Лайк через транзакцию: 1 лайк на пользователя, переключение без багов
    private fun attachLike(btn: Button, postId: String) {
        btn.setOnClickListener {
            if (currentUserId.isEmpty()) return@setOnClickListener
            val ref = database.child("posts").child(postId)
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val likes = (mutableData.child("likes").value as? Number)?.toInt() ?: 0
                    val likedBy = mutableData.child("likedBy")
                    val has = likedBy.child(currentUserId).exists()
                    if (has) {
                        likedBy.child(currentUserId).value = null
                        mutableData.child("likes").value = maxOf(0, likes - 1)
                    } else {
                        likedBy.child(currentUserId).value = true
                        mutableData.child("likes").value = likes + 1
                    }
                    return Transaction.success(mutableData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                    if (committed && data != null) {
                        val newCount = (data.child("likes").value as? Number)?.toInt() ?: 0
                        btn.text = "♥ $newCount"
                    }
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        
        val content = FrameLayout(this)
        content.layoutParams = lp(-1, 0, 0)
        wt(content, 1f)
        
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        
        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChats()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))
        
        val titles = arrayOf("📰 Лента", "🎬 Видео", "💬 Чаты", "👤 Профиль")
        tabBtns = Array(4) { i ->
            val b = Button(this)
            b.text = titles[i]
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            b.setOnClickListener { showTab(i) }
            b
        }
        for (b in tabBtns) panel.addView(b)
        
        root.addView(content)
        root.addView(panel)
        setContentView(root)
        showTab(0)
        
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && currentUserId.isEmpty()) {
                currentUserId = user.uid
                viewingUserId = currentUserId
                viewingNick = ""
                database.child("users").child(currentUserId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.child("nick").getValue(String::class.java) ?: "Аноним"
                            currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                            if (::feedBlock.isInitialized && feedBlock.visibility == View.GONE) {
                                enterFeedMode()
                            }
                            if (::profileRoot.isInitialized) refreshProfile()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else if (user == null && currentUserId.isNotEmpty()) {
                currentUserId = ""
                currentNick = ""
                currentAvatar = ""
                if (::feedBlock.isInitialized) {
                    feedBlock.visibility = View.GONE
                    authBlock.visibility = View.VISIBLE
                    feedStatus.text = "не залогинен"
                }
            }
        }
        auth.addAuthStateListener(authListener!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        authListener?.let { auth.removeAuthStateListener(it) }
        feedListener?.let { database.child("posts").removeEventListener(it) }
    }
    
    private fun showTab(i: Int) {
        for (idx in tabs.indices) tabs[idx].visibility = if (idx == i) View.VISIBLE else View.GONE
        for (idx in tabBtns.indices) tabBtns[idx].setBackgroundColor(
            if (idx == i) 0xFF222222.toInt() else 0xFFCCCCCC.toInt()
        )
        if (i == 3) {
            viewingUserId = currentUserId
            viewingNick = ""
            refreshProfile()
        }
    }

    // ================= ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        authBlock = LinearLayout(this)
        authBlock.orientation = LinearLayout.VERTICAL
        
        feedEmail = EditText(this)
        feedEmail.hint = "Email"
        
        feedNick = EditText(this)
        feedNick.hint = "Ник"
        
        feedPass = EditText(this)
        feedPass.hint = "Пароль"
        feedPass.inputType = 1
        
        val rowAuth = LinearLayout(this)
        rowAuth.orientation = LinearLayout.HORIZONTAL
        
        regBtn = Button(this)
        regBtn.text = "Регистрация"
        regBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(regBtn, 1f)
        
        logBtn = Button(this)
        logBtn.text = "Вход"
        logBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(logBtn, 1f)
        
        rowAuth.addView(regBtn)
        rowAuth.addView(logBtn)
        
        feedStatus = TextView(this)
        feedStatus.text = "не залогинен"
        
        authBlock.addView(feedEmail)
        authBlock.addView(feedNick)
        authBlock.addView(feedPass)
        authBlock.addView(rowAuth)
        authBlock.addView(feedStatus)
        
        feedBlock = LinearLayout(this)
        feedBlock.orientation = LinearLayout.VERTICAL
        feedBlock.visibility = View.GONE
        
        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        
        feedHeader = TextView(this)
        feedHeader.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedHeader, 1f)
        
        val switchBtn = Button(this)
        switchBtn.text = "сменить"
        switchBtn.layoutParams = lp(-2, -2, 0)
        
        headerRow.addView(feedHeader)
        headerRow.addView(switchBtn)
        
        pendingWrap = LinearLayout(this)
        pendingWrap.orientation = LinearLayout.VERTICAL
        pendingWrap.visibility = View.GONE
        
        val pendHead = LinearLayout(this)
        pendHead.orientation = LinearLayout.HORIZONTAL
        
        pendingInfo = TextView(this)
        pendingInfo.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pendingInfo, 1f)
        
        val clearBtn = Button(this)
        clearBtn.text = "✖"
        clearBtn.layoutParams = lp(-2, -2, 0)
        
        pendHead.addView(pendingInfo)
        pendHead.addView(clearBtn)
        
        val pendScroll = HorizontalScrollView(this)
        pendScroll.layoutParams = lp(-1, -2, 0)
        pendScroll.isHorizontalScrollBarEnabled = false
        
        pendingRow = LinearLayout(this)
        pendingRow.orientation = LinearLayout.HORIZONTAL
        
        pendScroll.addView(pendingRow)
        pendingWrap.addView(pendHead)
        pendingWrap.addView(pendScroll)
        
        feedPostText = EditText(this)
        feedPostText.hint = "Подпись к посту (до 4 фото, ссылки кликабельны)"
        
        val rowPost = LinearLayout(this)
        rowPost.orientation = LinearLayout.HORIZONTAL
        
        val pubBtn = Button(this)
        pubBtn.text = "Опубликовать"
        pubBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pubBtn, 2f)
        
        val phBtn = Button(this)
        phBtn.text = "📷 несколько"
        phBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(phBtn, 1f)
        
        rowPost.addView(pubBtn)
        rowPost.addView(phBtn)
        
        val rowAct = LinearLayout(this)
        rowAct.orientation = LinearLayout.HORIZONTAL
        
        val feedBtn = Button(this)
        feedBtn.text = "⟳ Лента"
        feedBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedBtn, 1f)
        
        feedFollowNick = EditText(this)
        feedFollowNick.hint = "@ник подписки"
        feedFollowNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedFollowNick, 2f)
        
        val folBtn = Button(this)
        folBtn.text = "Подписаться"
        folBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(folBtn, 1f)
        
        rowAct.addView(feedBtn)
        rowAct.addView(feedFollowNick)
        rowAct.addView(folBtn)
        
        feedBox = LinearLayout(this)
        feedBox.orientation = LinearLayout.VERTICAL
        
        feedScroll = ScrollView(this)
        feedScroll.addView(feedBox)
        feedScroll.layoutParams = lp(-1, 0, 0)
        wt(feedScroll, 1f)
        feedScroll.isVerticalScrollBarEnabled = false
        
        feedBlock.addView(headerRow)
        feedBlock.addView(pendingWrap)
        feedBlock.addView(feedPostText)
        feedBlock.addView(rowPost)
        feedBlock.addView(rowAct)
        feedBlock.addView(feedScroll)
        
        regBtn.setOnClickListener {
            if (isRegistering) return@setOnClickListener
            val email = feedEmail.text.toString()
            val nick = feedNick.text.toString()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) { 
                feedStatus.text = "Заполни все поля"
                return@setOnClickListener 
            }
            isRegistering = true
            regBtn.isEnabled = false
            regBtn.text = "..."
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    database.child("users").child(userId).child("email").setValue(email)
                    database.child("users").child(userId).child("nick").setValue(nick)
                    currentUserId = userId
                    currentNick = nick
                    viewingUserId = currentUserId
                    feedStatus.text = "Зарегистрирован!"
                    enterFeedMode()
                } else { 
                    feedStatus.text = "Ошибка: ${task.exception?.message}" 
                }
                isRegistering = false
                regBtn.isEnabled = true
                regBtn.text = "Регистрация"
            }
        }
        
        logBtn.setOnClickListener {
            if (isLoggingIn) return@setOnClickListener
            val email = feedEmail.text.toString()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || pass.isEmpty()) { 
                feedStatus.text = "Email + пароль"
                return@setOnClickListener 
            }
            isLoggingIn = true
            logBtn.isEnabled = false
            logBtn.text = "..."
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    currentUserId = userId
                    viewingUserId = currentUserId
                    database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.child("nick").getValue(String::class.java) ?: "Аноним"
                            currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                            enterFeedMode()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                } else { 
                    feedStatus.text = "Ошибка: ${task.exception?.message}" 
                }
                isLoggingIn = false
                logBtn.isEnabled = true
                logBtn.text = "Вход"
            }
        }
        
        switchBtn.setOnClickListener {
            auth.signOut()
            feedBlock.visibility = View.GONE
            authBlock.visibility = View.VISIBLE
            feedStatus.text = "не залогинен"
            currentUserId = ""
            currentNick = ""
            currentAvatar = ""
        }
        
        clearBtn.setOnClickListener { 
            pendingImages.clear()
            pendingWrap.visibility = View.GONE 
        }
        
        phBtn.setOnClickListener { 
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(Intent.createChooser(intent, "Выбери фото"), PICK_IMAGES) 
        }
        
        pubBtn.setOnClickListener {
            val text = feedPostText.text.toString()
            if (text.isEmpty() && pendingImages.isEmpty()) return@setOnClickListener
            val postId = database.child("posts").push().key ?: return@setOnClickListener
            val post = HashMap<String, Any>()
            post["authorId"] = currentUserId
            post["authorNick"] = currentNick
            post["text"] = text
            post["images"] = pendingImages.joinToString(",")
            post["timestamp"] = System.currentTimeMillis()
            post["likes"] = 0
            post["likedBy"] = HashMap<String, Boolean>()
            database.child("posts").child(postId).setValue(post)
            feedPostText.setText("")
            pendingImages.clear()
            pendingWrap.visibility = View.GONE
        }
        
        feedBtn.setOnClickListener { loadFeed() }
        folBtn.setOnClickListener {
            val n = feedFollowNick.text.toString()
            if (n.isNotEmpty()) { 
                addKnown(n)
                feedFollowNick.setText("") 
            }
        }
        
        col.addView(authBlock)
        col.addView(feedBlock)
        return col
    }
    
    private fun enterFeedMode() {
        authBlock.visibility = View.GONE
        feedBlock.visibility = View.VISIBLE
        feedHeader.text = "@$currentNick"
        loadFeed()
    }
    
    private fun loadFeed() {
        feedListener?.let { database.child("posts").removeEventListener(it) }
        feedBox.removeAllViews()
        lastFeedSig = ""
        
        feedListener = database.child("posts").orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = ArrayList<DataSnapshot>()
                for (child in snapshot.children) posts.add(child)
                
                // Сигнатура ленты БЕЗ лайков: если изменились только лайки - не перерисовываем
                val sb = StringBuilder()
                for (p in posts) {
                    sb.append(p.key).append('~')
                      .append(p.child("timestamp").getValue(Long::class.java) ?: 0).append('~')
                      .append(p.child("text").getValue(String::class.java) ?: "").append('~')
                      .append(p.child("images").getValue(String::class.java) ?: "").append(';')
                }
                val sig = sb.toString()
                if (sig == lastFeedSig) return
                lastFeedSig = sig
                
                feedBox.removeAllViews()
                for (post in posts.reversed()) {
                    val postId = post.key ?: ""
                    val authorId = post.child("authorId").getValue(String::class.java) ?: ""
                    val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
                    val text = post.child("text").getValue(String::class.java) ?: ""
                    val imagesStr = post.child("images").getValue(String::class.java) ?: ""
                    val ts = post.child("timestamp").getValue(Long::class.java) ?: 0
                    val likeCount = (post.child("likes").value as? Number)?.toInt() ?: 0
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                    
                    val card = LinearLayout(this@MainActivity)
                    card.orientation = LinearLayout.VERTICAL
                    card.setPadding(0, 0, 0, 12)
                    card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
                    card.setBackgroundColor(0xFFFFFFFF.toInt())
                    card.elevation = 2f
                    
                    // ШАПКА ПОСТА
                    val header = LinearLayout(this@MainActivity)
                    header.orientation = LinearLayout.HORIZONTAL
                    header.setPadding(12, 14, 12, 12)
                    header.gravity = Gravity.CENTER_VERTICAL
                    
                    val avatarView = ImageView(this@MainActivity)
                    val avatarSize = dp(40)
                    val avParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                    avParams.marginEnd = 12
                    avatarView.layoutParams = avParams
                    avatarView.scaleType = ImageView.ScaleType.FIT_CENTER
                    
                    if (avatarCache.containsKey(authorId)) {
                        avatarView.setImageBitmap(avatarCache[authorId])
                    } else {
                        avatarView.setImageBitmap(makeCircularBitmap(createAvatarBitmap(authorNick), avatarSize))
                        database.child("users").child(authorId).child("avatar")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(avatarSnapshot: DataSnapshot) {
                                    val authorAvatar = avatarSnapshot.getValue(String::class.java) ?: ""
                                    if (authorAvatar.isNotEmpty()) {
                                        try {
                                            val bytes = Base64.decode(authorAvatar, Base64.NO_WRAP)
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            val circular = makeCircularBitmap(bmp, avatarSize)
                                            avatarCache[authorId] = circular
                                            avatarView.setImageBitmap(circular)
                                        } catch (e: Exception) {}
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
                    
                    avatarView.setOnClickListener { openUserProfile(authorId, authorNick) }
                    
                    val nickTimeLayout = LinearLayout(this@MainActivity)
                    nickTimeLayout.orientation = LinearLayout.VERTICAL
                    val nickParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    nickParams.weight = 1f
                    nickTimeLayout.layoutParams = nickParams
                    
                    val nickView = TextView(this@MainActivity)
                    nickView.text = authorNick
                    nickView.textSize = 15f
                    nickView.setTextColor(0xFF000000.toInt())
                    nickView.setOnClickListener { openUserProfile(authorId, authorNick) }
                    
                    val timeView = TextView(this@MainActivity)
                    timeView.text = timeStr
                    timeView.textSize = 12f
                    timeView.setTextColor(0xFF888888.toInt())
                    
                    nickTimeLayout.addView(nickView)
                    nickTimeLayout.addView(timeView)
                    
                    header.addView(avatarView)
                    header.addView(nickTimeLayout)
                    card.addView(header)
                    
                    // ТЕКСТ ПОСТА С КЛИКАБЕЛЬНЫМИ ССЫЛКАМИ
                    if (text.isNotEmpty()) {
                        val bt = TextView(this@MainActivity)
                        bt.text = text
                        bt.setPadding(12, 0, 12, 12)
                        bt.textSize = 14f
                        bt.setTextColor(0xFF000000.toInt())
                        bt.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                        Linkify.addLinks(bt, Linkify.WEB_URLS)
                        bt.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                        card.addView(bt)
                    }
                    
                    // ФОТО (сетка)
                    if (imagesStr.isNotEmpty()) {
                        val images = imagesStr.split(",").filter { it.isNotEmpty() }
                        val photoCount = images.size
                        
                        if (photoCount == 1) {
                            try {
                                val bytes = Base64.decode(images[0], Base64.NO_WRAP)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val box = FrameLayout(this@MainActivity)
                                box.layoutParams = lp(-1, dp(250), 0)
                                box.setBackgroundColor(0xFFFAFAFA.toInt())
                                val iv = ImageView(this@MainActivity)
                                iv.setImageBitmap(bmp)
                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                iv.setOnClickListener { openViewer(images, 0) }
                                box.addView(iv)
                                card.addView(box)
                            } catch (e: Exception) {}
                        } else if (photoCount == 2) {
                            val row = LinearLayout(this@MainActivity)
                            row.orientation = LinearLayout.HORIZONTAL
                            row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                            for (i in images.indices) {
                                try {
                                    val bytes = Base64.decode(images[i], Base64.NO_WRAP)
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    val box = FrameLayout(this@MainActivity)
                                    box.layoutParams = lp(0, dp(180), 2)
                                    wt(box, 1f)
                                    box.setBackgroundColor(0xFFFAFAFA.toInt())
                                    val iv = ImageView(this@MainActivity)
                                    iv.setImageBitmap(bmp)
                                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                    iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                    val idx = i
                                    iv.setOnClickListener { openViewer(images, idx) }
                                    box.addView(iv)
                                    row.addView(box)
                                } catch (e: Exception) {}
                            }
                            card.addView(row)
                        } else {
                            val grid = LinearLayout(this@MainActivity)
                            grid.orientation = LinearLayout.VERTICAL
                            grid.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                            var rowIdx = 0
                            while (rowIdx < images.size) {
                                val row = LinearLayout(this@MainActivity)
                                row.orientation = LinearLayout.HORIZONTAL
                                row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 2)
                                for (colIdx in 0 until 2) {
                                    val imgIdx = rowIdx + colIdx
                                    if (imgIdx < images.size) {
                                        try {
                                            val bytes = Base64.decode(images[imgIdx], Base64.NO_WRAP)
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            val box = FrameLayout(this@MainActivity)
                                            box.layoutParams = lp(0, dp(180), 2)
                                            wt(box, 1f)
                                            box.setBackgroundColor(0xFFFAFAFA.toInt())
                                            val iv = ImageView(this@MainActivity)
                                            iv.setImageBitmap(bmp)
                                            iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                            iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                            val idx = imgIdx
                                            iv.setOnClickListener { openViewer(images, idx) }
                                            box.addView(iv)
                                            row.addView(box)
                                        } catch (e: Exception) {
                                            val box = FrameLayout(this@MainActivity)
                                            box.layoutParams = lp(0, dp(180), 2)
                                            wt(box, 1f)
                                            box.setBackgroundColor(0xFFFAFAFA.toInt())
                                            row.addView(box)
                                        }
                                    } else {
                                        val box = FrameLayout(this@MainActivity)
                                        box.layoutParams = lp(0, dp(180), 2)
                                        wt(box, 1f)
                                        box.setBackgroundColor(0xFFFAFAFA.toInt())
                                        row.addView(box)
                                    }
                                }
                                grid.addView(row)
                                rowIdx += 2
                            }
                            card.addView(grid)
                        }
                    }
                    
                    // КНОПКИ
                    val row = LinearLayout(this@MainActivity)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(12, 8, 12, 8)
                    
                    val likeBtn = Button(this@MainActivity)
                    likeBtn.text = "♥ $likeCount"
                    likeBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                    wt(likeBtn, 1f)
                    attachLike(likeBtn, postId)
                    row.addView(likeBtn)
                    
                    if (authorId != currentUserId) {
                        val fb = Button(this@MainActivity)
                        fb.text = "+ @$authorNick"
                        fb.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                        wt(fb, 2f)
                        fb.setOnClickListener { addKnown(authorNick) }
                        row.addView(fb)
                    }
                    card.addView(row)
                    feedBox.addView(card)
                }
                if (posts.isEmpty()) feedBox.addView(TextView(this@MainActivity).apply { text = "Лента пуста" })
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun openUserProfile(userId: String, nick: String) {
        viewingUserId = userId
        viewingNick = nick
        showTab(3)
    }
    
    private fun openViewer(images: List<String>, start: Int) {
        if (images.isEmpty()) return
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this)
        frame.setBackgroundColor(0xFF000000.toInt())
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.MATRIX
        frame.addView(img, FrameLayout.LayoutParams(-1, -1))
        
        val back = TextView(this)
        back.text = "←"
        back.setTextColor(0xFFFFFFFF.toInt())
        back.textSize = 28f
        back.setPadding(dp(16), dp(10), dp(16), dp(10))
        back.setBackgroundColor(0x66000000)
        
        val blp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        blp.gravity = Gravity.TOP or Gravity.START
        frame.addView(back, blp)
        back.setOnClickListener { dlg.dismiss() }
        
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(0x66000000)
        
        val prevB = Button(this)
        prevB.text = "◀"
        prevB.setTextColor(0xFFFFFFFF.toInt())
        prevB.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(prevB, 1f)
        
        val cnt = TextView(this)
        cnt.setTextColor(0xFFFFFFFF.toInt())
        cnt.gravity = Gravity.CENTER
        cnt.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(cnt, 2f)
        
        val nextB = Button(this)
        nextB.text = "▶"
        nextB.setTextColor(0xFFFFFFFF.toInt())
        nextB.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(nextB, 1f)
        
        bar.addView(prevB)
        bar.addView(cnt)
        bar.addView(nextB)
        
        val brp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        brp.gravity = Gravity.BOTTOM
        frame.addView(bar, brp)
        
        dlg.setContentView(frame)
        dlg.show()
        
        var idx = start.coerceIn(0, images.size - 1)
        var bmp: Bitmap? = null
        var scale = 1f
        var minS = 1f
        var maxS = ZOOM_MAX
        var panX = 0f
        var panY = 0f
        
        fun clampPan(vw: Int, vh: Int) {
            if (bmp == null) return
            val bw = bmp!!.width * scale
            val bh = bmp!!.height * scale
            val mx = maxOf(0f, (bw - vw) / 2f)
            val my = maxOf(0f, (bh - vh) / 2f)
            panX = panX.coerceIn(-mx, mx)
            panY = panY.coerceIn(-my, my)
        }
        
        fun applyMatrix() {
            val b = bmp ?: return
            val vw = img.width
            val vh = img.height
            if (vw == 0 || vh == 0) return
            clampPan(vw, vh)
            val m = Matrix()
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
            maxS = ZOOM_MAX
            scale = minOf(fit, 1f)
            panX = 0f
            panY = 0f
            applyMatrix()
        }
        
        fun show() {
            try {
                val bytes = Base64.decode(images[idx], Base64.NO_WRAP)
                val bb = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                img.setImageBitmap(bb)
                img.post { setup(bb) }
            } catch (e: Exception) {}
            cnt.text = "${idx + 1} / ${images.size}"
            prevB.isEnabled = idx > 0
            nextB.isEnabled = idx < images.size - 1
        }
        
        prevB.setOnClickListener { if (idx > 0) { idx--; show() } }
        nextB.setOnClickListener { if (idx < images.size - 1) { idx++; show() } }
        
        val sgd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(minS, maxS)
                applyMatrix()
                return true
            }
        })
        
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scale > minS + 0.001f) { 
                    panX -= dx
                    panY -= dy
                    applyMatrix() 
                }
                return true
            }
        })
        
        img.setOnTouchListener { _, ev -> sgd.onTouchEvent(ev); gd.onTouchEvent(ev); true }
        show()
    }

    // ================= ЧАТЫ =================
    private fun buildChats(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        chatListRoot = LinearLayout(this)
        chatListRoot.orientation = LinearLayout.VERTICAL
        
        chatStatus = TextView(this)
        chatStatus.text = "Firebase подключен"
        
        val rowSearch = LinearLayout(this)
        rowSearch.orientation = LinearLayout.HORIZONTAL
        
        searchField = EditText(this)
        searchField.hint = "Поиск по нику"
        searchField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchField, 2f)
        
        val searchBtn = Button(this)
        searchBtn.text = "🔍"
        searchBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchBtn, 1f)
        
        rowSearch.addView(searchField)
        rowSearch.addView(searchBtn)
        
        searchResults = LinearLayout(this)
        searchResults.orientation = LinearLayout.VERTICAL
        
        chatListRows = LinearLayout(this)
        chatListRows.orientation = LinearLayout.VERTICAL
        
        val listScroll = ScrollView(this)
        listScroll.addView(chatListRows)
        listScroll.layoutParams = lp(-1, 0, 0)
        wt(listScroll, 1f)
        listScroll.isVerticalScrollBarEnabled = false
        
        chatListRoot.addView(chatStatus)
        chatListRoot.addView(rowSearch)
        chatListRoot.addView(searchResults)
        chatListRoot.addView(listScroll)
        
        convRoot = LinearLayout(this)
        convRoot.orientation = LinearLayout.VERTICAL
        convRoot.visibility = View.GONE
        
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        
        val backBtn = Button(this)
        backBtn.text = "←"
        backBtn.layoutParams = lp(-2, -2, 0)
        
        convTitle = TextView(this)
        convTitle.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(convTitle, 1f)
        convTitle.textSize = 18f
        
        top.addView(backBtn)
        top.addView(convTitle)
        
        convBox = LinearLayout(this)
        convBox.orientation = LinearLayout.VERTICAL
        
        convScroll = ScrollView(this)
        convScroll.addView(convBox)
        convScroll.layoutParams = lp(-1, 0, 0)
        wt(convScroll, 1f)
        convScroll.isVerticalScrollBarEnabled = false
        
        msgField = EditText(this)
        msgField.hint = "Сообщение..."
        
        val sendBtn = Button(this)
        sendBtn.text = "Отправить"
        sendBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        convRoot.addView(top)
        convRoot.addView(convScroll)
        convRoot.addView(msgField)
        convRoot.addView(sendBtn)
        
        searchBtn.setOnClickListener {
            val query = searchField.text.toString().trim().lowercase()
            if (query.isEmpty()) return@setOnClickListener
            searchResults.removeAllViews()
            database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (user in snapshot.children) {
                        val nick = user.child("nick").getValue(String::class.java) ?: ""
                        if (nick.lowercase().contains(query) && user.key != currentUserId) {
                            val btn = Button(this@MainActivity)
                            btn.text = "✉ $nick"
                            btn.layoutParams = lp(-1, -2, 4)
                            btn.setOnClickListener { openChat(user.key ?: "", nick) }
                            searchResults.addView(btn)
                        }
                    }
                    if (searchResults.childCount == 0) {
                        searchResults.addView(TextView(this@MainActivity).apply { text = "Никого не нашли" })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
        
        backBtn.setOnClickListener { 
            currentChat = ""
            convRoot.visibility = View.GONE
            chatListRoot.visibility = View.VISIBLE 
        }
        
        sendBtn.setOnClickListener {
            val text = msgField.text.toString()
            if (text.isEmpty() || currentChat.isEmpty()) return@setOnClickListener
            val msgId = database.child("chats").child(currentChat).child("messages").push().key ?: return@setOnClickListener
            val msg = HashMap<String, Any>()
            msg["senderId"] = currentUserId
            msg["senderNick"] = currentNick
            msg["text"] = text
            msg["timestamp"] = System.currentTimeMillis()
            database.child("chats").child(currentChat).child("messages").child(msgId).setValue(msg)
            msgField.setText("")
        }
        
        col.addView(chatListRoot)
        col.addView(convRoot)
        return col
    }
    
    private fun openChat(otherUserId: String, otherNick: String) {
        currentChat = if (currentUserId < otherUserId) "${currentUserId}_$otherUserId" else "${otherUserId}_$currentUserId"
        convTitle.text = otherNick
        chatListRoot.visibility = View.GONE
        convRoot.visibility = View.VISIBLE
        convBox.removeAllViews()
        loadMessages()
    }
    
    private fun loadMessages() {
        database.child("chats").child(currentChat).child("messages").orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    convBox.removeAllViews()
                    for (msg in snapshot.children) {
                        val senderNick = msg.child("senderNick").getValue(String::class.java) ?: "?"
                        val text = msg.child("text").getValue(String::class.java) ?: ""
                        val isMe = msg.child("senderId").getValue(String::class.java) == currentUserId
                        
                        val tv = TextView(this@MainActivity)
                        tv.text = if (isMe) "→ $text" else "← $senderNick: $text"
                        tv.setPadding(0, 4, 0, 4)
                        tv.setTextColor(if (isMe) 0xFF004400.toInt() else 0xFF000044.toInt())
                        convBox.addView(tv)
                    }
                    convScroll.post { convScroll.fullScroll(View.FOCUS_DOWN) }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ================= ВИДЕО =================
    private fun buildVideo(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        val tv = TextView(this)
        tv.text = "🎬 Видео\n\nЗдесь появится лента клипов.\nСкоро."
        col.addView(tv)
        return col
    }
    
    // ================= ПРОФИЛЬ =================
    private fun buildProfile(): View {
        profileRoot = LinearLayout(this)
        profileRoot.orientation = LinearLayout.VERTICAL
        
        profileScroll = ScrollView(this)
        profileScroll.layoutParams = lp(-1, -1)
        profileScroll.isVerticalScrollBarEnabled = false
        
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(16, 16, 16, 16)
        
        profileAvatar = ImageView(this)
        profileAvatar.layoutParams = lp(dp(100), dp(100), 16)
        profileAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        
        profileNick = TextView(this)
        profileNick.textSize = 20f
        profileNick.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        val statsRow = LinearLayout(this)
        statsRow.orientation = LinearLayout.HORIZONTAL
        statsRow.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 16)
        statsRow.gravity = Gravity.CENTER_HORIZONTAL
        
        val postsBlock = LinearLayout(this)
        postsBlock.orientation = LinearLayout.VERTICAL
        postsBlock.gravity = Gravity.CENTER
        postsBlock.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(postsBlock, 1f)
        profilePostsCount = TextView(this)
        profilePostsCount.gravity = Gravity.CENTER
        profilePostsCount.textSize = 18f
        postsBlock.addView(profilePostsCount)
        postsBlock.addView(TextView(this).apply { text = "Посты"; gravity = Gravity.CENTER; textSize = 12f })
        
        val followersBlock = LinearLayout(this)
        followersBlock.orientation = LinearLayout.VERTICAL
        followersBlock.gravity = Gravity.CENTER
        followersBlock.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(followersBlock, 1f)
        profileFollowersCount = TextView(this)
        profileFollowersCount.gravity = Gravity.CENTER
        profileFollowersCount.textSize = 18f
        followersBlock.addView(profileFollowersCount)
        followersBlock.addView(TextView(this).apply { text = "Подписчики"; gravity = Gravity.CENTER; textSize = 12f })
        
        val likesBlock = LinearLayout(this)
        likesBlock.orientation = LinearLayout.VERTICAL
        likesBlock.gravity = Gravity.CENTER
        likesBlock.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(likesBlock, 1f)
        profileLikesCount = TextView(this)
        profileLikesCount.gravity = Gravity.CENTER
        profileLikesCount.textSize = 18f
        likesBlock.addView(profileLikesCount)
        likesBlock.addView(TextView(this).apply { text = "Лайки"; gravity = Gravity.CENTER; textSize = 12f })
        
        statsRow.addView(postsBlock)
        statsRow.addView(followersBlock)
        statsRow.addView(likesBlock)
        
        profileEditBtn = Button(this)
        profileEditBtn.text = "Редактировать профиль"
        profileEditBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 16)
        
        profileBio = TextView(this)
        profileBio.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 16)
        
        val divider = View(this)
        divider.setBackgroundColor(0xFFCCCCCC.toInt())
        divider.layoutParams = lp(-1, dp(1), 16)
        
        profilePostsGrid = LinearLayout(this)
        profilePostsGrid.orientation = LinearLayout.VERTICAL
        profilePostsGrid.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        
        content.addView(profileAvatar)
        content.addView(profileNick)
        content.addView(statsRow)
        content.addView(profileEditBtn)
        content.addView(profileBio)
        content.addView(divider)
        content.addView(profilePostsGrid)
        
        profileScroll.addView(content)
        profileRoot.addView(profileScroll)
        
        profileEditBtn.setOnClickListener { showEditProfileDialog() }
        
        return profileRoot
    }
    
    private fun showEditProfileDialog() {
        if (viewingUserId != currentUserId) return
        
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(0xFFFFFFFF.toInt())
        layout.setPadding(32, 16, 32, 16)
        
        val headerLayout = LinearLayout(this)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.gravity = Gravity.CENTER_VERTICAL
        
        val backBtn = Button(this)
        backBtn.text = "←"
        backBtn.textSize = 24f
        backBtn.layoutParams = lp(-2, -2, 16)
        backBtn.setOnClickListener { dlg.dismiss() }
        
        val title = TextView(this)
        title.text = "Редактировать профиль"
        title.textSize = 20f
        title.layoutParams = lp(0, -2, 0)
        wt(title, 1f)
        
        headerLayout.addView(backBtn)
        headerLayout.addView(title)
        
        val avatarLayout = LinearLayout(this)
        avatarLayout.orientation = LinearLayout.VERTICAL
        avatarLayout.gravity = Gravity.CENTER_HORIZONTAL
        avatarLayout.layoutParams = lp(-1, -2, 32)
        
        editAvatarView = ImageView(this)
        editAvatarView!!.layoutParams = lp(dp(120), dp(120), 16)
        editAvatarView!!.scaleType = ImageView.ScaleType.FIT_CENTER
        
        if (currentAvatar.isNotEmpty()) {
            try {
                val bytes = Base64.decode(currentAvatar, Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                editAvatarView!!.setImageBitmap(makeCircularBitmap(bmp, dp(120)))
            } catch (e: Exception) {
                editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(currentNick), dp(120)))
            }
        } else {
            editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(currentNick), dp(120)))
        }
        
        val changeAvatarBtn = Button(this)
        changeAvatarBtn.text = "📷 Сменить аватар"
        changeAvatarBtn.layoutParams = lp(-1, -2, 0)
        
        avatarLayout.addView(editAvatarView)
        avatarLayout.addView(changeAvatarBtn)
        
        val nickEdit = EditText(this)
        nickEdit.hint = "Ник"
        nickEdit.setText(currentNick)
        nickEdit.layoutParams = lp(-1, -2, 16)
        
        val bioEdit = EditText(this)
        bioEdit.hint = "Био"
        bioEdit.setText(profileBio.text.toString())
        bioEdit.layoutParams = lp(-1, -2, 16)
        bioEdit.gravity = Gravity.TOP
        bioEdit.minLines = 3
        
        val saveBtn = Button(this)
        saveBtn.text = "Сохранить"
        saveBtn.layoutParams = lp(-1, -2, 0)
        
        changeAvatarBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_AVATAR)
        }
        
        saveBtn.setOnClickListener {
            val newNick = nickEdit.text.toString().trim()
            val newBio = bioEdit.text.toString().trim()
            if (newNick.isNotEmpty()) {
                database.child("users").child(currentUserId).child("nick").setValue(newNick)
                currentNick = newNick
            }
            database.child("users").child(currentUserId).child("bio").setValue(newBio)
            dlg.dismiss()
            refreshProfile()
            loadFeed()
        }
        
        layout.addView(headerLayout)
        layout.addView(avatarLayout)
        layout.addView(nickEdit)
        layout.addView(bioEdit)
        layout.addView(saveBtn)
        
        dlg.setContentView(layout)
        dlg.show()
    }
    
    private fun refreshProfile() {
        if (!::profileRoot.isInitialized) return
        
        val targetId = if (viewingUserId.isNotEmpty()) viewingUserId else currentUserId
        val targetNick = if (viewingNick.isNotEmpty()) viewingNick else currentNick
        
        database.child("users").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nick = snapshot.child("nick").getValue(String::class.java) ?: "?"
                val avatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                val bio = snapshot.child("bio").getValue(String::class.java) ?: "Напиши что-нибудь о себе..."
                
                if (avatar.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(avatar, Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        profileAvatar.setImageBitmap(makeCircularBitmap(bmp, dp(100)))
                    } catch (e: Exception) {
                        profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(nick), dp(100)))
                    }
                } else {
                    profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(nick), dp(100)))
                }
                
                profileNick.text = "@$nick"
                profileBio.text = bio
                
                if (targetId == currentUserId) {
                    profileEditBtn.visibility = View.VISIBLE
                    profileEditBtn.text = "Редактировать профиль"
                    profileEditBtn.setOnClickListener { showEditProfileDialog() }
                } else {
                    profileEditBtn.visibility = View.VISIBLE
                    profileEditBtn.text = "Написать сообщение"
                    profileEditBtn.setOnClickListener { openChat(targetId, nick) }
                }
                
                database.child("posts").orderByChild("authorId").equalTo(targetId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(postSnapshot: DataSnapshot) {
                            var totalLikes = 0
                            var postCount = 0
                            val userPosts = ArrayList<DataSnapshot>()
                            
                            for (post in postSnapshot.children) {
                                userPosts.add(post); postCount++
                                totalLikes += (post.child("likes").value as? Number)?.toInt() ?: 0
                            }
                            
                            profilePostsCount.text = postCount.toString()
                            profileFollowersCount.text = "0"
                            profileLikesCount.text = totalLikes.toString()
                            
                            profilePostsGrid.removeAllViews()
                            if (userPosts.isEmpty()) {
                                val empty = TextView(this@MainActivity)
                                empty.text = "Нет постов"
                                empty.gravity = Gravity.CENTER
                                empty.setPadding(0, dp(40), 0, dp(40))
                                profilePostsGrid.addView(empty)
                                return
                            }
                            
                            for (post in userPosts.reversed()) {
                                val postId = post.key ?: ""
                                val imagesStr = post.child("images").getValue(String::class.java) ?: ""
                                val text = post.child("text").getValue(String::class.java) ?: ""
                                val likeCount = (post.child("likes").value as? Number)?.toInt() ?: 0
                                
                                val card = LinearLayout(this@MainActivity)
                                card.orientation = LinearLayout.VERTICAL
                                card.setPadding(0, 0, 0, 8)
                                card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                                card.setBackgroundColor(0xFFFFFFFF.toInt())
                                card.elevation = 2f
                                
                                if (text.isNotEmpty()) {
                                    val tv = TextView(this@MainActivity)
                                    tv.text = text
                                    tv.textSize = 14f
                                    tv.setPadding(12, 12, 12, 8)
                                    tv.setTextColor(0xFF000000.toInt())
                                    Linkify.addLinks(tv, Linkify.WEB_URLS)
                                    tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                                    card.addView(tv)
                                }
                                
                                if (imagesStr.isNotEmpty()) {
                                    val images = imagesStr.split(",").filter { it.isNotEmpty() }
                                    if (images.isNotEmpty()) {
                                        try {
                                            val bytes = Base64.decode(images[0], Base64.NO_WRAP)
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            val box = FrameLayout(this@MainActivity)
                                            box.layoutParams = lp(-1, dp(200), 0)
                                            box.setBackgroundColor(0xFFFAFAFA.toInt())
                                            val iv = ImageView(this@MainActivity)
                                            iv.setImageBitmap(bmp)
                                            iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                            iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                            iv.setOnClickListener { openViewer(images, 0) }
                                            box.addView(iv)
                                            card.addView(box)
                                        } catch (e: Exception) {}
                                    }
                                }
                                
                                // Кнопка лайка в профиле (та же транзакция)
                                val likeBtn = Button(this@MainActivity)
                                likeBtn.text = "♥ $likeCount"
                                likeBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                                attachLike(likeBtn, postId)
                                card.addView(likeBtn)
                                
                                profilePostsGrid.addView(card)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    private fun createAvatarBitmap(nick: String): Bitmap {
        val size = dp(100)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = 0xFF4A90E2.toInt()
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = size / 2.5f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
        
        val initial = if (nick.isNotEmpty()) nick[0].uppercaseChar() else '?'
        val metrics = textPaint.fontMetrics
        val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initial.toString(), size / 2f, y, textPaint)
        
        return bmp
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        
        if (requestCode == PICK_IMAGES) {
            val uris = ArrayList<android.net.Uri>()
            val clip = data.clipData
            if (clip != null) { 
                for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri) 
            } else {
                data.data?.let { uris.add(it) }
            }
            
            if (uris.isEmpty()) return
            pendingImages.clear()
            for (u in uris) {
                if (pendingImages.size >= 4) break
                try {
                    val ins = contentResolver.openInputStream(u)
                    val bmp = BitmapFactory.decodeStream(ins)
                    ins?.close()
                    val scaled = if (bmp.width > 480) Bitmap.createScaledBitmap(bmp, 480, 480 * bmp.height / bmp.width, true) else bmp
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                    pendingImages.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                } catch (e: Exception) {}
            }
            
            if (pendingImages.isNotEmpty()) {
                pendingWrap.visibility = View.VISIBLE
                pendingInfo.text = "выбрано фото: ${pendingImages.size}"
                pendingRow.removeAllViews()
                for (i in pendingImages.indices) {
                    try {
                        val bytes = Base64.decode(pendingImages[i], Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val iv = ImageView(this)
                        iv.setImageBitmap(bmp)
                        iv.adjustViewBounds = true
                        iv.layoutParams = lp(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                        pendingRow.addView(iv)
                    } catch (e: Exception) {}
                }
            }
        } else if (requestCode == PICK_AVATAR) {
            val uri = data.data ?: return
            try {
                val ins = contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(ins)
                ins?.close()
                val size = minOf(bmp.width, bmp.height)
                val cropped = Bitmap.createBitmap(bmp, (bmp.width - size) / 2, (bmp.height - size) / 2, size, size)
                val scaled = Bitmap.createScaledBitmap(cropped, 200, 200, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                val avatarB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                database.child("users").child(currentUserId).child("avatar").setValue(avatarB64)
                currentAvatar = avatarB64
                
                // Обновляем кэш и все видимые аватарки
                avatarCache[currentUserId] = makeCircularBitmap(scaled, dp(40))
                profileAvatar.setImageBitmap(makeCircularBitmap(scaled, dp(100)))
                editAvatarView?.setImageBitmap(makeCircularBitmap(scaled, dp(120)))
            } catch (e: Exception) {}
        }
    }
}
