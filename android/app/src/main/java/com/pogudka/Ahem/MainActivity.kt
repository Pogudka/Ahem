package com.pogudka.Ahem

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
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
    private var currentUserId = ""
    private var currentNick = ""
    private val pendingImages = ArrayList<String>()
    private val likes = mutableMapOf<String, Int>()
    
    // ЧАТЫ
    private lateinit var chatListRoot: LinearLayout
    private lateinit var convRoot: LinearLayout
    private lateinit var chatListRows: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var chatStatus: TextView
    private lateinit var nameField: EditText
    private lateinit var convTitle: TextView
    private lateinit var convBox: LinearLayout
    private lateinit var convScroll: ScrollView
    private lateinit var msgField: EditText
    private var currentChat = ""
    private val knownNames = LinkedHashSet<String>()
    
    // ПРОФИЛЬ
    private lateinit var profileText: TextView
    private lateinit var lanInfo: TextView
    
    private val PICK_IMAGE = 1001
    private val PICK_IMAGES = 1002
    private var photoTarget = "chat"
    private val ZOOM_MAX = 3f
    
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }
    private fun wt(v: View, w: Float) { (v.layoutParams as LinearLayout.LayoutParams).weight = w }
    private fun me() = nameField.text.toString().ifEmpty { currentNick.ifEmpty { "Аноним" } }
    private fun addKnown(n: String) { if (n.isNotEmpty() && n != me()) knownNames.add(n) }

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
        
        checkAuth()
    }
    
    private fun checkAuth() {
        val user = auth.currentUser
        if (user != null) {
            currentUserId = user.uid
            database.child("users").child(currentUserId).child("nick")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                        nameField.setText(currentNick)
                        enterFeedMode()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        feedStatus.text = "Ошибка: ${error.message}"
                    }
                })
        }
    }
    
    private fun showTab(i: Int) {
        for (idx in tabs.indices) tabs[idx].visibility = if (idx == i) View.VISIBLE else View.GONE
        for (idx in tabBtns.indices) tabBtns[idx].setBackgroundColor(
            if (idx == i) 0xFF222222.toInt() else 0xFFCCCCCC.toInt()
        )
        if (i == 3) refreshProfile()
    }

    // ================= ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        authBlock = LinearLayout(this)
        authBlock.orientation = LinearLayout.VERTICAL
        
        feedEmail = EditText(this).apply { hint = "Email" }
        feedNick = EditText(this).apply { hint = "Ник" }
        feedPass = EditText(this).apply { hint = "Пароль"; inputType = 1 }
        
        val rowAuth = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val regBtn = Button(this).apply { text = "Регистрация"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        val logBtn = Button(this).apply { text = "Вход"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        rowAuth.addView(regBtn); rowAuth.addView(logBtn)
        
        feedStatus = TextView(this).apply { text = "не залогинен" }
        
        authBlock.addView(feedEmail); authBlock.addView(feedNick); authBlock.addView(feedPass)
        authBlock.addView(rowAuth); authBlock.addView(feedStatus)
        
        feedBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        feedHeader = TextView(this).apply { layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        val switchBtn = Button(this).apply { text = "сменить"; layoutParams = lp(-2, -2, 0) }
        headerRow.addView(feedHeader); headerRow.addView(switchBtn)
        
        pendingWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val pendHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pendingInfo = TextView(this).apply { layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        val clearBtn = Button(this).apply { text = "✖"; layoutParams = lp(-2, -2, 0) }
        pendHead.addView(pendingInfo); pendHead.addView(clearBtn)
        val pendScroll = HorizontalScrollView(this).apply { layoutParams = lp(-1, -2, 0) }
        pendingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pendScroll.addView(pendingRow)
        pendingWrap.addView(pendHead); pendingWrap.addView(pendScroll)
        
        feedPostText = EditText(this).apply { hint = "Подпись к посту (до 4 фото)" }
        val rowPost = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pubBtn = Button(this).apply { text = "Опубликовать"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 2f) }
        val phBtn = Button(this).apply { text = "📷 несколько"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        rowPost.addView(pubBtn); rowPost.addView(phBtn)
        
        val rowAct = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val feedBtn = Button(this).apply { text = "⟳ Лента"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        feedFollowNick = EditText(this).apply { hint = "@ник подписки"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 2f) }
        val folBtn = Button(this).apply { text = "Подписаться"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        rowAct.addView(feedBtn); rowAct.addView(feedFollowNick); rowAct.addView(folBtn)
        
        feedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        feedScroll = ScrollView(this).apply { addView(feedBox); layoutParams = lp(-1, 0, 0); wt(this, 1f) }
        
        feedBlock.addView(headerRow); feedBlock.addView(pendingWrap); feedBlock.addView(feedPostText)
        feedBlock.addView(rowPost); feedBlock.addView(rowAct); feedBlock.addView(feedScroll)
        
        regBtn.setOnClickListener {
            val email = feedEmail.text.toString(); val nick = feedNick.text.toString(); val pass = feedPass.text.toString()
            if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) { feedStatus.text = "Заполни все поля"; return@setOnClickListener }
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    database.child("users").child(userId).child("email").setValue(email)
                    database.child("users").child(userId).child("nick").setValue(nick)
                    currentUserId = userId; currentNick = nick
                    nameField.setText(nick)
                    enterFeedMode()
                } else { feedStatus.text = "Ошибка: ${task.exception?.message}" }
            }
        }
        
        logBtn.setOnClickListener {
            val email = feedEmail.text.toString(); val pass = feedPass.text.toString()
            if (email.isEmpty() || pass.isEmpty()) { feedStatus.text = "Email + пароль"; return@setOnClickListener }
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    currentUserId = userId
                    database.child("users").child(userId).child("nick").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                            nameField.setText(currentNick)
                            enterFeedMode()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                } else { feedStatus.text = "Ошибка: ${task.exception?.message}" }
            }
        }
        
        switchBtn.setOnClickListener {
            auth.signOut()
            feedBlock.visibility = View.GONE; authBlock.visibility = View.VISIBLE
            feedStatus.text = "не залогинен"; currentUserId = ""; currentNick = ""
        }
        
        clearBtn.setOnClickListener { pendingImages.clear(); pendingWrap.visibility = View.GONE }
        phBtn.setOnClickListener { 
            val i = Intent(Intent.ACTION_GET_CONTENT); i.type = "image/*"; i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(Intent.createChooser(i, "Выбери фото"), PICK_IMAGES) 
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
            database.child("posts").child(postId).setValue(post)
            feedPostText.setText(""); pendingImages.clear(); pendingWrap.visibility = View.GONE
        }
        
        feedBtn.setOnClickListener { loadFeed() }
        folBtn.setOnClickListener {
            val n = feedFollowNick.text.toString()
            if (n.isNotEmpty()) { addKnown(n); feedFollowNick.setText("") }
        }
        
        col.addView(authBlock); col.addView(feedBlock)
        return col
    }
    
    private fun enterFeedMode() {
        authBlock.visibility = View.GONE; feedBlock.visibility = View.VISIBLE
        feedHeader.text = "@$currentNick"
        loadFeed()
    }
    
    private fun loadFeed() {
        feedBox.removeAllViews()
        database.child("posts").orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = ArrayList<DataSnapshot>()
                for (child in snapshot.children) posts.add(child)
                for (post in posts.reversed()) {
                    val postId = post.key ?: ""
                    val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
                    val text = post.child("text").getValue(String::class.java) ?: ""
                    val imagesStr = post.child("images").getValue(String::class.java) ?: ""
                    val ts = post.child("timestamp").getValue(Long::class.java) ?: 0
                    val likeCount = post.child("likes").getValue(Int::class.java) ?: 0
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                    
                    val card = LinearLayout(this@MainActivity).apply { 
                        orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8)
                        layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
                        setBackgroundColor(0xFFEEEEEE.toInt())
                    }
                    
                    val title = TextView(this@MainActivity).apply { 
                        text = "[$authorNick] $timeStr   ♥$likeCount"
                        layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                    }
                    card.addView(title)
                    
                    if (text.isNotEmpty()) {
                        val bt = TextView(this@MainActivity).apply { 
                            this.text = text; setPadding(0, 4, 0, 4)
                            layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                        }
                        card.addView(bt)
                    }
                    
                    if (imagesStr.isNotEmpty()) {
                        val images = imagesStr.split(",").filter { it.isNotEmpty() }
                        for (img in images) {
                            try {
                                val bytes = Base64.decode(img, Base64.NO_WRAP)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val box = FrameLayout(this@MainActivity).apply {
                                    layoutParams = lp(-1, dp(200), 6)
                                    setBackgroundColor(0xFFFAFAFA.toInt())
                                }
                                val iv = ImageView(this@MainActivity).apply { 
                                    setImageBitmap(bmp)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                                    setOnClickListener { 
                                        val imgList = images
                                        val idx = images.indexOf(img)
                                        openViewer(imgList, idx)
                                    }
                                }
                                box.addView(iv)
                                card.addView(box)
                            } catch (e: Exception) {}
                        }
                    }
                    
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                    val likeBtn = Button(this@MainActivity).apply {
                        text = "♥"
                        layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                        wt(this, 1f)
                        setOnClickListener {
                            val newCount = likeCount + 1
                            database.child("posts").child(postId).child("likes").setValue(newCount)
                            Toast.makeText(this@MainActivity, "Лайк поставлен!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(likeBtn)
                    
                    if (authorNick != currentNick) {
                        val fb = Button(this@MainActivity).apply {
                            text = "+ @$authorNick"
                            layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                            wt(this, 2f)
                            setOnClickListener { addKnown(authorNick) }
                        }
                        row.addView(fb)
                    }
                    card.addView(row)
                    feedBox.addView(card)
                }
                if (posts.isEmpty()) feedBox.addView(TextView(this@MainActivity).apply { text = "Лента пуста" })
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun openViewer(images: List<String>, start: Int) {
        if (images.isEmpty()) return
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this); frame.setBackgroundColor(0xFF000000.toInt())
        val img = ImageView(this); img.scaleType = ImageView.ScaleType.MATRIX
        frame.addView(img, FrameLayout.LayoutParams(-1, -1))
        
        val back = TextView(this).apply {
            text = "←"; setTextColor(0xFFFFFFFF.toInt()); textSize = 28f
            setPadding(dp(16), dp(10), dp(16), dp(10)); setBackgroundColor(0x66000000)
        }
        val blp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        blp.gravity = Gravity.TOP or Gravity.START; frame.addView(back, blp)
        back.setOnClickListener { dlg.dismiss() }
        
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0x66000000) }
        val prevB = Button(this).apply { text = "◀"; setTextColor(0xFFFFFFFF.toInt()); layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        val cnt = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 2f) }
        val nextB = Button(this).apply { text = "▶"; setTextColor(0xFFFFFFFF.toInt()); layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        bar.addView(prevB); bar.addView(cnt); bar.addView(nextB)
        val brp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        brp.gravity = Gravity.BOTTOM; frame.addView(bar, brp)
        
        dlg.setContentView(frame); dlg.show()
        
        var idx = start.coerceIn(0, images.size - 1)
        var bmp: Bitmap? = null; var scale = 1f; var minS = 1f; var maxS = ZOOM_MAX; var panX = 0f; var panY = 0f
        
        fun clampPan(vw: Int, vh: Int) {
            if (bmp == null) return
            val bw = bmp!!.width * scale; val bh = bmp!!.height * scale
            val mx = maxOf(0f, (bw - vw) / 2f); val my = maxOf(0f, (bh - vh) / 2f)
            panX = panX.coerceIn(-mx, mx); panY = panY.coerceIn(-my, my)
        }
        fun apply() {
            val b = bmp ?: return; val vw = img.width; val vh = img.height
            if (vw == 0 || vh == 0) return
            clampPan(vw, vh)
            val m = Matrix(); m.postScale(scale, scale)
            m.postTranslate((vw - b.width * scale) / 2f + panX, (vh - b.height * scale) / 2f + panY)
            img.imageMatrix = m
        }
        fun setup(b: Bitmap) {
            bmp = b; val vw = img.width; val vh = img.height
            if (vw == 0 || vh == 0) { img.post { setup(b) }; return }
            val fit = minOf(vw.toFloat() / b.width, vh.toFloat() / b.height)
            minS = minOf(fit, 1f); maxS = ZOOM_MAX; scale = minOf(fit, 1f)
            panX = 0f; panY = 0f; apply()
        }
        fun show() {
            try {
                val bytes = Base64.decode(images[idx], Base64.NO_WRAP)
                val bb = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                img.setImageBitmap(bb); img.post { setup(bb) }
            } catch (e: Exception) {}
            cnt.text = "${idx + 1} / ${images.size}"
            prevB.isEnabled = idx > 0; nextB.isEnabled = idx < images.size - 1
        }
        prevB.setOnClickListener { if (idx > 0) { idx--; show() } }
        nextB.setOnClickListener { if (idx < images.size - 1) { idx++; show() } }
        
        val sgd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(minS, maxS); apply(); return true
            }
        })
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scale > minS + 0.001f) { panX -= dx; panY -= dy; apply() }
                return true
            }
        })
        img.setOnTouchListener { _, ev -> sgd.onTouchEvent(ev); gd.onTouchEvent(ev); true }
        show()
    }

    // ================= ЧАТЫ =================
    private fun buildChats(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        
        chatListRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameField = EditText(this).apply { hint = "Твоё имя (из ленты)" }
        chatStatus = TextView(this).apply { text = "Firebase подключен" }
        
        val rowSearch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchField = EditText(this).apply { hint = "Поиск по нику"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 2f) }
        val searchBtn = Button(this).apply { text = "🔍"; layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f) }
        rowSearch.addView(searchField); rowSearch.addView(searchBtn)
        
        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatListRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val listScroll = ScrollView(this).apply { addView(chatListRows); layoutParams = lp(-1, 0, 0); wt(this, 1f) }
        
        chatListRoot.addView(nameField); chatListRoot.addView(chatStatus)
        chatListRoot.addView(rowSearch); chatListRoot.addView(searchResults); chatListRoot.addView(listScroll)
        
        convRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val backBtn = Button(this).apply { text = "←"; layoutParams = lp(-2, -2, 0) }
        convTitle = TextView(this).apply { layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(this, 1f); textSize = 18f }
        top.addView(backBtn); top.addView(convTitle)
        
        convBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        convScroll = ScrollView(this).apply { addView(convBox); layoutParams = lp(-1, 0, 0); wt(this, 1f) }
        msgField = EditText(this).apply { hint = "Сообщение..." }
        val sendBtn = Button(this).apply { text = "Отправить"; layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8) }
        
        convRoot.addView(top); convRoot.addView(convScroll); convRoot.addView(msgField); convRoot.addView(sendBtn)
        
        searchBtn.setOnClickListener {
            val query = searchField.text.toString().trim().lowercase()
            if (query.isEmpty()) return@setOnClickListener
            searchResults.removeAllViews()
            database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (user in snapshot.children) {
                        val nick = user.child("nick").getValue(String::class.java) ?: ""
                        if (nick.lowercase().contains(query) && user.key != currentUserId) {
                            val btn = Button(this@MainActivity).apply {
                                text = "✉ $nick"; layoutParams = lp(-1, -2, 4)
                                setOnClickListener { openChat(user.key ?: "", nick) }
                            }
                            searchResults.addView(btn)
                        }
                    }
                    if (searchResults.childCount == 0) searchResults.addView(TextView(this@MainActivity).apply { text = "Никого не нашли" })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
        
        backBtn.setOnClickListener { currentChat = ""; convRoot.visibility = View.GONE; chatListRoot.visibility = View.VISIBLE }
        
        sendBtn.setOnClickListener {
            val text = msgField.text.toString()
            if (text.isEmpty() || currentChat.isEmpty()) return@setOnClickListener
            val msgId = database.child("chats").child(currentChat).child("messages").push().key ?: return@setOnClickListener
            val msg = HashMap<String, Any>()
            msg["senderId"] = currentUserId; msg["senderNick"] = currentNick
            msg["text"] = text; msg["timestamp"] = System.currentTimeMillis()
            database.child("chats").child(currentChat).child("messages").child(msgId).setValue(msg)
            msgField.setText("")
        }
        
        col.addView(chatListRoot); col.addView(convRoot)
        return col
    }
    
    private fun openChat(otherUserId: String, otherNick: String) {
        currentChat = if (currentUserId < otherUserId) "${currentUserId}_$otherUserId" else "${otherUserId}_$currentUserId"
        convTitle.text = otherNick
        chatListRoot.visibility = View.GONE; convRoot.visibility = View.VISIBLE
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
                        
                        val tv = TextView(this@MainActivity).apply {
                            this.text = if (isMe) "→ $text" else "← $senderNick: $text"
                            setPadding(0, 4, 0, 4)
                            setTextColor(if (isMe) 0xFF004400.toInt() else 0xFF000044.toInt())
                        }
                        convBox.addView(tv)
                    }
                    convScroll.post { convScroll.fullScroll(View.FOCUS_DOWN) }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ================= ВИДЕО И ПРОФИЛЬ =================
    private fun buildVideo(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        col.addView(TextView(this).apply { 
            text = "🎬 Видео\n\nЗдесь появится лента клипов — свайп вверх/вниз.\nСкоро." 
        })
        return col
    }
    
    private fun buildProfile(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        col.addView(TextView(this).apply { text = "👤 Профиль и сеть"; textSize = 18f })
        lanInfo = TextView(this)
        profileText = TextView(this)
        col.addView(lanInfo); col.addView(profileText)
        return col
    }
    
    private fun refreshProfile() {
        lanInfo.text = "Подключено к Firebase Realtime Database"
        val chatName = nameField.text.toString()
        profileText.text = "Лента: @${if (currentNick.isEmpty()) "—" else currentNick}\nЧат: ${if (chatName.isEmpty()) "—" else chatName}\nUID: ${if (currentUserId.isEmpty()) "—" else currentUserId}\n\nНастройки, аватар и био появятся здесь."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        
        if (requestCode == PICK_IMAGES) {
            val uris = ArrayList<android.net.Uri>()
            val clip = data.clipData
            if (clip != null) { for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri) }
            else data.data?.let { uris.add(it) }
            
            if (uris.isEmpty()) return
            pendingImages.clear()
            for (u in uris) {
                if (pendingImages.size >= 4) break
                try {
                    val ins = contentResolver.openInputStream(u)
                    val bmp = BitmapFactory.decodeStream(ins); ins?.close()
                    val scaled = if (bmp.width > 480) Bitmap.createScaledBitmap(bmp, 480, 480 * bmp.height / bmp.width, true) else bmp
                    val baos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
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
                        val iv = ImageView(this).apply { 
                            setImageBitmap(bmp); adjustViewBounds = true
                            layoutParams = lp(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                        }
                        pendingRow.addView(iv)
                    } catch (e: Exception) {}
                }
            }
        }
    }
}
