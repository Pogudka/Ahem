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
    private lateinit var regBtn: Button
    private lateinit var logBtn: Button
    private var currentUserId = ""
    private var currentNick = ""
    private val pendingImages = ArrayList<String>()
    private var isRegistering = false
    private var isLoggingIn = false
    
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
    
    // Слушатели Firebase
    private var feedListener: ValueEventListener? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }
    private fun wt(v: View, w: Float) { 
        (v.layoutParams as LinearLayout.LayoutParams).weight = w 
    }
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
        
        val titles = arrayOf("📰 Лента", " Видео", "💬 Чаты", " Профиль")
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
        
        // Слушатель состояния авторизации для автовхода
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && currentUserId.isEmpty()) {
                currentUserId = user.uid
                database.child("users").child(currentUserId).child("nick")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                            if (::nameField.isInitialized) nameField.setText(currentNick)
                            if (::feedBlock.isInitialized && feedBlock.visibility == View.GONE) {
                                enterFeedMode()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else if (user == null && currentUserId.isNotEmpty()) {
                currentUserId = ""
                currentNick = ""
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
        if (i == 3) refreshProfile()
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
        
        pendingRow = LinearLayout(this)
        pendingRow.orientation = LinearLayout.HORIZONTAL
        
        pendScroll.addView(pendingRow)
        pendingWrap.addView(pendHead)
        pendingWrap.addView(pendScroll)
        
        feedPostText = EditText(this)
        feedPostText.hint = "Подпись к посту (до 4 фото)"
        
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
                    nameField.setText(nick)
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
                    database.child("users").child(userId).child("nick").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                            nameField.setText(currentNick)
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
        // Удаляем старый слушатель если есть
        feedListener?.let { database.child("posts").removeEventListener(it) }
        feedBox.removeAllViews()
        
        feedListener = database.child("posts").orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                feedBox.removeAllViews()
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
                    
                    val card = LinearLayout(this@MainActivity)
                    card.orientation = LinearLayout.VERTICAL
                    card.setPadding(8, 8, 8, 8)
                    card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
                    card.setBackgroundColor(0xFFEEEEEE.toInt())
                    
                    val title = TextView(this@MainActivity)
                    title.text = "[$authorNick] $timeStr   ♥$likeCount"
                    title.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                    card.addView(title)
                    
                    if (text.isNotEmpty()) {
                        val bt = TextView(this@MainActivity)
                        bt.text = text
                        bt.setPadding(0, 4, 0, 4)
                        bt.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                        card.addView(bt)
                    }
                    
                    if (imagesStr.isNotEmpty()) {
                        val images = imagesStr.split(",").filter { it.isNotEmpty() }
                        for (img in images) {
                            try {
                                val bytes = Base64.decode(img, Base64.NO_WRAP)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val box = FrameLayout(this@MainActivity)
                                box.layoutParams = lp(-1, dp(200), 6)
                                box.setBackgroundColor(0xFFFAFAFA.toInt())
                                
                                val iv = ImageView(this@MainActivity)
                                iv.setImageBitmap(bmp)
                                iv.scaleType = ImageView.ScaleType.FIT_CENTER
                                iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                iv.setOnClickListener { 
                                    val idx = images.indexOf(img)
                                    openViewer(images, idx)
                                }
                                box.addView(iv)
                                card.addView(box)
                            } catch (e: Exception) {}
                        }
                    }
                    
                    val row = LinearLayout(this@MainActivity)
                    row.orientation = LinearLayout.HORIZONTAL
                    
                    val likeBtn = Button(this@MainActivity)
                    likeBtn.text = "♥"
                    likeBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                    wt(likeBtn, 1f)
                    likeBtn.setOnClickListener {
                        // Проверяем, лайкал ли уже этот пользователь
                        val likedBy = post.child("likedBy").children.map { it.key }.toSet()
                        if (currentUserId in likedBy) {
                            Toast.makeText(this@MainActivity, "Вы уже лайкнули", Toast.LENGTH_SHORT).show()
                        } else {
                            val newCount = likeCount + 1
                            database.child("posts").child(postId).child("likes").setValue(newCount)
                            database.child("posts").child(postId).child("likedBy").child(currentUserId).setValue(true)
                        }
                    }
                    row.addView(likeBtn)
                    
                    if (authorNick != currentNick) {
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
                if (posts.isEmpty()) {
                    feedBox.addView(TextView(this@MainActivity).apply { text = "Лента пуста" })
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
        
        nameField = EditText(this)
        nameField.hint = "Твоё имя (из ленты)"
        
        chatStatus = TextView(this)
        chatStatus.text = "Firebase подключен"
        
        val rowSearch = LinearLayout(this)
        rowSearch.orientation = LinearLayout.HORIZONTAL
        
        searchField = EditText(this)
        searchField.hint = "Поиск по нику"
        searchField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchField, 2f)
        
        val searchBtn = Button(this)
        searchBtn.text = ""
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
        
        chatListRoot.addView(nameField)
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

    // ================= ВИДЕО И ПРОФИЛЬ =================
    private fun buildVideo(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        val tv = TextView(this)
        tv.text = "🎬 Видео\n\nЗдесь появится лента клипов — свайп вверх/вниз.\nСкоро."
        col.addView(tv)
        return col
    }
    
    private fun buildProfile(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        val head = TextView(this)
        head.text = "👤 Профиль и сеть"
        head.textSize = 18f
        
        lanInfo = TextView(this)
        profileText = TextView(this)
        
        col.addView(head)
        col.addView(lanInfo)
        col.addView(profileText)
        return col
    }
    
    private fun refreshProfile() {
        lanInfo.text = "Подключено к Firebase Realtime Database"
        val chatName = nameField.text.toString()
        profileText.text = "Лента: @${if (currentNick.isEmpty()) "—" else currentNick}\n" +
                "Чат: ${if (chatName.isEmpty()) "—" else chatName}\n" +
                "UID: ${if (currentUserId.isEmpty()) "—" else currentUserId}\n\n" +
                "Настройки, аватар и био появятся здесь."
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
        }
    }
}
