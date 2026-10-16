package com.pogudka.Ahem

import android.content.Context
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

internal const val DB_URL = ""

class MainActivity : Activity() {

    internal val uiHandler = Handler(Looper.getMainLooper())

    internal lateinit var auth: FirebaseAuth
    internal lateinit var database: DatabaseReference
    internal lateinit var rootFrame: FrameLayout
    internal lateinit var contentFrame: FrameLayout
    internal lateinit var navWrap: FrameLayout

    private lateinit var tabs: Array<View>
    private lateinit var navIcons: Array<ImageView>
    private var glassNav: GlassDrawable? = null

    internal lateinit var authBlock: LinearLayout
    internal lateinit var feedBlock: LinearLayout
    internal lateinit var feedEmail: EditText
    internal lateinit var feedNick: EditText
    internal lateinit var feedPass: EditText
    internal lateinit var feedStatus: TextView
    internal lateinit var pendingWrap: LinearLayout
    internal lateinit var pendingInfo: TextView
    internal lateinit var pendingRow: LinearLayout
    internal lateinit var feedPostText: EditText
    internal lateinit var feedSearch: EditText
    internal lateinit var feedBox: LinearLayout
    internal lateinit var feedScroll: ScrollView
    internal lateinit var regBtn: TextView
    internal lateinit var logBtn: TextView
    internal var currentUserId = ""
    internal var currentNick = ""
    internal var currentEmail = ""
    internal var currentAvatar = ""
    internal val pendingImages = ArrayList<String>()
    internal var isRegistering = false
    internal var isLoggingIn = false
    internal var feedQuery = ""
    internal val lastPosts = ArrayList<DataSnapshot>()

    internal lateinit var ptrHeader: FrameLayout
    internal lateinit var ptrSpin: ImageView
    internal lateinit var ptrRoot: LinearLayout
    internal var ptrStartY = 0f
    internal var ptrPulling = false
    internal var ptrLoading = false
    internal var ptrAnimator: ObjectAnimator? = null

    internal lateinit var chatListRoot: LinearLayout
    internal lateinit var convRoot: LinearLayout
    internal lateinit var chatListRows: LinearLayout
    internal lateinit var searchField: EditText
    internal lateinit var searchResults: LinearLayout
    internal lateinit var chatStatus: TextView
    internal lateinit var convTitle: TextView
    internal lateinit var convBox: LinearLayout
    internal lateinit var convScroll: ScrollView
    internal lateinit var msgField: EditText
    internal var currentChat = ""
    internal var currentChatOtherUid = ""
    internal var currentChatOtherNick = ""
    internal var convListener: ValueEventListener? = null
    internal var convListener2: ValueEventListener? = null
    internal var chatSnapChats: DataSnapshot? = null
    internal var chatSnapInbox: DataSnapshot? = null

    internal lateinit var profileRoot: LinearLayout
    internal lateinit var profileAvatar: ImageView
    internal lateinit var profileNick: TextView
    internal lateinit var profileName: TextView
    internal lateinit var profileBio: TextView
    internal lateinit var profilePostsCount: TextView
    internal lateinit var profileFollowersCount: TextView
    internal lateinit var profileFollowingCount: TextView
    internal lateinit var profileLikesCount: TextView
    internal lateinit var profileBtn1: TextView
    internal lateinit var profileBtn2: TextView
    internal lateinit var profileEditIv: ImageView
    internal lateinit var profileSettingsIv: ImageView
    internal lateinit var profileTabPosts: ImageView
    internal lateinit var profileTabLikes: ImageView
    internal lateinit var profilePostsGrid: LinearLayout
    internal lateinit var profileScroll: ScrollView
    internal var profileMode = 0
    internal var viewingUserId = ""
    internal var viewingNick = ""
    internal var currentProfileTargetId = ""
    internal var lastProfileTarget = ""
    internal var editAvatarView: ImageView? = null

    internal val PICK_IMAGES = 1002
    internal val PICK_AVATAR = 1003
    internal val ZOOM_MAX = 3f
    internal val FEED_LIMIT = 40

    internal var profileGen = 0
    internal var feedGen = 0
    internal var migrationScheduled = false
    internal var migrationRunning = false
    internal val gridCache = HashMap<String, ArrayList<DataSnapshot>>()
    internal val gridStatsCache = HashMap<String, Pair<Long, Long>>()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    internal val avatarCache = HashMap<String, Bitmap>()
    internal val imageCache = HashMap<String, Bitmap>()
    internal val mediaCache = HashMap<String, String>()
    internal val viewedLocally = HashSet<String>()
    internal val statsMap = HashMap<String, DataSnapshot>()
    internal val myFollowingIds = HashSet<String>()
    internal val viewsTvMap = HashMap<String, ArrayList<TextView>>()

