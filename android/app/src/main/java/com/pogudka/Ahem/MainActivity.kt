package com.pogudka.Ahem

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.util.Base64
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.core.content.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    // ================= ЛИКВИДНОЕ СТЕКЛО: ПАЛИТРА =================
    private object C {
        val bg1 = 0xFF05050C.toInt()
        val bg2 = 0xFF101024.toInt()
        val bg3 = 0xFF1A0F2E.toInt()
        val accent = 0xFFFF3D71.toInt()
        val accent2 = 0xFF7C4DFF.toInt()
        val cyan = 0xFF08D9D6.toInt()
        val text = 0xFFFFFFFF.toInt()
        val textSec = 0xFF8E8EA3.toInt()
        val glassFill = Color.argb(0x14, 255, 255, 255)
        val glassStroke = Color.argb(0x30, 255, 255, 255)
        val glassFillStrong = Color.argb(0x22, 255, 255, 255)
    }

    // ================= ШРИФТЫ: UNBOUNDED + UBUNTU =================
    private val fBold: Typeface by lazy { ResourcesCompat.getFont(this, R.font.unbounded)!! }
    private val fSemi: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    private val fMed: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    private val fReg: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_regular)!! }

    // ================= ХЕЛПЕРЫ РАЗМЕТКИ =================
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

    private fun addKnown(n: String) {
        if (n.isNotEmpty() && n != me()) knownNames.add(n)
    }

    private fun parseCount(t: TextView): Int =
        t.text.toString().substringAfter("♥").trim().toIntOrNull() ?: 0

    // ================= ГРАФИЧЕСКИЕ ХЕЛПЕРЫ ДИЗАЙНА =================
    private fun glass(radius: Float, strong: Boolean = false): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(if (strong) C.glassFillStrong else C.glassFill)
        g.cornerRadius = radius
        g.setStroke(dp(1), C.glassStroke)
        return g
    }

    private fun grad(
        colors: IntArray,
        radius: Float,
        orient: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable {
        val g = GradientDrawable(orient, colors)
        g.cornerRadius = radius
        return g
    }

    private fun noise(): BitmapDrawable {
        val n = 90
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        val px = IntArray(n * n)
        for (i in px.indices) {
            val v = rnd.nextInt(255)
            px[i] = Color.argb(10, v, v, v)
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        val d = BitmapDrawable(resources, bmp)
        d.tileModeX = Shader.TileMode.REPEAT
        d.tileModeY = Shader.TileMode.REPEAT
        return d
    }

    private fun tv(text: String, size: Float, type: Typeface, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.typeface = type
        t.setTextColor(color)
        return t
    }

    private fun glassField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(C.textSec)
        e.setTextColor(C.text)
        e.typeface = fReg
        e.textSize = 14f
        e.background = glass(dp(16).toFloat())
        e.setPadding(dp(16), dp(12), dp(16), dp(12))
        return e
    }

    private fun glassBtn(label: String): TextView {
        val t = tv(label, 13f, fSemi, C.text)
        t.gravity = Gravity.CENTER
        t.background = glass(dp(12).toFloat(), true)
        t.setPadding(dp(12), dp(9), dp(12), dp(9))
        return t
    }

    private fun gradBtn(label: String): TextView {
        val t = tv(label, 13f, fSemi, C.text)
        t.gravity = Gravity.CENTER
        t.background = grad(intArrayOf(C.accent, C.accent2), dp(14).toFloat())
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        return t
    }

    private fun icon(res: Int, tint: Int, size: Int): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.setColorFilter(tint)
        iv.layoutParams = lp(size, size, 0)
        return iv
    }

    private fun iconGlass(res: Int, tint: Int, size: Int): ImageView {
        val iv = icon(res, tint, size)
        iv.setPadding(dp(8), dp(8), dp(8), dp(8))
        iv.background = glass(dp(12).toFloat())
        return iv
    }

    private fun shaderText(t: TextView) {
        t.post {
            val w = t.paint.measureText(t.text.toString()).coerceAtLeast(1f)
            t.paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(C.accent, C.accent2, C.cyan),
                null, Shader.TileMode.CLAMP
            )
            t.invalidate()
        }
    }

    private fun blurDialog(dlg: Dialog) {
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                dlg.window?.setBackgroundBlurRadius(60)
                dlg.window?.setDimAmount(0.35f)
            } catch (e: Exception) {}
        } else {
            dlg.window?.setDimAmount(0.55f)
        }
    }

    // ================= АВАТАРКИ =================
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

    private fun createAvatarBitmap(nick: String): Bitmap {
        val size = dp(100)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            intArrayOf(C.accent, C.accent2), null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val textPaint = Paint()
        textPaint.color = Color.WHITE
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

    // ================= СОСТОЯНИЕ: ЛЕНТА =================
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var tabs: Array<View>
    private lateinit var navIcons: Array<ImageView>

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
    private lateinit var regBtn: TextView
    private lateinit var logBtn: TextView
    private var currentUserId = ""
    private var currentNick = ""
    private var currentAvatar = ""
    private val pendingImages = ArrayList<String>()
    private var isRegistering = false
    private var isLoggingIn = false
    private var lastFeedSig = ""

    // ================= СОСТОЯНИЕ: ЧАТЫ =================
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

    // ================= СОСТОЯНИЕ: ПРОФИЛЬ =================
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
    private lateinit var profileMenuIv: ImageView
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

    private var feedListener: ValueEventListener? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private val avatarCache = HashMap<String, Bitmap>()

    // ================= ЛАЙКИ: МГНОВЕННО + ТРАНЗАКЦИЯ =================
    private fun heartSet(iv: ImageView, liked: Boolean) {
        iv.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        iv.setColorFilter(if (liked) C.accent else C.text)
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
            countTv.text = "♥ " + if (liked[0]) prevCount + 1 else maxOf(0, prevCount - 1)
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
                        countTv.text = "♥ $newCount"
                        heartSet(heartIv, liked[0])
                    } else {
                        liked[0] = prevLiked
                        countTv.text = "♥ $prevCount"
                        heartSet(heartIv, prevLiked)
                    }
                }
            })
        }
    }

    // ================= НИК: ОБНОВЛЕНИЕ ПОСТАХ + УНИКАЛЬНОСТЬ =================
    private fun updatePostsNick(newNick: String) {
        database.child("posts").orderByChild("authorId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (p in snapshot.children) {
                        p.ref.child("authorNick").setValue(newNick)
                    }
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

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.bg1, C.bg2, C.bg3)),
            noise()
        ))

        val content = FrameLayout(this)
        content.layoutParams = lp(-1, 0, 0)
        wt(content, 1f)

        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChats()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))

        // Плавающая стеклянная пилюля-навигация
        val navWrap = FrameLayout(this)
        navWrap.layoutParams = lp(-1, -2, 0)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = glass(dp(28).toFloat(), true)
        nav.setPadding(dp(10), dp(8), dp(10), dp(8))
        val nlp = FrameLayout.LayoutParams(-1, -2)
        nlp.gravity = Gravity.CENTER_HORIZONTAL
        nlp.setMargins(dp(14), 0, dp(14), dp(10))
        nav.layoutParams = nlp

        val icons = intArrayOf(R.drawable.ic_feed, R.drawable.ic_video, R.drawable.ic_chat, R.drawable.ic_profile)
        navIcons = Array(4) { i ->
            val iv = icon(icons[i], Color.argb(0xB0, 255, 255, 255), dp(24))
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
        for (idx in 0 until 4) {
            navIcons[idx].setColorFilter(if (idx == i) C.accent else Color.argb(0xB0, 255, 255, 255))
        }
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

        // --- БЛОК АВТОРИЗАЦИИ ---
        authBlock = LinearLayout(this)
        authBlock.orientation = LinearLayout.VERTICAL
        authBlock.gravity = Gravity.CENTER_HORIZONTAL
        authBlock.setPadding(dp(28), dp(40), dp(28), 0)

        val logo = tv("Ahem", 30f, fBold, C.text)
        shaderText(logo)
        val tag = tv("liquid glass social", 12f, fMed, C.textSec)
        tag.layoutParams = lp(-2, -2, dp(28))

        feedEmail = glassField("Email")
        feedEmail.layoutParams = lp(-1, -2, dp(10))

        feedNick = glassField("Ник (мин. 5 символов, уникальный)")
        feedNick.layoutParams = lp(-1, -2, dp(10))

        feedPass = glassField("Пароль")
        feedPass.inputType = 1
        feedPass.layoutParams = lp(-1, -2, dp(14))

        val rowAuth = LinearLayout(this)
        rowAuth.orientation = LinearLayout.HORIZONTAL
        rowAuth.layoutParams = lp(-1, -2, dp(10))

        regBtn = gradBtn("Регистрация")
        regBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(regBtn, 1f)

        logBtn = glassBtn("Вход")
        logBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(logBtn, 1f)

        rowAuth.addView(regBtn)
        rowAuth.addView(logBtn)

        feedStatus = tv("не залогинен", 12f, fReg, C.textSec)

        authBlock.addView(logo)
        authBlock.addView(tag)
        authBlock.addView(feedEmail)
        authBlock.addView(feedNick)
        authBlock.addView(feedPass)
        authBlock.addView(rowAuth)
        authBlock.addView(feedStatus)

        // --- БЛОК ЛЕНТЫ ---
        feedBlock = LinearLayout(this)
        feedBlock.orientation = LinearLayout.VERTICAL
        feedBlock.visibility = View.GONE

        // Топбар: лого + ник + сменить
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(16), dp(12), dp(16), dp(8))

        val logoS = tv("Ahem", 18f, fBold, C.text)
        shaderText(logoS)
        logoS.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(logoS, 1f)

        feedHeader = tv("", 13f, fSemi, C.textSec)
        feedHeader.layoutParams = lp(-2, LinearLayout.LayoutParams.WRAP_CONTENT, 0)

        val switchIv = iconGlass(R.drawable.ic_edit, C.text, dp(36))

        top.addView(logoS)
        top.addView(feedHeader)
        top.addView(switchIv)

        // Композер поста
        val composer = LinearLayout(this)
        composer.orientation = LinearLayout.VERTICAL
        composer.background = glass(dp(20).toFloat())
        composer.setPadding(dp(12), dp(12), dp(12), dp(12))
        val compM = LinearLayout.LayoutParams(-1, -2)
        compM.setMargins(dp(10), 0, dp(10), dp(10))
        composer.layoutParams = compM

        feedPostText = glassField("Подпись к посту (до 4 фото, ссылки кликабельны)")
        feedPostText.background = glass(dp(14).toFloat(), true)
        feedPostText.layoutParams = lp(-1, -2, dp(10))

        pendingWrap = LinearLayout(this)
        pendingWrap.orientation = LinearLayout.VERTICAL
        pendingWrap.visibility = View.GONE
        pendingWrap.layoutParams = lp(-1, -2, dp(8))

        val pendHead = LinearLayout(this)
        pendHead.orientation = LinearLayout.HORIZONTAL
        pendHead.gravity = Gravity.CENTER_VERTICAL

        pendingInfo = tv("", 12f, fMed, C.textSec)
        pendingInfo.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pendingInfo, 1f)

        val clearIv = iconGlass(R.drawable.ic_close, C.accent, dp(30))

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

        val camIv = iconGlass(R.drawable.ic_camera, C.accent, dp(42))

        val pubBtn = gradBtn("Опубликовать")
        pubBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(pubBtn, 1f)
        (pubBtn.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)

        cRow.addView(camIv)
        cRow.addView(pubBtn)

        composer.addView(feedPostText)
        composer.addView(pendingWrap)
        composer.addView(cRow)

        // Ряд действий: лента + подписка
        val rowAct = LinearLayout(this)
        rowAct.orientation = LinearLayout.HORIZONTAL
        rowAct.gravity = Gravity.CENTER_VERTICAL
        val raM = LinearLayout.LayoutParams(-1, -2)
        raM.setMargins(dp(10), 0, dp(10), dp(10))
        rowAct.layoutParams = raM

        val feedBtn = glassBtn("⟳ Лента")
        feedBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedBtn, 1f)

        feedFollowNick = glassField("@ник подписки")
        feedFollowNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(feedFollowNick, 2f)
        (feedFollowNick.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)

        val folBtn = glassBtn("Подписаться")
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

        // --- ОБРАБОТЧИКИ ---
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
                        viewingUserId = currentUserId
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
            val email = feedEmail.text.toString()
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
                logBtn.text = "Вход"
            }
        }

        switchIv.setOnClickListener {
            auth.signOut()
            feedBlock.visibility = View.GONE
            authBlock.visibility = View.VISIBLE
            feedStatus.text = "не залогинен"
            currentUserId = ""
            currentNick = ""
            currentAvatar = ""
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
            post["likedBy"] = HashMap<String, Boolean>()
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
                      .append(p.child("authorNick").getValue(String::class.java) ?: "").append('~')
                      .append(p.child("text").getValue(String::class.java) ?: "").append('~')
                      .append(p.child("images").getValue(String::class.java) ?: "").append(';')
                }
                val sig = sb.toString()
                if (sig == lastFeedSig) return
                lastFeedSig = sig

                feedBox.removeAllViews()
                val imgW = resources.displayMetrics.widthPixels - dp(20) - dp(24)

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
                    card.background = glass(dp(24).toFloat())
                    val cm2 = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                    cm2.setMargins(dp(10), 0, dp(10), dp(12))
                    card.layoutParams = cm2

                    // ШАПКА ПОСТА
                    val header = LinearLayout(this@MainActivity)
                    header.orientation = LinearLayout.HORIZONTAL
                    header.setPadding(dp(12), dp(12), dp(12), dp(10))
                    header.gravity = Gravity.CENTER_VERTICAL

                    val avatarSize = dp(40)
                    val avatarImg = ImageView(this@MainActivity)
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
                                    val authorAvatar = avatarSnapshot.getValue(String::class.java) ?: ""
                                    if (authorAvatar.isNotEmpty()) {
                                        try {
                                            val bytes = Base64.decode(authorAvatar, Base64.NO_WRAP)
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            val circular = makeCircularBitmap(bmp, avatarSize)
                                            avatarCache[authorId] = circular
                                            avatarImg.setImageBitmap(circular)
                                        } catch (e: Exception) {}
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }

                    avatarImg.setOnClickListener { openUserProfile(authorId, authorNick) }

                    val nickTimeLayout = LinearLayout(this@MainActivity)
                    nickTimeLayout.orientation = LinearLayout.VERTICAL
                    val nickParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    nickParams.weight = 1f
                    nickTimeLayout.layoutParams = nickParams

                    val nickView = tv(authorNick, 15f, fSemi, C.text)
                    nickView.setOnClickListener { openUserProfile(authorId, authorNick) }

                    val timeView = tv(timeStr, 12f, fReg, C.textSec)

                    nickTimeLayout.addView(nickView)
                    nickTimeLayout.addView(timeView)

                    header.addView(avatarImg)
                    header.addView(nickTimeLayout)
                    card.addView(header)

                    // ТЕКСТ ПОСТА С КЛИКАБЕЛЬНЫМИ ССЫЛКАМИ
                    if (text.isNotEmpty()) {
                        val bt = TextView(this@MainActivity)
                        bt.typeface = fReg
                        bt.textSize = 14f
                        bt.setTextColor(C.text)
                        bt.setPadding(dp(12), 0, dp(12), dp(12))
                        bt.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                        Linkify.addLinks(bt, Linkify.WEB_URLS)
                        bt.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                        bt.setLinkTextColor(C.cyan)
                        bt.text = text
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
                                box.layoutParams = lp(-1, imgW, 0)
                                (box.layoutParams as LinearLayout.LayoutParams).setMargins(dp(12), 0, dp(12), dp(8))
                                box.background = glass(dp(16).toFloat(), true)
                                box.clipToOutline = true
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
                            val rm = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                            rm.setMargins(dp(12), 0, dp(12), dp(8))
                            row.layoutParams = rm
                            val half = (imgW - dp(4)) / 2
                            for (i in images.indices) {
                                try {
                                    val bytes = Base64.decode(images[i], Base64.NO_WRAP)
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    val box = FrameLayout(this@MainActivity)
                                    box.layoutParams = lp(0, half, 0)
                                    wt(box, 1f)
                                    (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (i == 0) dp(4) else 0
                                    box.background = glass(dp(14).toFloat(), true)
                                    box.clipToOutline = true
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
                            val gm = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                            gm.setMargins(dp(12), 0, dp(12), dp(8))
                            grid.layoutParams = gm
                            val cell = (imgW - dp(4)) / 2
                            var rowIdx = 0
                            while (rowIdx < images.size) {
                                val row = LinearLayout(this@MainActivity)
                                row.orientation = LinearLayout.HORIZONTAL
                                row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, dp(4))
                                for (colIdx in 0 until 2) {
                                    val imgIdx = rowIdx + colIdx
                                    if (imgIdx < images.size) {
                                        try {
                                            val bytes = Base64.decode(images[imgIdx], Base64.NO_WRAP)
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            val box = FrameLayout(this@MainActivity)
                                            box.layoutParams = lp(0, cell, 0)
                                            wt(box, 1f)
                                            (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (colIdx == 0) dp(4) else 0
                                            box.background = glass(dp(14).toFloat(), true)
                                            box.clipToOutline = true
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
                                            box.layoutParams = lp(0, cell, 0)
                                            wt(box, 1f)
                                            (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (colIdx == 0) dp(4) else 0
                                            box.background = glass(dp(14).toFloat(), true)
                                            row.addView(box)
                                        }
                                    } else {
                                        val box = FrameLayout(this@MainActivity)
                                        box.layoutParams = lp(0, cell, 0)
                                        wt(box, 1f)
                                        (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (colIdx == 0) dp(4) else 0
                                        box.background = glass(dp(14).toFloat(), true)
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
                    row.setPadding(dp(12), dp(6), dp(12), dp(10))
                    row.gravity = Gravity.CENTER_VERTICAL

                    val heartIv = icon(R.drawable.ic_heart_outline, C.text, dp(24))
                    val countTv = tv("♥ $likeCount", 13f, fSemi, C.text)
                    (countTv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)

                    val likeBox = LinearLayout(this@MainActivity)
                    likeBox.orientation = LinearLayout.HORIZONTAL
                    likeBox.gravity = Gravity.CENTER_VERTICAL
                    likeBox.addView(heartIv)
                    likeBox.addView(countTv)

                    val likedBy = post.child("likedBy").children.map { it.key }.toSet()
                    attachLike(likeBox, countTv, heartIv, postId, currentUserId in likedBy)
                    row.addView(likeBox)

                    if (authorId != currentUserId) {
                        val fb = glassBtn("+ @$authorNick")
                        fb.textSize = 11f
                        (fb.layoutParams as LinearLayout.LayoutParams).marginStart = dp(12)
                        fb.setOnClickListener { addKnown(authorNick) }
                        row.addView(fb)
                    }
                    card.addView(row)
                    feedBox.addView(card)
                }
                if (posts.isEmpty()) {
                    feedBox.addView(tv("Лента пуста", 14f, fMed, C.textSec))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
        back.setTextColor(C.text)
        back.textSize = 28f
        back.typeface = fBold
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
        prevB.setTextColor(C.text)
        prevB.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(prevB, 1f)

        val cnt = TextView(this)
        cnt.setTextColor(C.text)
        cnt.gravity = Gravity.CENTER
        cnt.typeface = fSemi
        cnt.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(cnt, 2f)

        val nextB = Button(this)
        nextB.text = "▶"
        nextB.setTextColor(C.text)
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
        col.setPadding(dp(16), dp(12), dp(16), 0)

        chatListRoot = LinearLayout(this)
        chatListRoot.orientation = LinearLayout.VERTICAL

        val head = tv("Сообщения", 20f, fBold, C.text)
        head.layoutParams = lp(-1, -2, dp(10))

        chatStatus = tv("Firebase подключен", 12f, fReg, C.textSec)
        chatStatus.layoutParams = lp(-1, -2, dp(12))

        val rowSearch = LinearLayout(this)
        rowSearch.orientation = LinearLayout.HORIZONTAL
        rowSearch.layoutParams = lp(-1, -2, dp(12))

        searchField = glassField("Поиск по нику")
        searchField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(searchField, 2f)

        val searchIv = iconGlass(R.drawable.ic_search, C.accent, dp(44))
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

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.background = glass(dp(18).toFloat())
        top.setPadding(dp(8), dp(8), dp(12), dp(8))
        top.layoutParams = lp(-1, -2, dp(10))

        val backIv = iconGlass(R.drawable.ic_back, C.text, dp(36))

        convTitle = tv("", 16f, fSemi, C.text)
        convTitle.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(convTitle, 1f)
        (convTitle.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)

        top.addView(backIv)
        top.addView(convTitle)

        convBox = LinearLayout(this)
        convBox.orientation = LinearLayout.VERTICAL

        convScroll = ScrollView(this)
        convScroll.addView(convBox)
        convScroll.layoutParams = lp(-1, 0, 0)
        wt(convScroll, 1f)
        convScroll.isVerticalScrollBarEnabled = false

        msgField = glassField("Сообщение...")

        val sendIv = ImageView(this)
        sendIv.setImageResource(R.drawable.ic_send)
        sendIv.setColorFilter(C.text)
        sendIv.setPadding(dp(11), dp(11), dp(11), dp(11))
        sendIv.layoutParams = lp(dp(44), dp(44), 8)
        sendIv.background = grad(intArrayOf(C.accent, C.accent2), dp(14).toFloat())

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.layoutParams = lp(-1, -2, dp(8))
        msgField.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(msgField, 1f)
        (sendIv.layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
        inputRow.addView(msgField)
        inputRow.addView(sendIv)

        convRoot.addView(top)
        convRoot.addView(convScroll)
        convRoot.addView(inputRow)

        searchIv.setOnClickListener {
            val query = searchField.text.toString().trim().lowercase()
            if (query.isEmpty()) return@setOnClickListener
            searchResults.removeAllViews()
            database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (user in snapshot.children) {
                        val nick = user.child("nick").getValue(String::class.java) ?: ""
                        if (nick.lowercase().contains(query) && user.key != currentUserId) {
                            val btn = LinearLayout(this@MainActivity)
                            btn.orientation = LinearLayout.HORIZONTAL
                            btn.gravity = Gravity.CENTER_VERTICAL
                            btn.background = glass(dp(16).toFloat())
                            btn.layoutParams = lp(-1, -2, dp(6))
                            btn.setPadding(dp(10), dp(8), dp(12), dp(8))
                            btn.addView(avatarView(null, dp(32), nick))
                            val nt = tv("✉ $nick", 14f, fMed, C.text)
                            (nt.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
                            btn.addView(nt)
                            btn.setOnClickListener { openChat(user.key ?: "", nick); searchResults.removeAllViews() }
                            searchResults.addView(btn)
                        }
                    }
                    if (searchResults.childCount == 0) {
                        searchResults.addView(tv("Никого не нашли", 13f, fMed, C.textSec))
                    }
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
                        bubble.background = if (isMe) grad(intArrayOf(C.accent, C.accent2), dp(18).toFloat()) else glass(dp(18).toFloat(), true)
                        bubble.setPadding(dp(14), dp(9), dp(14), dp(7))

                        val mt = tv(text, 14f, fReg, C.text)
                        val tt = tv(timeStr, 9f, fReg, if (isMe) 0xCCFFFFFF.toInt() else C.textSec)
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
        col.setPadding(dp(24), dp(24), dp(24), 24)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.background = glass(dp(28).toFloat())
        card.setPadding(dp(24), dp(36), dp(24), dp(36))

        card.addView(icon(R.drawable.ic_video, C.accent2, dp(56)))
        val t = tv("Видео скоро", 18f, fBold, C.text)
        t.layoutParams = lp(-2, -2, dp(6))
        t.gravity = Gravity.CENTER
        val s = tv("Здесь появится лента клипов", 13f, fReg, C.textSec)
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

        profileScroll = ScrollView(this)
        profileScroll.layoutParams = lp(-1, -1)
        profileScroll.isVerticalScrollBarEnabled = false

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(12), dp(16), 0)

        // Ник + меню
        val nickRow = LinearLayout(this)
        nickRow.orientation = LinearLayout.HORIZONTAL
        nickRow.gravity = Gravity.CENTER_VERTICAL
        nickRow.layoutParams = lp(-1, -2, dp(14))

        profileNick = tv("@", 18f, fBold, C.text)
        profileNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(profileNick, 1f)

        profileMenuIv = iconGlass(R.drawable.ic_edit, C.text, dp(34))

        nickRow.addView(profileNick)
        nickRow.addView(profileMenuIv)
        content.addView(nickRow)

        // Аватар + статистика (4 колонки: публикации / подписчики / подписки / лайки)
        val infoRow = LinearLayout(this)
        infoRow.orientation = LinearLayout.HORIZONTAL
        infoRow.gravity = Gravity.CENTER_VERTICAL
        infoRow.layoutParams = lp(-1, -2, dp(14))

        profileAvatar = ImageView(this)
        profileAvatar.layoutParams = lp(dp(86), dp(86), 0)
        (profileAvatar.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(18)
        profileAvatar.scaleType = ImageView.ScaleType.FIT_CENTER

        profilePostsCount = TextView(this)
        profileFollowersCount = TextView(this)
        profileFollowingCount = TextView(this)
        profileLikesCount = TextView(this)

        fun statCol(num: TextView, label: String): LinearLayout {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER
            c.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
            wt(c, 1f)
            num.gravity = Gravity.CENTER
            num.textSize = 17f
            num.typeface = fBold
            num.setTextColor(C.text)
            val l = tv(label, 10f, fReg, C.textSec)
            l.gravity = Gravity.CENTER
            c.addView(num)
            c.addView(l)
            return c
        }

        infoRow.addView(profileAvatar)
        infoRow.addView(statCol(profilePostsCount, "публикации"))
        infoRow.addView(statCol(profileFollowersCount, "подписчики"))
        infoRow.addView(statCol(profileFollowingCount, "подписки"))
        infoRow.addView(statCol(profileLikesCount, "лайки"))
        content.addView(infoRow)

        // Имя + био
        profileName = tv("", 14f, fSemi, C.text)
        profileName.layoutParams = lp(-1, -2, dp(2))
        profileBio = tv("", 13f, fReg, C.text)
        profileBio.layoutParams = lp(-1, -2, dp(12))
        content.addView(profileName)
        content.addView(profileBio)

        // Кнопки
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.layoutParams = lp(-1, -2, dp(14))

        profileBtn1 = glassBtn("Редактировать профиль")
        profileBtn1.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, dp(6))
        wt(profileBtn1, 1f)

        profileBtn2 = glassBtn("Поделиться")
        profileBtn2.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        wt(profileBtn2, 1f)

        btnRow.addView(profileBtn1)
        btnRow.addView(profileBtn2)
        content.addView(btnRow)

        // Вкладки сетки
        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.background = glass(dp(16).toFloat())
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4))
        tabRow.layoutParams = lp(-1, -2, dp(2))

        profileTabPosts = icon(R.drawable.ic_grid, C.text, dp(22))
        profileTabPosts.setPadding(dp(6), dp(6), dp(6), dp(6))
        profileTabPosts.layoutParams = lp(0, dp(34), 0)
        wt(profileTabPosts, 1f)

        profileTabLikes = icon(R.drawable.ic_heart, Color.argb(0xB0, 255, 255, 255), dp(22))
        profileTabLikes.setPadding(dp(6), dp(6), dp(6), dp(6))
        profileTabLikes.layoutParams = lp(0, dp(34), 0)
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

    private fun showEditProfileDialog() {
        if (viewingUserId != currentUserId) return

        val dlg = Dialog(this)
        blurDialog(dlg)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.background = glass(dp(28).toFloat(), true)
        layout.setPadding(dp(20), dp(18), dp(20), dp(20))

        val headerLayout = LinearLayout(this)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.gravity = Gravity.CENTER_VERTICAL
        headerLayout.layoutParams = lp(-1, -2, dp(16))

        val backIv = iconGlass(R.drawable.ic_close, C.text, dp(36))
        backIv.setOnClickListener { dlg.dismiss() }

        val title = tv("Редактировать профиль", 16f, fBold, C.text)
        title.layoutParams = lp(0, -2, 0)
        wt(title, 1f)
        (title.layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)

        headerLayout.addView(backIv)
        headerLayout.addView(title)

        val avatarLayout = LinearLayout(this)
        avatarLayout.orientation = LinearLayout.VERTICAL
        avatarLayout.gravity = Gravity.CENTER_HORIZONTAL
        avatarLayout.layoutParams = lp(-1, -2, dp(16))

        editAvatarView = ImageView(this)
        editAvatarView!!.layoutParams = lp(dp(96), dp(96), dp(12))
        (editAvatarView!!.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
        editAvatarView!!.scaleType = ImageView.ScaleType.FIT_CENTER

        if (currentAvatar.isNotEmpty()) {
            try {
                val bytes = Base64.decode(currentAvatar, Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                editAvatarView!!.setImageBitmap(makeCircularBitmap(bmp, dp(96)))
            } catch (e: Exception) {
                editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(currentNick), dp(96)))
            }
        } else {
            editAvatarView!!.setImageBitmap(makeCircularBitmap(createAvatarBitmap(currentNick), dp(96)))
        }

        val changeAvatarBtn = glassBtn("📷 Сменить аватар")
        changeAvatarBtn.layoutParams = lp(-1, -2, 0)

        avatarLayout.addView(editAvatarView)
        avatarLayout.addView(changeAvatarBtn)

        val nickEdit = glassField("Ник (мин. 5 символов, уникальный)")
        nickEdit.setText(currentNick)
        nickEdit.layoutParams = lp(-1, -2, dp(10))

        val bioEdit = glassField("Био")
        bioEdit.setText(profileBio.text.toString())
        bioEdit.layoutParams = lp(-1, -2, dp(14))
        bioEdit.gravity = Gravity.TOP
        bioEdit.minLines = 3
        bioEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        val saveBtn = gradBtn("Сохранить")
        saveBtn.layoutParams = lp(-1, -2, 0)

        changeAvatarBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_AVATAR)
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
                    feedHeader.text = "@$newNick"
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

        dlg.setContentView(layout)
        dlg.show()
    }

    private fun refreshProfile() {
        if (!::profileRoot.isInitialized) return

        val targetId = if (viewingUserId.isNotEmpty()) viewingUserId else currentUserId

        profileTabPosts.setColorFilter(if (profileMode == 0) C.text else Color.argb(0xB0, 255, 255, 255))
        profileTabLikes.setColorFilter(if (profileMode == 1) C.accent else Color.argb(0xB0, 255, 255, 255))

        database.child("users").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nick = snapshot.child("nick").getValue(String::class.java) ?: "?"
                val avatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                val bio = snapshot.child("bio").getValue(String::class.java) ?: "Напиши что-нибудь о себе..."

                if (avatar.isNotEmpty()) {
                    try {
                        val bytes = Base64.decode(avatar, Base64.NO_WRAP)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        profileAvatar.setImageBitmap(makeCircularBitmap(bmp, dp(86)))
                    } catch (e: Exception) {
                        profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(nick), dp(86)))
                    }
                } else {
                    profileAvatar.setImageBitmap(makeCircularBitmap(createAvatarBitmap(nick), dp(86)))
                }

                profileNick.text = "@$nick"
                profileName.text = nick
                profileBio.text = bio
                profileFollowingCount.text = knownNames.size.toString()

                if (targetId == currentUserId) {
                    profileBtn1.text = "Редактировать профиль"
                    profileBtn1.setOnClickListener { showEditProfileDialog() }
                    profileMenuIv.visibility = View.VISIBLE
                    profileMenuIv.setOnClickListener { showEditProfileDialog() }
                } else {
                    profileBtn1.text = "Написать сообщение"
                    profileBtn1.setOnClickListener { openChat(targetId, nick) }
                    profileMenuIv.visibility = View.GONE
                }

                profileBtn2.text = "Поделиться"
                profileBtn2.setOnClickListener {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("nick", "@$nick"))
                    Toast.makeText(this@MainActivity, "Ник скопирован", Toast.LENGTH_SHORT).show()
                }

                database.child("posts").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(postSnapshot: DataSnapshot) {
                        var ownCount = 0
                        var sumLikes = 0
                        val grid = ArrayList<DataSnapshot>()
                        for (p in postSnapshot.children) {
                            val isMine = p.child("authorId").getValue(String::class.java) == targetId
                            val liked = p.child("likedBy").child(targetId).value != null
                            if (isMine) {
                                ownCount++
                                sumLikes += (p.child("likes").value as? Number)?.toInt() ?: 0
                            }
                            if (profileMode == 0 && isMine) grid.add(p)
                            if (profileMode == 1 && liked) grid.add(p)
                        }

                        profilePostsCount.text = ownCount.toString()
                        profileFollowersCount.text = "0"
                        profileLikesCount.text = sumLikes.toString()

                        profilePostsGrid.removeAllViews()
                        if (grid.isEmpty()) {
                            val empty = tv(if (profileMode == 0) "Нет постов" else "Нет лайков", 13f, fMed, C.textSec)
                            empty.gravity = Gravity.CENTER
                            empty.setPadding(0, dp(40), 0, dp(40))
                            profilePostsGrid.addView(empty)
                            return
                        }

                        val cell = (resources.displayMetrics.widthPixels - dp(32) - dp(4)) / 3
                        var i = 0
                        while (i < grid.size) {
                            val row = LinearLayout(this@MainActivity)
                            row.orientation = LinearLayout.HORIZONTAL
                            row.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
                            for (c in 0 until 3) {
                                val idx = i + c
                                val box = FrameLayout(this@MainActivity)
                                val p2 = LinearLayout.LayoutParams(cell, cell)
                                p2.setMargins(0, 0, dp(2), dp(2))
                                box.layoutParams = p2
                                if (idx < grid.size) {
                                    val p = grid[idx]
                                    val postId = p.key ?: ""
                                    val imgStr = p.child("images").getValue(String::class.java) ?: ""
                                    val txt = p.child("text").getValue(String::class.java) ?: ""
                                    val lc = (p.child("likes").value as? Number)?.toInt() ?: 0
                                    if (imgStr.isNotEmpty()) {
                                        val first = imgStr.split(",").firstOrNull { it.isNotEmpty() }
                                        if (first != null) {
                                            try {
                                                val bytes = Base64.decode(first, Base64.NO_WRAP)
                                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                val iv = ImageView(this@MainActivity)
                                                iv.setImageBitmap(bmp)
                                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                                iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
                                                val imgs = imgStr.split(",").filter { it.isNotEmpty() }
                                                iv.setOnClickListener { openViewer(imgs, 0) }
                                                box.addView(iv)
                                            } catch (e: Exception) {}
                                        }
                                    } else {
                                        box.background = glass(dp(6).toFloat(), true)
                                        val tt = tv(txt, 10f, fMed, C.text)
                                        tt.maxLines = 6
                                        tt.setPadding(dp(6), dp(6), dp(6), dp(18))
                                        box.addView(tt)
                                    }
                                    val likedBy = p.child("likedBy").children.map { it.key }.toSet()
                                    val heartQuick = icon(R.drawable.ic_heart_outline, C.text, dp(18))
                                    val hp = FrameLayout.LayoutParams(dp(18), dp(18))
                                    hp.gravity = Gravity.TOP or Gravity.END
                                    hp.setMargins(0, dp(6), dp(6), 0)
                                    box.addView(heartQuick, hp)
                                    val cntHidden = tv("♥ $lc", 12f, fSemi, C.text)
                                    attachLike(heartQuick, cntHidden, heartQuick, postId, currentUserId in likedBy)
                                    if (profileMode == 1) {
                                        val badge = tv("♥ $lc", 10f, fSemi, C.text)
                                        badge.setBackgroundColor(0x88000000.toInt())
                                        badge.setPadding(dp(5), dp(2), dp(5), dp(2))
                                        val bp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                                        bp.gravity = Gravity.BOTTOM or Gravity.START
                                        box.addView(badge, bp)
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

    // ================= РЕЗУЛЬТАТЫ ВЫБОРА ФОТО =================
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
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        iv.layoutParams = lp(dp(84), dp(84), dp(6))
                        iv.background = glass(dp(12).toFloat())
                        iv.clipToOutline = true
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

                avatarCache[currentUserId] = makeCircularBitmap(scaled, dp(40))
                profileAvatar.setImageBitmap(makeCircularBitmap(scaled, dp(86)))
                editAvatarView?.setImageBitmap(makeCircularBitmap(scaled, dp(96)))
            } catch (e: Exception) {}
        }
    }
}
