package com.pogudka.Ahem

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.util.Linkify
import android.util.Base64
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    // ================= ПАЛИТРА (только белый / чёрный / серый) =================
    private object P {
        var bg = 0xFFFFFFFF.toInt()
        var surface = 0xFFF7F7F9.toInt()
        var fieldBg = 0xFFFFFFFF.toInt()
        var border = 0xFFE2E2E6.toInt()
        var text = 0xFF000000.toInt()
        var textSec = 0xFF8A8A8E.toInt()
        var accent = 0xFF000000.toInt()
        var accentText = 0xFFFFFFFF.toInt()
        var navInactive = 0xFF9A9AA0.toInt()
        var heartOn = 0xFF000000.toInt()
        var scrim = 0x66000000.toInt()
    }

    private fun applyTheme(dark: Boolean) {
        if (dark) {
            P.bg = 0xFF000000.toInt()
            P.surface = 0xFF1C1C1E.toInt()
            P.fieldBg = 0xFF1C1C1E.toInt()
            P.border = 0xFF3A3A3C.toInt()
            P.text = 0xFFFFFFFF.toInt()
            P.textSec = 0xFF98989F.toInt()
            P.accent = 0xFFFFFFFF.toInt()
            P.accentText = 0xFF000000.toInt()
            P.navInactive = 0xFF7C7C82.toInt()
            P.heartOn = 0xFFFFFFFF.toInt()
            P.scrim = 0x99000000.toInt()
        } else {
            P.bg = 0xFFFFFFFF.toInt()
            P.surface = 0xFFF7F7F9.toInt()
            P.fieldBg = 0xFFFFFFFF.toInt()
            P.border = 0xFFE2E2E6.toInt()
            P.text = 0xFF000000.toInt()
            P.textSec = 0xFF8A8A8E.toInt()
            P.accent = 0xFF000000.toInt()
            P.accentText = 0xFFFFFFFF.toInt()
            P.navInactive = 0xFF9A9AA0.toInt()
            P.heartOn = 0xFF000000.toInt()
            P.scrim = 0x66000000.toInt()
        }
    }

    private fun isDark(): Boolean =
        getSharedPreferences("ahem", Context.MODE_PRIVATE).getBoolean("dark", false)

    // ================= ФОРМАТ ЧИСЕЛ (10M / 100K / 10K) =================
    private fun trimNum(d: Double): String {
        val r = Math.round(d * 10.0) / 10.0
        return if (r == Math.floor(r)) {
            r.toLong().toString()
        } else {
            r.toString()
        }
    }

    private fun fmtNum(n: Long): String {
        if (n >= 1_000_000L) return trimNum(n / 1_000_000.0) + "M"
        if (n >= 1_000L) return trimNum(n / 1_000.0) + "K"
        return n.toString()
    }

    private fun getTs(post: DataSnapshot): Long {
        val v = post.child("timestamp").value
        if (v is Number) return v.toLong()
        return (v as? String)?.toLongOrNull() ?: 0L
    }

    // ================= ШРИФТЫ =================
    private val fBold: Typeface by lazy { ResourcesCompat.getFont(this, R.font.unbounded)!! }
    private val fSemi: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    private val fMed: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    private val fReg: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_regular)!! }

    // ================= РАЗМЕТКА =================
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }

    private fun wt(v: View, w: Float) {
        val cur = v.layoutParams
        if (cur is LinearLayout.LayoutParams) {
            cur.weight = w
            v.layoutParams = cur
        }
    }

    private fun me(): String = currentNick.ifEmpty { "Аноним" }

    private fun addKnown(n: String) {
        if (n.isNotEmpty() && n != me()) knownNames.add(n)
    }

    private fun parseCount(t: TextView): Int =
        t.text.toString().substringAfter("♥").trim().replace("K", "000").replace("M", "000000").toIntOrNull() ?: 0

    // ================= ГРАФИКА =================
    private fun surf(radius: Int, filled: Boolean = true): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(if (filled) P.surface else Color.TRANSPARENT)
        g.cornerRadius = dp(radius).toFloat()
        g.setStroke(dp(1), P.border)
        return g
    }

    private fun fieldBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(P.fieldBg)
        g.cornerRadius = dp(14).toFloat()
        g.setStroke(dp(1), P.border)
        return g
    }

    private fun accentBg(radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(P.accent)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun tv(text: String, size: Float, type: Typeface, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.typeface = type
        t.setTextColor(color)
        return t
    }

    private fun field(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(P.textSec)
        e.setTextColor(P.text)
        e.typeface = fReg
        e.textSize = 15f
        e.background = fieldBg()
        e.setPadding(dp(16), dp(13), dp(16), dp(13))
        return e
    }

    private fun btnOutline(label: String): TextView {
        val t = tv(label, 14f, fSemi, P.text)
        t.gravity = Gravity.CENTER
        t.background = surf(12, filled = false)
        t.setPadding(dp(14), dp(11), dp(14), dp(11))
        return t
    }

    private fun btnAccent(label: String): TextView {
        val t = tv(label, 14f, fSemi, P.accentText)
        t.gravity = Gravity.CENTER
        t.background = accentBg(12)
        t.setPadding(dp(14), dp(11), dp(14), dp(11))
        return t
    }

    private fun icon(res: Int, tint: Int, size: Int): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.setColorFilter(tint)
        iv.layoutParams = lp(size, size, 0)
        return iv
    }

    private fun iconRound(res: Int, tint: Int, size: Int): ImageView {
        val iv = icon(res, tint, size)
        iv.setPadding(dp(9), dp(9), dp(9), dp(9))
        iv.background = surf(20, filled = false)
        return iv
    }

    // ================= DECODE С УМЕНЬШЕНИЕМ (анти-OOM) =================
    private fun decodeScaled(b64: String, reqPx: Int): Bitmap? {
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val req = reqPx.coerceAtLeast(1)
            while (bounds.outWidth / sample / 2 >= req && bounds.outHeight / sample / 2 >= req) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) {
            null
        }
    }

    // ================= АВАТАРКИ =================
    private fun makeCircularBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val sq = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(sq, targetSize, targetSize, true)
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f - 1f, paint)
        return out
    }

    private fun createAvatarBitmap(nick: String): Bitmap {
        val size = dp(100)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = P.border
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val textPaint = Paint()
        textPaint.color = P.textSec
        textPaint.textSize = size / 2.5f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
        textPaint.typeface = fBold
        val initial = if (nick.isNotEmpty()) nick[0].uppercaseChar() else '?'
        val metrics = textPaint.fontMetrics
        val y = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initial.toString(), size / 2f, y, textPaint)
        return bmp
    }

    private fun avatarView(bmp: Bitmap?, size: Int, nick: String): ImageView {
        val iv = ImageView(this)
        iv.layoutParams = lp(size, size, 0)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setImageBitmap(bmp ?: makeCircularBitmap(createAvatarBitmap(nick), size))
        return iv
    }

    // ================= СОСТОЯНИЕ =================
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var tabs: Array<View>
    private lateinit var navIcons: Array<ImageView>

    private lateinit var authBlock: LinearLayout
    private lateinit var feedBlock: LinearLayout
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
    private lateinit var regBtn: TextView
    private lateinit var logBtn: TextView
    private var currentUserId = ""
    private var currentNick = ""
    private var currentEmail = ""
    private var currentAvatar = ""
    private val pendingImages = ArrayList<String>()
    private var isRegistering = false
    private var isLoggingIn = false
    private var lastFeedSig = ""

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

    private lateinit var profileRoot: LinearLayout
    private lateinit var profileAvatar: ImageView
    private lateinit var profileNick: TextView
    private lateinit var profileName: TextView
    private lateinit var profileBio: TextView
    private lateinit var profilePostsCount: TextView
    private lateinit var profileFollowersCount: TextView
    private lateinit var profileFollowingCount: TextView
    private lateinit var profileLikesCount: TextView
    private lateinit var profileBtn1: TextView
    private lateinit var profileBtn2: TextView
    private lateinit var profileEditIv: ImageView
    private lateinit var profileSettingsIv: ImageView
    private lateinit var profileTabPosts: ImageView
    private lateinit var profileTabLikes: ImageView
    private lateinit var profilePostsGrid: LinearLayout
    private lateinit var profileScroll: ScrollView
    private var profileMode = 0
    private var viewingUserId = ""
    private var viewingNick = ""
    private var editAvatarView: ImageView? = null

    private val PICK_IMAGES = 1002
    private val PICK_AVATAR = 1003
    private val ZOOM_MAX = 3f
    private val FEED_LIMIT = 40

    private var feedListener: ValueEventListener? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private val avatarCache = HashMap<String, Bitmap>()
    private val viewedLocally = HashSet<String>()

    // ================= МУЛЬТИАККАУНТ (SharedPreferences) =================
    private fun saveAccount(uid: String, nick: String, email: String) {
        if (uid.isEmpty()) return
        val prefs = getSharedPreferences("ahem", Context.MODE_PRIVATE)
        val raw = prefs.getString("accounts", "") ?: ""
        val list = ArrayList<Triple<String, String, String>>()
        for (rec in raw.split("\u0002")) {
            if (rec.isEmpty()) continue
            val parts = rec.split("\u0001")
            if (parts.size == 3) list.add(Triple(parts[0], parts[1], parts[2]))
        }
        val filtered = list.filter { it.first != uid }
        filtered.add(Triple(uid, nick, email))
        val out = filtered.joinToString("\u0002") { "${it.first}\u0001${it.second}\u0001${it.third}" }
        prefs.edit().putString("accounts", out).apply()
    }

    private fun loadAccounts(): List<Triple<String, String, String>> {
        val prefs = getSharedPreferences("ahem", Context.MODE_PRIVATE)
        val raw = prefs.getString("accounts", "") ?: ""
        val list = ArrayList<Triple<String, String, String>>()
        for (rec in raw.split("\u0002")) {
            if (rec.isEmpty()) continue
            val parts = rec.split("\u0001")
            if (parts.size == 3) list.add(Triple(parts[0], parts[1], parts[2]))
        }
        return list
    }

    // ================= ЛАЙКИ =================
    private fun heartSet(iv: ImageView, liked: Boolean) {
        iv.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        iv.setColorFilter(if (liked) P.heartOn else P.textSec)
    }

    private fun attachLike(
        root: View,
        countTv: TextView,
        heartIv: ImageView,
        postId: String,
        initialLiked: Boolean
    ) {
        val liked = booleanArrayOf(initialLiked)
        heartSet(heartIv, initialLiked)
        root.setOnClickListener {
            if (currentUserId.isEmpty()) return@setOnClickListener
            val prevLiked = liked[0]
            val prevCount = parseCount(countTv)
            liked[0] = !prevLiked
            countTv.text = "♥ " + if (liked[0]) fmtNum(prevCount + 1L) else fmtNum(maxOf(0, prevCount - 1).toLong())
            heartSet(heartIv, liked[0])
            database.child("posts").child(postId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val likes = (mutableData.child("likes").value as? Number)?.toInt() ?: 0
                    val likedBy = mutableData.child("likedBy")
                    if (likedBy.child(currentUserId).value != null) {
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
                        liked[0] = data.child("likedBy").child(currentUserId).value != null
                        countTv.text = "♥ " + fmtNum(newCount.toLong())
                        heartSet(heartIv, liked[0])
                    } else {
                        liked[0] = prevLiked
                        countTv.text = "♥ " + fmtNum(prevCount.toLong())
                        heartSet(heartIv, prevLiked)
                    }
                }
            })
        }
    }

    // ================= ПРОСМОТРЫ (1 на пользователя, серверная транзакция) =================
    private fun incrementViews(postId: String, onDone: ((Long) -> Unit)?) {
        if (currentUserId.isEmpty()) return
        if (postId in viewedLocally) return
        database.child("posts").child(postId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val views = (mutableData.child("views").value as? Number)?.toLong() ?: 0L
                val viewedBy = mutableData.child("viewedBy")
                if (viewedBy.child(currentUserId).value != null) {
                    viewedLocally.add(postId)
                    return Transaction.success(mutableData)
                }
                viewedBy.child(currentUserId).value = true
                mutableData.child("views").value = views + 1
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                if (committed && data != null) {
                    val nv = (data.child("views").value as? Number)?.toLong() ?: 0L
                    viewedLocally.add(postId)
                    onDone?.invoke(nv)
                }
            }
        })
    }

    private fun updatePostsNick(newNick: String) {
        database.child("posts").orderByChild("authorId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (p in snapshot.children) p.ref.child("authorNick").setValue(newNick)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun isNickTaken(nick: String, excludeUid: String, callback: (Boolean) -> Unit) {
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var taken = false
                for (u in snapshot.children) {
                    val n = u.child("nick").getValue(String::class.java) ?: ""
                    if (n.equals(nick, ignoreCase = true) && u.key != excludeUid) {
                        taken = true
                        break
                    }
                }
                callback(taken)
            }
            override fun onCancelled(error: DatabaseError) { callback(false) }
        })
    }

    // ================= СОЗДАНИЕ =================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(isDark())

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(P.bg)

        val content = FrameLayout(this)
        content.layoutParams = lp(-1, 0, 0)
        wt(content, 1f)

        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChats()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))

        val navWrap = FrameLayout(this)
        navWrap.layoutParams = lp(-1, -2, 0)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = surf(26, filled = true)
        nav.setPadding(dp(10), dp(8), dp(10), dp(8))
        val nlp = FrameLayout.LayoutParams(-1, -2)
        nlp.gravity = Gravity.CENTER_HORIZONTAL
        nlp.setMargins(dp(16), 0, dp(16), dp(12))
        nav.layoutParams = nlp

        val icons = intArrayOf(R.drawable.ic_feed, R.drawable.ic_video, R.drawable.ic_chat, R.drawable.ic_profile)
        navIcons = Array(4) { i ->
            val iv = icon(icons[i], P.navInactive, dp(24))
            iv.setPadding(dp(4), dp(4), dp(4), dp(4))
            val p = LinearLayout.LayoutParams(0, dp(40), 1f)
            iv.layoutParams = p
            iv.setOnClickListener { showTab(i) }
            iv
        }
        for (iv in navIcons) nav.addView(iv)

        navWrap.addView(nav)
        root.addView(content)
        root.addView(navWrap)
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
                            currentEmail = snapshot.child("email").getValue(String::class.java) ?: (auth.currentUser?.email ?: "")
                            saveAccount(currentUserId, currentNick, currentEmail)
                            if (::feedBlock.isInitialized && feedBlock.visibility == View.GONE) enterFeedMode()
                            if (::profileRoot.isInitialized) refreshProfile()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else if (user == null && currentUserId.isNotEmpty()) {
                currentUserId = ""
                currentNick = ""
                currentEmail = ""
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
        for (idx in 0 until 4) navIcons[idx].setColorFilter(if (idx == i) P.accent else P.navInactive)
        if (i == 3) {
            viewingUserId = currentUserId
            viewingNick = ""
            profileMode = 0
            refreshProfile()
        }
    }

    // ================= ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(P.bg)

        authBlock = LinearLayout(this)
        authBlock.orientation = LinearLayout.VERTICAL
        authBlock.setPadding(dp(24), dp(48), dp(24), 0)

        val logo = tv("Ahem", 34f, fBold, P.text)
        val tag = tv("social", 13f, fMed, P.textSec)
        tag.layoutParams = lp(-2, -2, dp(32))

        feedEmail = field("Email")
        feedEmail.layoutParams = lp(-1, -2, dp(10))
        feedNick = field("Ник (мин. 5 символов)")
        feedNick.layoutParams = lp(-1, -2, dp(10))
        feedPass = field("Пароль")
        feedPass.inputType = 1
        feedPass.layoutParams = lp(-1, -2, dp(16))

        val rowAuth = LinearLayout(this)
        rowAuth.orientation = LinearLayout.HORIZONTAL
        rowAuth.layoutParams = lp(-1, -2, dp(12))
        regBtn = btnAccent("Регистрация")
        regBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, dp(6))
        wt(regBtn, 1f)
        logBtn = btnOutline("Вход")
        logBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(logBtn, 1f)
        rowAuth.addView(regBtn)
        rowAuth.addView(logBtn)

        feedStatus = tv("не залогинен", 13f, fReg, P.textSec)

        authBlock.addView(logo)
        authBlock.addView(tag)
        authBlock.addView(feedEmail)
        authBlock.addView(feedNick)
        authBlock.addView(feedPass)
        authBlock.addView(rowAuth)
        authBlock.addView(feedStatus)

        feedBlock = LinearLayout(this)
        feedBlock.orientation = LinearLayout.VERTICAL
        feedBlock.visibility = View.GONE
        feedBlock.setBackgroundColor(P.bg)

        // Верх ленты: ТОЛЬКО лого (ник и карандаш убраны)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(18), dp(14), dp(18), dp(10))
        val logoS = tv("Ahem", 20f, fBold, P.text)
        logoS.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(logoS, 1f)
        top.addView(logoS)

        val composer = LinearLayout(this)
        composer.orientation = LinearLayout.VERTICAL
        composer.background = surf(18, filled = true)
        composer.setPadding(dp(14), dp(14), dp(14), dp(14))
        val compM = LinearLayout.LayoutParams(-1, -2)
        compM.setMargins(dp(14), 0, dp(14), dp(12))
        composer.layoutParams = compM

        feedPostText = field("Подпись к посту (до 4 фото)")
        feedPostText.layoutParams = lp(-1, -2, dp(10))

        pendingWrap = LinearLayout(this)
        pendingWrap.orientation = LinearLayout.VERTICAL
        pendingWrap.visibility = View.GONE
        pendingWrap.layoutParams = lp(-1, -2, dp(10))
        val pendHead = LinearLayout(this)
        pendHead.orientation = LinearLayout.HORIZONTAL
        pendHead.gravity = Gravity.CENTER_VERTICAL
        pendingInfo = tv("", 12f, fMed, P.textSec)
        pendingInfo.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pendingInfo, 1f)
        val clearIv = iconRound(R.drawable.ic_close, P.text, dp(30))
        pendHead.addView(pendingInfo)
        pendHead.addView(clearIv)
        val pendScroll = HorizontalScrollView(this)
        pendScroll.layoutParams = lp(-1, -2, 0)
        pendScroll.isHorizontalScrollBarEnabled = false
        pendingRow = LinearLayout(this)
        pendingRow.orientation = LinearLayout.HORIZONTAL
        pendScroll.addView(pendingRow)
        pendingWrap.addView(pendHead)
        pendingWrap.addView(pendScroll)

        val cRow = LinearLayout(this)
        cRow.orientation = LinearLayout.HORIZONTAL
        cRow.gravity = Gravity.CENTER_VERTICAL
        val camIv = iconRound(R.drawable.ic_camera, P.text, dp(44))
        val pubBtn = btnAccent("Опубликовать")
        pubBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pubBtn, 1f)
        (pubBtn.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
        cRow.addView(camIv)
        cRow.addView(pubBtn)

        composer.addView(feedPostText)
        composer.addView(pendingWrap)
        composer.addView(cRow)

        val rowAct = LinearLayout(this)
        rowAct.orientation = LinearLayout.HORIZONTAL
        rowAct.gravity = Gravity.CENTER_VERTICAL
        val raM = LinearLayout.LayoutParams(-1, -2)
        raM.setMargins(dp(14), 0, dp(14), dp(12))
        rowAct.layoutParams = raM
        val feedBtn = btnOutline("⟳ Лента")
        feedBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedBtn, 1f)
        feedFollowNick = field("@ник")
        feedFollowNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedFollowNick, 2f)
        (feedFollowNick.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        val folBtn = btnOutline("Подписаться")
        folBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(folBtn, 1f)
        (folBtn.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
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

        feedBlock.addView(top)
        feedBlock.addView(composer)
        feedBlock.addView(rowAct)
        feedBlock.addView(feedScroll)

        regBtn.setOnClickListener {
            if (isRegistering) return@setOnClickListener
            val email = feedEmail.text.toString().trim()
            val nick = feedNick.text.toString().trim()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) {
                feedStatus.text = "Заполни все поля"
                return@setOnClickListener
            }
            if (nick.length < 5) {
                feedStatus.text = "Ник минимум 5 символов"
                return@setOnClickListener
            }
            isRegistering = true
            regBtn.text = "..."
            isNickTaken(nick, "") { taken ->
                if (taken) {
                    feedStatus.text = "Ник уже занят"
                    isRegistering = false
                    regBtn.text = "Регистрация"
                    return@isNickTaken
                }
                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        database.child("users").child(userId).child("email").setValue(email)
                        database.child("users").child(userId).child("nick").setValue(nick)
                        currentUserId = userId
                        currentNick = nick
                        currentEmail = email
                        viewingUserId = currentUserId
                        saveAccount(userId, nick, email)
                        feedStatus.text = "Зарегистрирован!"
                        enterFeedMode()
                    } else {
                        feedStatus.text = "Ошибка: ${task.exception?.message}"
                    }
                    isRegistering = false
                    regBtn.text = "Регистрация"
                }
            }
        }

        logBtn.setOnClickListener {
            if (isLoggingIn) return@setOnClickListener
            val email = feedEmail.text.toString().trim()
            val pass = feedPass.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                feedStatus.text = "Email + пароль"
                return@setOnClickListener
            }
            isLoggingIn = true
            logBtn.text = "..."
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                    currentUserId = userId
                    currentEmail = email
                    viewingUserId = currentUserId
                    database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.child("nick").getValue(String::class.java) ?: "Аноним"
                            currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                            saveAccount(userId, currentNick, email)
                            enterFeedMode()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                } else {
                    feedStatus.text = "Ошибка: ${task.exception?.message}"
                }
                isLoggingIn = false
                logBtn.text = "Вход"
            }
        }

        clearIv.setOnClickListener {
            pendingImages.clear()
            pendingWrap.visibility = View.GONE
        }

        camIv.setOnClickListener {
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
            post["views"] = 0
            post["likedBy"] = HashMap<String, Boolean>()
            post["viewedBy"] = HashMap<String, Boolean>()
            database.child("posts").child(postId).setValue(post)
            feedPostText.setText("")
            pendingImages.clear()
            pendingWrap.visibility = View.GONE
        }

        feedBtn.setOnClickListener { loadFeed() }

        folBtn.setOnClickListener {
            val n = feedFollowNick.text.toString().trim()
            if (n.isNotEmpty()) {
                addKnown(n)
                feedFollowNick.setText("")
                Toast.makeText(this, "Подписаны на $n", Toast.LENGTH_SHORT).show()
            }
        }

        col.addView(authBlock)
        col.addView(feedBlock)
        return col
    }

    private fun enterFeedMode() {
        authBlock.visibility = View.GONE
        feedBlock.visibility = View.VISIBLE
        loadFeed()
    }

    private fun loadFeed() {
        feedListener?.let { database.child("posts").removeEventListener(it) }
        feedBox.removeAllViews()
        lastFeedSig = ""

        // ФИКС: читаем children напрямую (без orderByChild) и сортируем на клиенте.
        feedListener = database.child("posts").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = ArrayList<DataSnapshot>()
                for (child in snapshot.children) posts.add(child)
                val sb = StringBuilder()
                for (p in posts) {
                    sb.append(p.key).append('~')
                    sb.append(getTs(p)).append('~')
                    sb.append(p.child("authorNick").getValue(String::class.java) ?: "").append('~')
                    sb.append(p.child("text").getValue(String::class.java) ?: "").append('~')
                    sb.append(p.child("images").getValue(String::class.java) ?: "").append(';')
                }
                val sig = sb.toString()
                if (sig == lastFeedSig) return
                lastFeedSig = sig
                feedBox.removeAllViews()

                val sorted = posts.sortedByDescending { getTs(it) }.take(FEED_LIMIT)
                for (post in sorted) {
                    try {
                        feedBox.addView(buildPostCard(post))
                    } catch (e: Exception) {
                        // пропускаем проблемный пост
                    }
                }
                if (posts.isEmpty()) feedBox.addView(tv("Лента пуста", 15f, fMed, P.textSec))
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ================= ЕДИНАЯ КАРТОЧКА ПОСТА (лента + детальное окно) =================
    private fun buildPostCard(post: DataSnapshot): View {
        val postId = post.key ?: ""
        val authorId = post.child("authorId").getValue(String::class.java) ?: ""
        val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
        val text = post.child("text").getValue(String::class.java) ?: ""
        val imagesStr = post.child("images").getValue(String::class.java) ?: ""
        val ts = getTs(post)
        val likeCount = (post.child("likes").value as? Number)?.toLong() ?: 0L
        val viewCount = (post.child("views").value as? Number)?.toLong() ?: 0L
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        val imgW = resources.displayMetrics.widthPixels - dp(28)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = surf(18, filled = true)
        val cm2 = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
        cm2.setMargins(dp(14), 0, dp(14), dp(12))
        card.layoutParams = cm2

        // Шапка
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.setPadding(dp(14), dp(14), dp(14), dp(10))
        header.gravity = Gravity.CENTER_VERTICAL
        val avatarSize = dp(40)
        val avatarImg = ImageView(this)
        val avParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
        avParams.marginEnd = dp(12)
        avatarImg.layoutParams = avParams
        avatarImg.scaleType = ImageView.ScaleType.FIT_CENTER
        if (avatarCache.containsKey(authorId)) {
            avatarImg.setImageBitmap(avatarCache[authorId])
        } else {
            avatarImg.setImageBitmap(makeCircularBitmap(createAvatarBitmap(authorNick), avatarSize))
            database.child("users").child(authorId).child("avatar")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(avatarSnapshot: DataSnapshot) {
                        val s = avatarSnapshot.getValue(String::class.java) ?: ""
                        if (s.isNotEmpty()) {
                            val b = decodeScaled(s, avatarSize)
                            if (b != null) {
                                val circ = makeCircularBitmap(b, avatarSize)
                                avatarCache[authorId] = circ
                                avatarImg.setImageBitmap(circ)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
        avatarImg.setOnClickListener { openUserProfile(authorId, authorNick) }
        val nickTime = LinearLayout(this)
        nickTime.orientation = LinearLayout.VERTICAL
        val np = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
        np.weight = 1f
        nickTime.layoutParams = np
        val nickView = tv(authorNick, 15f, fSemi, P.text)
        nickView.setOnClickListener { openUserProfile(authorId, authorNick) }
        val timeView = tv(timeStr, 12f, fReg, P.textSec)
        nickTime.addView(nickView)
        nickTime.addView(timeView)
        header.addView(avatarImg)
        header.addView(nickTime)
        card.addView(header)

        // Текст
        if (text.isNotEmpty()) {
            val bt = TextView(this)
            bt.typeface = fReg
            bt.textSize = 15f
            bt.setTextColor(P.text)
            bt.setPadding(dp(14), 0, dp(14), dp(12))
            bt.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
            Linkify.addLinks(bt, Linkify.WEB_URLS)
            bt.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            bt.setLinkTextColor(P.text)
            bt.text = text
            card.addView(bt)
        }

        // Фото
        if (imagesStr.isNotEmpty()) {
            val images = imagesStr.split(",").filter { it.isNotEmpty() }
            if (images.size == 1) {
                val b = decodeScaled(images[0], imgW)
                if (b != null) {
                    val box = FrameLayout(this)
                    val bm = LinearLayout.LayoutParams(-1, imgW)
                    bm.setMargins(dp(14), 0, dp(14), dp(12))
                    box.layoutParams = bm
                    box.background = surf(14, filled = true)
                    box.clipToOutline = true
                    val iv = ImageView(this)
                    iv.setImageBitmap(b)
                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                    iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                    iv.setOnClickListener { openViewer(images, 0) }
                    box.addView(iv)
                    card.addView(box)
                }
            } else {
                val grid = LinearLayout(this)
                grid.orientation = LinearLayout.VERTICAL
                val gm = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                gm.setMargins(dp(14), 0, dp(14), dp(12))
                grid.layoutParams = gm
                val cell = (imgW - dp(4)) / 2
                var ri = 0
                while (ri < images.size) {
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, dp(4))
                    for (ci in 0 until 2) {
                        val idx = ri + ci
                        if (idx < images.size) {
                            val b = decodeScaled(images[idx], cell)
                            if (b != null) {
                                val box = FrameLayout(this)
                                box.layoutParams = lp(0, cell, 0)
                                wt(box, 1f)
                                (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (ci == 0) dp(4) else 0
                                box.background = surf(12, filled = true)
                                box.clipToOutline = true
                                val iv = ImageView(this)
                                iv.setImageBitmap(b)
                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                val kk = idx
                                iv.setOnClickListener { openViewer(images, kk) }
                                box.addView(iv)
                                row.addView(box)
                            } else {
                                val empty = View(this)
                                empty.layoutParams = lp(0, cell, 0)
                                wt(empty, 1f)
                                (empty.layoutParams as LinearLayout.LayoutParams).marginEnd = if (ci == 0) dp(4) else 0
                                row.addView(empty)
                            }
                        } else {
                            val empty = View(this)
                            empty.layoutParams = lp(0, cell, 0)
                            wt(empty, 1f)
                            (empty.layoutParams as LinearLayout.LayoutParams).marginEnd = if (ci == 0) dp(4) else 0
                            row.addView(empty)
                        }
                    }
                    grid.addView(row)
                    ri += 2
                }
                card.addView(grid)
            }
        }

        // Действия: лайк + просмотры + подписка
        val act = LinearLayout(this)
        act.orientation = LinearLayout.HORIZONTAL
        act.setPadding(dp(14), dp(4), dp(14), dp(12))
        act.gravity = Gravity.CENTER_VERTICAL

        val heartIv = icon(R.drawable.ic_heart_outline, P.textSec, dp(22))
        val countTv = tv("♥ " + fmtNum(likeCount), 14f, fSemi, P.text)
        (countTv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(6)
        val likeBox = LinearLayout(this)
        likeBox.orientation = LinearLayout.HORIZONTAL
        likeBox.gravity = Gravity.CENTER_VERTICAL
        likeBox.addView(heartIv)
        likeBox.addView(countTv)
        val likedBy = post.child("likedBy").children.mapNotNull { it.key }.toSet()
        attachLike(likeBox, countTv, heartIv, postId, currentUserId in likedBy)
        act.addView(likeBox)

        val eyeIv = icon(R.drawable.ic_eye, P.textSec, dp(20))
        val viewsTv = tv(fmtNum(viewCount), 13f, fMed, P.textSec)
        (viewsTv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(6)
        (viewsTv.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        val viewsBox = LinearLayout(this)
        viewsBox.orientation = LinearLayout.HORIZONTAL
        viewsBox.gravity = Gravity.CENTER_VERTICAL
        (viewsBox.layoutParams as LinearLayout.LayoutParams).marginStart = dp(16)
        viewsBox.addView(eyeIv)
        viewsBox.addView(viewsTv)
        act.addView(viewsBox)

        if (authorId != currentUserId && authorId.isNotEmpty()) {
            val fb = btnOutline("+ @$authorNick")
            fb.textSize = 12f
            (fb.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
            fb.setOnClickListener { addKnown(authorNick) }
            act.addView(fb)
        }
        card.addView(act)
        return card
    }

    // ================= ДЕТАЛЬНОЕ ОКНО ПОСТА =================
    private fun openPostDetail(post: DataSnapshot) {
        val postId = post.key ?: ""
        incrementViews(postId, null)
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(P.bg)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(10), dp(14), dp(10))
        val backIv = iconRound(R.drawable.ic_back, P.text, dp(38))
        backIv.setOnClickListener { dlg.dismiss() }
        val title = tv("Пост", 17f, fBold, P.text)
        title.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(title, 1f)
        (title.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
        bar.addView(backIv)
        bar.addView(title)
        outer.addView(bar)

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        holder.setPadding(0, dp(6), 0, dp(20))
        try {
            holder.addView(buildPostCard(post))
        } catch (e: Exception) {
            holder.addView(tv("Не удалось загрузить пост", 14f, fMed, P.textSec))
        }
        scroll.addView(holder)
        val slp = LinearLayout.LayoutParams(-1, 0)
        slp.weight = 1f
        outer.addView(scroll, slp)

        dlg.setContentView(outer)
        dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        dlg.show()
    }

    private fun openUserProfile(userId: String, nick: String) {
        viewingUserId = userId
        viewingNick = nick
        profileMode = 0
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
        back.typeface = fBold
        back.setPadding(dp(16), dp(10), dp(16), dp(10))
        back.setBackgroundColor(P.scrim)
        val blp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        blp.gravity = Gravity.TOP or Gravity.START
        frame.addView(back, blp)
        back.setOnClickListener { dlg.dismiss() }

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(P.scrim)
        val prevB = Button(this)
        prevB.text = "◀"
        prevB.setTextColor(0xFFFFFFFF.toInt())
        prevB.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(prevB, 1f)
        val cnt = TextView(this)
        cnt.setTextColor(0xFFFFFFFF.toInt())
        cnt.gravity = Gravity.CENTER
        cnt.typeface = fSemi
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
        var scale = 1f
        var minS = 1f
        var maxS = ZOOM_MAX
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
            applyM()
        }

        fun show() {
            val req = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
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

        val sgd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(minS, maxS)
                applyM()
                return true
            }
        })

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
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

    // ================= ЧАТЫ =================
    private fun buildChats(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(P.bg)
        col.setPadding(dp(18), dp(14), dp(18), 0)

        chatListRoot = LinearLayout(this)
        chatListRoot.orientation = LinearLayout.VERTICAL
        val head = tv("Сообщения", 24f, fBold, P.text)
        head.layoutParams = lp(-1, -2, dp(6))
        chatStatus = tv("подключено", 12f, fReg, P.textSec)
        chatStatus.layoutParams = lp(-1, -2, dp(14))
        val rowSearch = LinearLayout(this)
        rowSearch.orientation = LinearLayout.HORIZONTAL
        rowSearch.layoutParams = lp(-1, -2, dp(14))
        searchField = field("Поиск по нику")
        searchField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchField, 2f)
        val searchIv = iconRound(R.drawable.ic_search, P.text, dp(46))
        searchIv.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchIv, 1f)
        (searchIv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        rowSearch.addView(searchField)
        rowSearch.addView(searchIv)
        searchResults = LinearLayout(this)
        searchResults.orientation = LinearLayout.VERTICAL
        chatListRows = LinearLayout(this)
        chatListRows.orientation = LinearLayout.VERTICAL
        val listScroll = ScrollView(this)
        listScroll.addView(chatListRows)
        listScroll.layoutParams = lp(-1, 0, 0)
        wt(listScroll, 1f)
        listScroll.isVerticalScrollBarEnabled = false
        chatListRoot.addView(head)
        chatListRoot.addView(chatStatus)
        chatListRoot.addView(rowSearch)
        chatListRoot.addView(searchResults)
        chatListRoot.addView(listScroll)

        convRoot = LinearLayout(this)
        convRoot.orientation = LinearLayout.VERTICAL
        convRoot.visibility = View.GONE
        convRoot.setBackgroundColor(P.bg)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(4), dp(4), dp(4), dp(10))
        top.layoutParams = lp(-1, -2, 0)
        val backIv = iconRound(R.drawable.ic_back, P.text, dp(38))
        convTitle = tv("", 17f, fSemi, P.text)
        convTitle.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(convTitle, 1f)
        (convTitle.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
        top.addView(backIv)
        top.addView(convTitle)
        convBox = LinearLayout(this)
        convBox.orientation = LinearLayout.VERTICAL
        convScroll = ScrollView(this)
        convScroll.addView(convBox)
        convScroll.layoutParams = lp(-1, 0, 0)
        wt(convScroll, 1f)
        convScroll.isVerticalScrollBarEnabled = false
        msgField = field("Сообщение...")
        val sendIv = ImageView(this)
        sendIv.setImageResource(R.drawable.ic_send)
        sendIv.setColorFilter(P.accentText)
        sendIv.setPadding(dp(12), dp(12), dp(12), dp(12))
        sendIv.layoutParams = lp(dp(46), dp(46), 0)
        sendIv.background = accentBg(14)
        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.layoutParams = lp(-1, -2, dp(10))
        msgField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(msgField, 1f)
        (sendIv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        inputRow.addView(msgField)
        inputRow.addView(sendIv)
        convRoot.addView(top)
        convRoot.addView(convScroll)
        convRoot.addView(inputRow)

        searchIv.setOnClickListener {
            val q = searchField.text.toString().trim().lowercase()
            if (q.isEmpty()) return@setOnClickListener
            searchResults.removeAllViews()
            database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (u in snapshot.children) {
                        val nick = u.child("nick").getValue(String::class.java) ?: ""
                        if (nick.lowercase().contains(q) && u.key != currentUserId) {
                            val row = LinearLayout(this@MainActivity)
                            row.orientation = LinearLayout.HORIZONTAL
                            row.gravity = Gravity.CENTER_VERTICAL
                            row.background = surf(14, filled = true)
                            row.layoutParams = lp(-1, -2, dp(6))
                            row.setPadding(dp(12), dp(10), dp(12), dp(10))
                            row.addView(avatarView(null, dp(34), nick))
                            val nt = tv(nick, 15f, fMed, P.text)
                            (nt.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
                            row.addView(nt)
                            row.setOnClickListener { openChat(u.key ?: "", nick); searchResults.removeAllViews() }
                            searchResults.addView(row)
                        }
                    }
                    if (searchResults.childCount == 0) searchResults.addView(tv("Никого не нашли", 14f, fMed, P.textSec))
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        backIv.setOnClickListener {
            currentChat = ""
            convRoot.visibility = View.GONE
            chatListRoot.visibility = View.VISIBLE
        }

        sendIv.setOnClickListener {
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
                        val text = msg.child("text").getValue(String::class.java) ?: ""
                        val ts = msg.child("timestamp").getValue(Long::class.java) ?: 0
                        val isMe = msg.child("senderId").getValue(String::class.java) == currentUserId
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                        val row = LinearLayout(this@MainActivity)
                        row.orientation = LinearLayout.HORIZONTAL
                        row.gravity = if (isMe) Gravity.END else Gravity.START
                        row.layoutParams = lp(-1, -2, dp(4))
                        val bubble = LinearLayout(this@MainActivity)
                        bubble.orientation = LinearLayout.VERTICAL
                        if (isMe) {
                            bubble.background = accentBg(18)
                        } else {
                            bubble.background = surf(18, filled = true)
                        }
                        bubble.setPadding(dp(14), dp(10), dp(14), dp(6))
                        val mt = tv(text, 15f, fReg, if (isMe) P.accentText else P.text)
                        val tt = tv(timeStr, 10f, fReg, if (isMe) P.accentText else P.textSec)
                        tt.gravity = Gravity.END
                        bubble.addView(mt)
                        bubble.addView(tt)
                        row.addView(bubble)
                        convBox.addView(row)
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
        col.gravity = Gravity.CENTER
        col.setBackgroundColor(P.bg)
        col.setPadding(dp(24), dp(24), dp(24), 24)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.background = surf(20, filled = true)
        card.setPadding(dp(28), dp(40), dp(28), dp(40))
        card.addView(icon(R.drawable.ic_video, P.text, dp(48)))
        val t = tv("Видео скоро", 18f, fBold, P.text)
        t.layoutParams = lp(-2, -2, dp(6))
        t.gravity = Gravity.CENTER
        val s = tv("Здесь появится лента клипов", 14f, fReg, P.textSec)
        s.gravity = Gravity.CENTER
        card.addView(t)
        card.addView(s)
        col.addView(card)
        return col
    }

    // ================= ПРОФИЛЬ =================
    private fun buildProfile(): View {
        profileRoot = LinearLayout(this)
        profileRoot.orientation = LinearLayout.VERTICAL
        profileRoot.setBackgroundColor(P.bg)
        profileScroll = ScrollView(this)
        profileScroll.layoutParams = lp(-1, -1)
        profileScroll.isVerticalScrollBarEnabled = false
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(14), dp(18), 0)

        val nickRow = LinearLayout(this)
        nickRow.orientation = LinearLayout.HORIZONTAL
        nickRow.gravity = Gravity.CENTER_VERTICAL
        nickRow.layoutParams = lp(-1, -2, dp(16))
        profileNick = tv("@", 22f, fBold, P.text)
        profileNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(profileNick, 1f)
        profileSettingsIv = iconRound(R.drawable.ic_settings, P.text, dp(38))
        profileSettingsIv.setOnClickListener { showSettingsDialog() }
        profileEditIv = iconRound(R.drawable.ic_edit, P.text, dp(38))
        (profileEditIv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        nickRow.addView(profileNick)
        nickRow.addView(profileSettingsIv)
        nickRow.addView(profileEditIv)
        content.addView(nickRow)

        val infoRow = LinearLayout(this)
        infoRow.orientation = LinearLayout.HORIZONTAL
        infoRow.gravity = Gravity.CENTER_VERTICAL
        infoRow.layoutParams = lp(-1, -2, dp(16))
        profileAvatar = ImageView(this)
        profileAvatar.layoutParams = lp(dp(84), dp(84), 0)
        (profileAvatar.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(20)
        profileAvatar.scaleType = ImageView.ScaleType.FIT_CENTER
        profilePostsCount = TextView(this)
        profileFollowersCount = TextView(this)
        profileFollowingCount = TextView(this)
        profileLikesCount = TextView(this)
        infoRow.addView(profileAvatar)
        infoRow.addView(statCol(profilePostsCount, "посты"))
        infoRow.addView(statCol(profileFollowersCount, "подписчики"))
        infoRow.addView(statCol(profileFollowingCount, "подписки"))
        infoRow.addView(statCol(profileLikesCount, "лайки"))
        content.addView(infoRow)

        profileName = tv("", 15f, fSemi, P.text)
        profileName.layoutParams = lp(-1, -2, dp(2))
        profileBio = tv("", 14f, fReg, P.text)
        profileBio.layoutParams = lp(-1, -2, dp(14))
        content.addView(profileName)
        content.addView(profileBio)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.layoutParams = lp(-1, -2, dp(16))
        profileBtn1 = btnOutline("Редактировать профиль")
        profileBtn1.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, dp(8))
        wt(profileBtn1, 1f)
        profileBtn2 = btnOutline("Поделиться")
        profileBtn2.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(profileBtn2, 1f)
        btnRow.addView(profileBtn1)
        btnRow.addView(profileBtn2)
        content.addView(btnRow)

        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.background = surf(14, filled = true)
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4))
        tabRow.layoutParams = lp(-1, -2, dp(2))
        profileTabPosts = icon(R.drawable.ic_grid, P.text, dp(22))
        profileTabPosts.setPadding(dp(8), dp(8), dp(8), dp(8))
        profileTabPosts.layoutParams = lp(0, dp(38), 0)
        wt(profileTabPosts, 1f)
        profileTabLikes = icon(R.drawable.ic_heart, P.navInactive, dp(22))
        profileTabLikes.setPadding(dp(8), dp(8), dp(8), dp(8))
        profileTabLikes.layoutParams = lp(0, dp(38), 0)
        wt(profileTabLikes, 1f)
        profileTabPosts.setOnClickListener { profileMode = 0; refreshProfile() }
        profileTabLikes.setOnClickListener { profileMode = 1; refreshProfile() }
        tabRow.addView(profileTabPosts)
        tabRow.addView(profileTabLikes)
        content.addView(tabRow)

        profilePostsGrid = LinearLayout(this)
        profilePostsGrid.orientation = LinearLayout.VERTICAL
        profilePostsGrid.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        content.addView(profilePostsGrid)

        profileScroll.addView(content)
        profileRoot.addView(profileScroll)
        return profileRoot
    }

    private fun statCol(num: TextView, label: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(c, 1f)
        num.gravity = Gravity.CENTER
        num.textSize = 18f
        num.typeface = fBold
        num.setTextColor(P.text)
        val l = tv(label, 11f, fReg, P.textSec)
        l.gravity = Gravity.CENTER
        c.addView(num)
        c.addView(l)
        return c
    }

    // ================= НАСТРОЙКИ (тема + аккаунт) =================
    private fun showSettingsDialog() {
        val dlg = Dialog(this)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = surf(20, filled = true)
        root.setPadding(dp(20), dp(18), dp(20), dp(20))
        val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
        val wrap = FrameLayout(this)
        val rlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
        rlp.gravity = Gravity.CENTER
        wrap.addView(root, rlp)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.layoutParams = lp(-1, -2, dp(16))
        val title = tv("Настройки", 18f, fBold, P.text)
        title.layoutParams = lp(0, -2, 0)
        wt(title, 1f)
        val closeIv = iconRound(R.drawable.ic_close, P.text, dp(34))
        closeIv.setOnClickListener { dlg.dismiss() }
        head.addView(title)
        head.addView(closeIv)
        root.addView(head)

        val themeLbl = tv("Тема оформления", 13f, fMed, P.textSec)
        themeLbl.layoutParams = lp(-1, -2, dp(10))
        root.addView(themeLbl)
        val darkNow = isDark()
        val lightOpt = themeOption("Светлая", !darkNow)
        lightOpt.setOnClickListener { setThemeAndRestart(false); dlg.dismiss() }
        val darkOpt = themeOption("Тёмная", darkNow)
        darkOpt.setOnClickListener { setThemeAndRestart(true); dlg.dismiss() }
        root.addView(lightOpt)
        val gap = View(this)
        gap.layoutParams = lp(-1, dp(8), 0)
        root.addView(gap)
        root.addView(darkOpt)

        val sep = View(this)
        sep.layoutParams = lp(-1, dp(1), dp(16))
        sep.setBackgroundColor(P.border)
        root.addView(sep)

        val accLbl = tv("Аккаунт", 13f, fMed, P.textSec)
        accLbl.layoutParams = lp(-1, -2, dp(10))
        root.addView(accLbl)

        val switchAcc = btnOutline("Сменить аккаунт")
        switchAcc.layoutParams = lp(-1, -2, dp(8))
        switchAcc.setOnClickListener { dlg.dismiss(); showAccountsDialog() }
        root.addView(switchAcc)

        val logout = btnOutline("Выйти из аккаунта")
        logout.layoutParams = lp(-1, -2, 0)
        (logout.layoutParams as LinearLayout.LayoutParams).topMargin = 0
        logout.setOnClickListener {
            dlg.dismiss()
            auth.signOut()
            showTab(0)
        }
        root.addView(logout)

        dlg.setContentView(wrap)
        dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        dlg.show()
    }

    private fun showAccountsDialog() {
        val dlg = Dialog(this)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = surf(20, filled = true)
        root.setPadding(dp(20), dp(18), dp(20), dp(20))
        val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
        val wrap = FrameLayout(this)
        val rlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
        rlp.gravity = Gravity.CENTER
        wrap.addView(root, rlp)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.layoutParams = lp(-1, -2, dp(16))
        val backIv = iconRound(R.drawable.ic_back, P.text, dp(34))
        backIv.setOnClickListener { dlg.dismiss(); showSettingsDialog() }
        val title = tv("Аккаунты", 18f, fBold, P.text)
        title.layoutParams = lp(0, -2, 0)
        wt(title, 1f)
        (title.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
        head.addView(backIv)
        head.addView(title)
        root.addView(head)

        val accounts = loadAccounts()
        if (accounts.isEmpty()) {
            root.addView(tv("Нет сохранённых аккаунтов", 14f, fMed, P.textSec))
        } else {
            for (acc in accounts) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.background = if (acc.first == currentUserId) surf(12, filled = true) else surf(12, filled = false)
                row.setPadding(dp(14), dp(12), dp(14), dp(12))
                row.layoutParams = lp(-1, -2, dp(6))
                row.addView(avatarView(null, dp(36), acc.second))
                val box = LinearLayout(this)
                box.orientation = LinearLayout.VERTICAL
                val bp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                bp.weight = 1f
                bp.marginStart = dp(12)
                box.layoutParams = bp
                box.addView(tv(acc.second, 15f, fSemi, P.text))
                box.addView(tv(acc.third, 12f, fReg, P.textSec))
                row.addView(box)
                if (acc.first == currentUserId) {
                    val cur = tv("сейчас", 11f, fMed, P.textSec)
                    row.addView(cur)
                }
                val targetEmail = acc.third
                row.setOnClickListener {
                    dlg.dismiss()
                    switchToAccount(targetEmail)
                }
                root.addView(row)
            }
        }

        val addBtn = btnAccent("Добавить аккаунт")
        addBtn.layoutParams = lp(-1, -2, 0)
        (addBtn.layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
        addBtn.setOnClickListener {
            dlg.dismiss()
            switchToAccount("")
        }
        root.addView(addBtn)

        dlg.setContentView(wrap)
        dlg.window?.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        dlg.show()
    }

    private fun switchToAccount(email: String) {
        auth.signOut()
        currentUserId = ""
        currentNick = ""
        currentEmail = ""
        currentAvatar = ""
        showTab(0)
        feedBlock.visibility = View.GONE
        authBlock.visibility = View.VISIBLE
        feedStatus.text = "вход / регистрация"
        feedEmail.setText(email)
        feedNick.setText("")
        feedPass.setText("")
        feedPass.requestFocus()
    }

    private fun themeOption(label: String, selected: Boolean): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = if (selected) surf(12, filled = true) else surf(12, filled = false)
        row.setPadding(dp(16), dp(14), dp(16), dp(14))
        row.layoutParams = lp(-1, -2, 0)
        val dot = View(this)
        dot.layoutParams = lp(dp(12), dp(12), 0)
        val dg = GradientDrawable()
        dg.shape = GradientDrawable.OVAL
        dg.setColor(if (selected) P.accent else P.border)
        dot.background = dg
        (dot.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
        val t = tv(label, 15f, if (selected) fSemi else fReg, P.text)
        row.addView(dot)
        row.addView(t)
        return row
    }

    private fun setThemeAndRestart(dark: Boolean) {
        getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
        recreate()
    }

    private fun showEditProfileDialog() {
        if (viewingUserId != currentUserId) return
        val dlg = Dialog(this)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.background = surf(20, filled = true)
        layout.setPadding(dp(20), dp(18), dp(20), dp(20))
        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        val wrap = FrameLayout(this)
        val wlp = FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT)
        wlp.gravity = Gravity.CENTER
        wrap.addView(layout, wlp)

        val headerLayout = LinearLayout(this)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.gravity = Gravity.CENTER_VERTICAL
        headerLayout.layoutParams = lp(-1, -2, dp(16))
        val backIv = iconRound(R.drawable.ic_close, P.text, dp(36))
        backIv.setOnClickListener { dlg.dismiss() }
        val title = tv("Редактировать", 17f, fBold, P.text)
        title.layoutParams = lp(0, -2, 0)
        wt(title, 1f)
        (title.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
        headerLayout.addView(backIv)
        headerLayout.addView(title)

        val avatarLayout = LinearLayout(this)
        avatarLayout.orientation = LinearLayout.VERTICAL
        avatarLayout.gravity = Gravity.CENTER_HORIZONTAL
        avatarLayout.layoutParams = lp(-1, -2, dp(16))
        editAvatarView = ImageView(this)
        val alp = LinearLayout.LayoutParams(dp(96), dp(96))
        alp.bottomMargin = dp(12)
        alp.gravity = Gravity.CENTER_HORIZONTAL
        editAvatarView!!.layoutParams = alp
        editAvatarView!!.scaleType = ImageView.ScaleType.FIT_CENTER
        if (currentAvatar.isNotEmpty()) {
            val b = decodeScaled(currentAvatar, dp(96))
            editAvatarView!!.setImageBitmap(if (b != null) makeCircularBitmap(b, dp(96)) else makeCircularBitmap(createAvatarBitmap(currentNick), dp(96)))
        } else {
            editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(currentNick), dp(96)))
        }
        val changeAvatarBtn = btnOutline("Сменить аватар")
        changeAvatarBtn.layoutParams = lp(-1, -2, 0)
        avatarLayout.addView(editAvatarView)
        avatarLayout.addView(changeAvatarBtn)

        val nickEdit = field("Ник (мин. 5 символов)")
        nickEdit.setText(currentNick)
        nickEdit.layoutParams = lp(-1, -2, dp(10))
        val bioEdit = field("Био")
        bioEdit.setText(profileBio.text.toString())
        bioEdit.layoutParams = lp(-1, -2, dp(14))
        bioEdit.gravity = Gravity.TOP
        bioEdit.minLines = 3
        bioEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val saveBtn = btnAccent("Сохранить")
        saveBtn.layoutParams = lp(-1, -2, 0)

        changeAvatarBtn.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_AVATAR)
        }
        saveBtn.setOnClickListener {
            val newNick = nickEdit.text.toString().trim()
            val newBio = bioEdit.text.toString().trim()
            if (newNick.length < 5) {
                Toast.makeText(this, "Ник минимум 5 символов", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isNickTaken(newNick, currentUserId) { taken ->
                if (taken) {
                    Toast.makeText(this@MainActivity, "Ник уже занят", Toast.LENGTH_SHORT).show()
                    return@isNickTaken
                }
                if (newNick != currentNick) {
                    database.child("users").child(currentUserId).child("nick").setValue(newNick)
                    currentNick = newNick
                    saveAccount(currentUserId, newNick, currentEmail)
                    updatePostsNick(newNick)
                }
                database.child("users").child(currentUserId).child("bio").setValue(newBio)
                dlg.dismiss()
                refreshProfile()
                loadFeed()
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

    private fun refreshProfile() {
        if (!::profileRoot.isInitialized) return
        val targetId = if (viewingUserId.isNotEmpty()) viewingUserId else currentUserId
        profileTabPosts.setColorFilter(if (profileMode == 0) P.text else P.navInactive)
        profileTabLikes.setColorFilter(if (profileMode == 1) P.accent else P.navInactive)
        database.child("users").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nick = snapshot.child("nick").getValue(String::class.java) ?: "?"
                val avatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                val bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                val b = if (avatar.isNotEmpty()) decodeScaled(avatar, dp(84)) else null
                profileAvatar.setImageBitmap(if (b != null) makeCircularBitmap(b, dp(84)) else makeCircularBitmap(createAvatarBitmap(nick), dp(84)))
                profileNick.text = "@$nick"
                profileName.text = nick
                profileBio.text = bio
                profileFollowingCount.text = fmtNum(knownNames.size.toLong())
                if (targetId == currentUserId) {
                    profileBtn1.text = "Редактировать профиль"
                    profileBtn1.setOnClickListener { showEditProfileDialog() }
                    profileEditIv.visibility = View.VISIBLE
                    profileEditIv.setOnClickListener { showEditProfileDialog() }
                    profileSettingsIv.visibility = View.VISIBLE
                } else {
                    profileBtn1.text = "Написать"
                    profileBtn1.setOnClickListener { openChat(targetId, nick) }
                    profileEditIv.visibility = View.GONE
                    profileSettingsIv.visibility = View.GONE
                }
                profileBtn2.setOnClickListener {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("nick", "@$nick"))
                    Toast.makeText(this@MainActivity, "Ник скопирован", Toast.LENGTH_SHORT).show()
                }
                database.child("posts").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(ps: DataSnapshot) {
                        var ownCount = 0L
                        var sumLikes = 0L
                        val grid = ArrayList<DataSnapshot>()
                        for (p in ps.children) {
                            val isMine = p.child("authorId").getValue(String::class.java) == targetId
                            val liked = p.child("likedBy").child(targetId).value != null
                            if (isMine) {
                                ownCount++
                                sumLikes += (p.child("likes").value as? Number)?.toLong() ?: 0L
                            }
                            if (profileMode == 0 && isMine) grid.add(p)
                            if (profileMode == 1 && liked) grid.add(p)
                        }
                        profilePostsCount.text = fmtNum(ownCount)
                        profileFollowersCount.text = "0"
                        profileLikesCount.text = fmtNum(sumLikes)
                        profilePostsGrid.removeAllViews()
                        if (grid.isEmpty()) {
                            val empty = tv(if (profileMode == 0) "Нет постов" else "Нет лайков", 14f, fMed, P.textSec)
                            empty.gravity = Gravity.CENTER
                            empty.setPadding(0, dp(40), 0, dp(40))
                            profilePostsGrid.addView(empty)
                            return
                        }
                        val sortedGrid = grid.sortedByDescending { getTs(it) }
                        val cell = (resources.displayMetrics.widthPixels - dp(36) - dp(4)) / 3
                        var i = 0
                        while (i < sortedGrid.size) {
                            val row = LinearLayout(this@MainActivity)
                            row.orientation = LinearLayout.HORIZONTAL
                            row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                            for (c in 0 until 3) {
                                val idx = i + c
                                val box = FrameLayout(this@MainActivity)
                                val p2 = LinearLayout.LayoutParams(cell, cell)
                                p2.setMargins(0, 0, dp(2), dp(2))
                                box.layoutParams = p2
                                if (idx < sortedGrid.size) {
                                    try {
                                        val p = sortedGrid[idx]
                                        val postId = p.key ?: ""
                                        val imgStr = p.child("images").getValue(String::class.java) ?: ""
                                        val txt = p.child("text").getValue(String::class.java) ?: ""
                                        val lc = (p.child("likes").value as? Number)?.toLong() ?: 0L
                                        if (imgStr.isNotEmpty()) {
                                            val first = imgStr.split(",").firstOrNull { it.isNotEmpty() }
                                            if (first != null) {
                                                val bb = decodeScaled(first, cell)
                                                if (bb != null) {
                                                    val iv = ImageView(this@MainActivity)
                                                    iv.setImageBitmap(bb)
                                                    iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                                    iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                                    box.addView(iv)
                                                }
                                            }
                                        } else {
                                            box.background = surf(6, filled = true)
                                            val tt = tv(txt, 11f, fMed, P.text)
                                            tt.maxLines = 6
                                            tt.setPadding(dp(6), dp(6), dp(6), dp(18))
                                            box.addView(tt)
                                        }
                                        // Тап по ячейке -> детальное окно поста
                                        box.setOnClickListener { openPostDetail(p) }
                                        // Сердечко поверх -> мгновенный лайк (не открывает деталь)
                                        val likedBy = p.child("likedBy").children.mapNotNull { it.key }.toSet()
                                        val heartQuick = icon(R.drawable.ic_heart_outline, P.textSec, dp(18))
                                        val hp = FrameLayout.LayoutParams(dp(18), dp(18))
                                        hp.gravity = Gravity.TOP or Gravity.END
                                        hp.setMargins(0, dp(6), dp(6), 0)
                                        box.addView(heartQuick, hp)
                                        val cntHidden = tv("♥ " + fmtNum(lc), 12f, fSemi, P.text)
                                        attachLike(heartQuick, cntHidden, heartQuick, postId, currentUserId in likedBy)
                                    } catch (e: Exception) {
                                        // пропускаем ячейку
                                    }
                                }
                                row.addView(box)
                            }
                            profilePostsGrid.addView(row)
                            i += 3
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ================= ВЫБОР ФОТО =================
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
                    val bounds = BitmapFactory.Options()
                    bounds.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(ins, null, bounds)
                    ins?.close()
                    var sample = 1
                    while (bounds.outWidth / sample / 2 > 720) sample *= 2
                    val ins2 = contentResolver.openInputStream(u)
                    val opts = BitmapFactory.Options()
                    opts.inSampleSize = sample
                    val bmp = BitmapFactory.decodeStream(ins2, null, opts)
                    ins2?.close()
                    if (bmp != null) {
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        pendingImages.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                    }
                } catch (e: Exception) {
                    // пропускаем битое фото
                }
            }
            if (pendingImages.isNotEmpty()) {
                pendingWrap.visibility = View.VISIBLE
                pendingInfo.text = "выбрано фото: ${pendingImages.size}"
                pendingRow.removeAllViews()
                for (im in pendingImages) {
                    val b = decodeScaled(im, dp(84))
                    if (b != null) {
                        val iv = ImageView(this)
                        iv.setImageBitmap(b)
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        iv.layoutParams = lp(dp(84), dp(84), dp(6))
                        iv.background = surf(12, filled = true)
                        iv.clipToOutline = true
                        pendingRow.addView(iv)
                    }
                }
            }
        } else if (requestCode == PICK_AVATAR) {
            val uri = data.data ?: return
            try {
                val ins = contentResolver.openInputStream(uri)
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                BitmapFactory.decodeStream(ins, null, bounds)
                ins?.close()
                var sample = 1
                while (bounds.outWidth / sample / 2 > 400) sample *= 2
                val ins2 = contentResolver.openInputStream(uri)
                val opts = BitmapFactory.Options()
                opts.inSampleSize = sample
                val bmp = BitmapFactory.decodeStream(ins2, null, opts)
                ins2?.close()
                if (bmp != null) {
                    val side = minOf(bmp.width, bmp.height)
                    val cropped = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                    val scaled = Bitmap.createScaledBitmap(cropped, 240, 240, true)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                    val avatarB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    database.child("users").child(currentUserId).child("avatar").setValue(avatarB64)
                    currentAvatar = avatarB64
                    avatarCache[currentUserId] = makeCircularBitmap(scaled, dp(40))
                    profileAvatar.setImageBitmap(makeCircularBitmap(scaled, dp(84)))
                    editAvatarView?.setImageBitmap(makeCircularBitmap(scaled, dp(96)))
                }
            } catch (e: Exception) {
                // не падаем при ошибке чтения аватара
            }
        }
    }
}