    internal val fBold: Typeface by lazy { ResourcesCompat.getFont(this, R.font.unbounded)!! }
    internal val fSemi: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    internal val fMed: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    internal val fReg: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_regular)!! }

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    internal fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.bottomMargin = margin
        return p
    }

    internal fun wt(v: View, w: Float) {
        val cur = v.layoutParams
        if (cur is LinearLayout.LayoutParams) {
            cur.weight = w
            v.layoutParams = cur
        }
    }

    internal fun setNavVisible(v: Boolean) {
        if (!::navWrap.isInitialized) return
        navWrap.visibility = if (v) View.VISIBLE else View.GONE
        glassNav?.requestRefresh()
    }

    private fun hookGlassScroll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val refresh = object : View.OnScrollChangeListener {
            override fun onScrollChange(v: View?, sx: Int, sy: Int, ox: Int, oy: Int) {
                glassNav?.requestRefresh()
            }
        }
        if (::feedScroll.isInitialized) feedScroll.setOnScrollChangeListener(refresh)
        if (::profileScroll.isInitialized) profileScroll.setOnScrollChangeListener(refresh)
        if (::convScroll.isInitialized) convScroll.setOnScrollChangeListener(refresh)
    }

    internal fun saveSessionPrefs() {
        getSharedPreferences("ahem", Context.MODE_PRIVATE).edit()
            .putString("last_uid", currentUserId)
            .putString("last_nick", currentNick)
            .putString("last_email", currentEmail)
            .apply()
    }

    internal fun clearSessionPrefs() {
        getSharedPreferences("ahem", Context.MODE_PRIVATE).edit()
            .remove("last_uid").remove("last_nick").remove("last_email")
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(this, isDark(this))

        auth = FirebaseAuth.getInstance()
        val dbInstance = if (DB_URL.isEmpty()) FirebaseDatabase.getInstance() else FirebaseDatabase.getInstance(DB_URL)
        try {
            dbInstance.setPersistenceEnabled(true)
        } catch (e: Exception) {
        }
        database = dbInstance.reference

        val root = FrameLayout(this)
        root.setBackgroundColor(P.bg)
        rootFrame = root

        val content = FrameLayout(this)
        content.layoutParams = FrameLayout.LayoutParams(-1, -1)
        contentFrame = content

        val feedTab = buildFeed(this)
        val videoTab = buildVideo(this)
        val chatTab = buildChats(this)
        val profTab = buildProfile(this)
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))

        navWrap = FrameLayout(this)
        val nwp = FrameLayout.LayoutParams(-1, -2)
        nwp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        nwp.setMargins(dp(16), 0, dp(16), dp(12))
        navWrap.layoutParams = nwp

        val glass = GlassDrawable()
        glass.setLiveBlur(true)
        glass.setRadius(dp(28).toFloat())
        glass.setBlurRadius(dp(30).toFloat())
        glass.setGlassTint(P.navGlass)
        glass.setBorder(P.border, dp(1).toFloat())
        glassNav = glass

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = glass
        nav.setPadding(dp(10), dp(8), dp(10), dp(8))
        nav.layoutParams = FrameLayout.LayoutParams(-1, -2)

        val icons = intArrayOf(R.drawable.ic_feed, R.drawable.ic_video, R.drawable.ic_chat, R.drawable.ic_profile)
        navIcons = Array(4) { i ->
            val iv = icon(this, icons[i], P.navInactive, dp(24))
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

        val navSpace = dp(96)
        feedBox.setPadding(0, 0, 0, navSpace)
        profilePostsGrid.setPadding(0, 0, 0, navSpace)
        convBox.setPadding(0, 0, 0, dp(8))
        chatListRows.setPadding(0, 0, 0, navSpace)

        glass.attach(window, contentFrame, navWrap)
        hookGlassScroll()

        showTab(0)

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && currentUserId.isEmpty()) {
                currentUserId = user.uid
                viewingUserId = currentUserId
                viewingNick = ""
                val prefs = getSharedPreferences("ahem", Context.MODE_PRIVATE)
                val pn = prefs.getString("last_nick", "") ?: ""
                val pe = prefs.getString("last_email", "") ?: ""
                if (pn.isNotEmpty()) currentNick = pn
                if (pe.isNotEmpty()) currentEmail = pe
                if (::feedBlock.isInitialized && feedBlock.visibility == View.GONE) enterFeedMode(this)
                if (::profileRoot.isInitialized) refreshProfile(this)
                loadMyFollowing(this) { renderFeed(this) }
                database.child("users").child(currentUserId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentNick = snapshot.child("nick").getValue(String::class.java) ?: currentNick.ifEmpty { "Аноним" }
                            currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                            currentEmail = snapshot.child("email").getValue(String::class.java) ?: currentEmail
                            saveSessionPrefs()
                            if (::profileRoot.isInitialized) refreshProfile(this@MainActivity)
                            renderFeed(this@MainActivity)
                            glassNav?.requestRefresh()
                        }
                        override fun onCancelled(error: DatabaseError) { dbDenied(this@MainActivity, error) }
                    })
            } else if (user == null && currentUserId.isNotEmpty()) {
                currentUserId = ""
                currentNick = ""
                currentEmail = ""
                currentAvatar = ""
                viewingUserId = ""
                viewingNick = ""
                lastProfileTarget = ""
                myFollowingIds.clear()
                imageCache.clear()
                avatarCache.clear()
                mediaCache.clear()
                viewsTvMap.clear()
                gridCache.clear()
                gridStatsCache.clear()
                clearSessionPrefs()
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
        ptrAnimator?.cancel()
        try {
            val me = currentUserId
            val peer = currentChat
            convListener?.let { database.child("chats").child(chatPairKey(me, peer)).removeEventListener(it) }
            convListener2?.let { database.child("conversations").child(me).removeEventListener(it) }
        } catch (e: Exception) {}
    }

    internal fun showTab(i: Int, keep: Boolean = false) {
        for (idx in tabs.indices) tabs[idx].visibility = if (idx == i) View.VISIBLE else View.GONE
        for (idx in 0 until 4) navIcons[idx].setColorFilter(if (idx == i) P.accent else P.navInactive)
        setNavVisible(true)
        uiHandler.postDelayed({ glassNav?.requestRefresh() }, 300)
        if (i == 3) {
            if (!keep) {
                viewingUserId = currentUserId
                viewingNick = ""
                profileMode = 0
            }
            refreshProfile(this)
        }
        if (i == 2) loadConversations(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleActivityResult(this, requestCode, resultCode, data)
    }
}
