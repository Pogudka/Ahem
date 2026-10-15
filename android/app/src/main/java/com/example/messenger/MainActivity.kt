package com.pogudka.Ahem

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
    
    // Лента
    private lateinit var authBlock: LinearLayout
    private lateinit var feedBlock: LinearLayout
    private lateinit var feedHeader: TextView
    private lateinit var feedEmail: EditText
    private lateinit var feedNick: EditText
    private lateinit var feedPass: EditText
    private lateinit var feedStatus: TextView
    private lateinit var feedBox: LinearLayout
    private lateinit var feedScroll: ScrollView
    private var currentUserId = ""
    private var currentNick = ""
    
    // Чаты
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
    
    private val PICK_IMAGE = 1001
    private var photoTarget = "chat"
    
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }
    private fun wt(v: View, w: Float) { (v.layoutParams as LinearLayout.LayoutParams).weight = w }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Firebase инициализация
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
        val chatTab = buildChats()
        tabs = arrayOf(feedTab, chatTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))
        
        val titles = arrayOf("📰 Лента", "💬 Чаты")
        tabBtns = Array(2) { i ->
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
        
        // Проверяем авторизацию
        checkAuth()
    }
    
    private fun checkAuth() {
        val user = auth.currentUser
        if (user != null) {
            currentUserId = user.uid
            // Загружаем ник из базы
            database.child("users").child(currentUserId).child("nick")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                        enterFeedMode()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }
    
    private fun showTab(i: Int) {
        for (idx in tabs.indices) tabs[idx].visibility = if (idx == i) View.VISIBLE else View.GONE
        for (idx in tabBtns.indices) tabBtns[idx].setBackgroundColor(
            if (idx == i) 0xFF222222.toInt() else 0xFFCCCCCC.toInt()
        )
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
        
        val regBtn = Button(this)
        regBtn.text = "Регистрация"
        regBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        val logBtn = Button(this)
        logBtn.text = "Вход"
        logBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        feedStatus = TextView(this)
        feedStatus.text = "не залогинен"
        
        authBlock.addView(feedEmail)
        authBlock.addView(feedNick)
        authBlock.addView(feedPass)
        authBlock.addView(regBtn)
        authBlock.addView(logBtn)
        authBlock.addView(feedStatus)
        
        feedBlock = LinearLayout(this)
        feedBlock.orientation = LinearLayout.VERTICAL
        feedBlock.visibility = View.GONE
        
        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        
        feedHeader = TextView(this)
        feedHeader.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedHeader, 1f)
        
        val logoutBtn = Button(this)
        logoutBtn.text = "Выйти"
        logoutBtn.layoutParams = lp(-2, -2, 0)
        
        headerRow.addView(feedHeader)
        headerRow.addView(logoutBtn)
        
        val postText = EditText(this)
        postText.hint = "Новый пост"
        
        val pubBtn = Button(this)
        pubBtn.text = "Опубликовать"
        pubBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        val feedBtn = Button(this)
        feedBtn.text = "⟳ Обновить"
        feedBtn.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        
        feedBox = LinearLayout(this)
        feedBox.orientation = LinearLayout.VERTICAL
        
        feedScroll = ScrollView(this)
        feedScroll.addView(feedBox)
        feedScroll.layoutParams = lp(-1, 0, 0)
        wt(feedScroll, 1f)
        
        feedBlock.addView(headerRow)
        feedBlock.addView(postText)
        feedBlock.addView(pubBtn)
        feedBlock.addView(feedBtn)
        feedBlock.addView(feedScroll)
        
        regBtn.setOnClickListener {
            val email = feedEmail.text.toString()
            val nick = feedNick.text.toString()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) {
                feedStatus.text = "Заполни все поля"
                return@setOnClickListener
            }
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        // Сохраняем ник в базе
                        database.child("users").child(userId).child("email").setValue(email)
                        database.child("users").child(userId).child("nick").setValue(nick)
                        currentUserId = userId
                        currentNick = nick
                        feedStatus.text = "Зарегистрирован!"
                        enterFeedMode()
                    } else {
                        feedStatus.text = "Ошибка: ${task.exception?.message}"
                    }
                }
        }
        
        logBtn.setOnClickListener {
            val email = feedEmail.text.toString()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                feedStatus.text = "Email + пароль"
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        currentUserId = userId
                        database.child("users").child(userId).child("nick")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    currentNick = snapshot.getValue(String::class.java) ?: "Аноним"
                                    enterFeedMode()
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                    } else {
                        feedStatus.text = "Ошибка: ${task.exception?.message}"
                    }
                }
        }
        
        logoutBtn.setOnClickListener {
            auth.signOut()
            feedBlock.visibility = View.GONE
            authBlock.visibility = View.VISIBLE
            feedStatus.text = "не залогинен"
            currentUserId = ""
            currentNick = ""
        }
        
        pubBtn.setOnClickListener {
            val text = postText.text.toString()
            if (text.isEmpty()) return@setOnClickListener
            val postId = database.child("posts").push().key ?: return@setOnClickListener
            val post = HashMap<String, Any>()
            post["authorId"] = currentUserId
            post["authorNick"] = currentNick
            post["text"] = text
            post["timestamp"] = System.currentTimeMillis()
            database.child("posts").child(postId).setValue(post)
            postText.setText("")
        }
        
        feedBtn.setOnClickListener { loadFeed() }
        
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
        feedBox.removeAllViews()
        database.child("posts").orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val posts = ArrayList<DataSnapshot>()
                    for (child in snapshot.children) posts.add(child)
                    // Новые сверху
                    for (post in posts.reversed()) {
                        val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
                        val text = post.child("text").getValue(String::class.java) ?: ""
                        val ts = post.child("timestamp").getValue(Long::class.java) ?: 0
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                        
                        val card = LinearLayout(this@MainActivity)
                        card.orientation = LinearLayout.VERTICAL
                        card.setPadding(8, 8, 8, 8)
                        card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8)
                        card.setBackgroundColor(0xFFEEEEEE.toInt())
                        
                        val title = TextView(this@MainActivity)
                        title.text = "@$authorNick  $timeStr"
                        card.addView(title)
                        
                        if (text.isNotEmpty()) {
                            val bt = TextView(this@MainActivity)
                            bt.text = text
                            bt.setPadding(0, 4, 0, 4)
                            card.addView(bt)
                        }
                        
                        feedBox.addView(card)
                    }
                    if (posts.isEmpty()) {
                        val tv = TextView(this@MainActivity)
                        tv.text = "Лента пуста"
                        feedBox.addView(tv)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    // ================= ЧАТЫ =================
    private fun buildChats(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(16, 16, 16, 16)
        
        chatListRoot = LinearLayout(this)
        chatListRoot.orientation = LinearLayout.VERTICAL
        
        nameField = EditText(this)
        nameField.hint = "Твоё имя"
        
        chatStatus = TextView(this)
        chatStatus.text = "не подключено"
        
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
            database.child("users").orderByChild("nick")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (user in snapshot.children) {
                            val nick = user.child("nick").getValue(String::class.java) ?: ""
                            if (nick.lowercase().contains(query) && user.key != currentUserId) {
                                val btn = Button(this@MainActivity)
                                btn.text = "✉ $nick"
                                btn.layoutParams = lp(-1, -2, 4)
                                btn.setOnClickListener {
                                    openChat(user.key ?: "", nick)
                                }
                                searchResults.addView(btn)
                            }
                        }
                        if (searchResults.childCount == 0) {
                            val tv = TextView(this@MainActivity)
                            tv.text = "Никого не нашли"
                            searchResults.addView(tv)
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
        currentChat = if (currentUserId < otherUserId) 
            "${currentUserId}_$otherUserId" 
        else 
            "${otherUserId}_$currentUserId"
        
        convTitle.text = otherNick
        chatListRoot.visibility = View.GONE
        convRoot.visibility = View.VISIBLE
        convBox.removeAllViews()
        
        loadMessages()
    }
    
    private fun loadMessages() {
        database.child("chats").child(currentChat).child("messages")
            .orderByChild("timestamp")
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
}
