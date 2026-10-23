package com.pogudka.Ahem

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

internal const val DB_URL = ""

class MainActivity : ComponentActivity() {

    internal val uiHandler = Handler(Looper.getMainLooper())

    internal lateinit var auth: FirebaseAuth
    internal lateinit var database: DatabaseReference

    internal val avatarCache = mutableStateOf(HashMap<String, String>())
    internal val mediaCache = HashMap<String, String>()

    internal val sessionUid = mutableStateOf("")
    internal var currentUserId: String
        get() = sessionUid.value
        set(value) { sessionUid.value = value }

    internal val tab = mutableStateOf(0)
    internal val panelVisible = mutableStateOf(true)
    internal val feedScrollTick = mutableStateOf(0)
    internal val dialog = mutableStateOf<String?>(null)
    internal val dialogArg = mutableStateOf("")
    internal val viewerImages = mutableStateOf<List<String>>(emptyList())
    internal val viewerStart = mutableStateOf(0)
    internal val refreshing = mutableStateOf(false)

    internal var currentNick = ""
    internal var currentEmail = ""
    internal var currentAvatar = ""
    internal val myFollowingIds = HashSet<String>()
    internal val viewedLocally = HashSet<String>()
    internal val statsMap = HashMap<String, DataSnapshot>()
    internal val statsVer = mutableStateOf(0)

    internal val optLiked = HashMap<String, Boolean>()
    internal val optCount = HashMap<String, Long>()
    internal val optViews = HashMap<String, Long>()

    internal val emailInput = mutableStateOf("")
    internal val nickInput = mutableStateOf("")
    internal val passInput = mutableStateOf("")
    internal val feedStatus = mutableStateOf("")
    internal var isRegistering = false
    internal var isLoggingIn = false

    internal val postsRaw = mutableStateListOf<DataSnapshot>()
    internal val feedQuery = mutableStateOf("")
    internal val pendingImages = mutableStateListOf<String>()
    internal var feedGen = 0
    internal var migrationScheduled = false
    internal var migrationRunning = false

    internal val profTarget = mutableStateOf("")
    internal val profNick = mutableStateOf("")
    internal val profName = mutableStateOf("")
    internal val profBio = mutableStateOf("")
    internal val profAvatarB64 = mutableStateOf("")
    internal val editAvatarB64 = mutableStateOf("")
    internal val profFollowers = mutableStateOf(0L)
    internal val profFollowing = mutableStateOf(0L)
    internal var viewingUserId = ""
    internal var currentProfileTargetId = ""
    internal var profileGen = 0

    internal val chatOpen = mutableStateOf(false)
    internal var chatPeerUid = ""
    internal var chatPeerNick = ""
    internal val chatMsgs = mutableStateListOf<Msg>()
    internal val peersList = mutableStateListOf<PeerRow>()
    internal val chatStatus = mutableStateOf("")

    internal val PICK_IMAGES = 1002
    internal val PICK_AVATAR = 1003
    internal val ZOOM_MAX = 3f
    internal val FEED_LIMIT = 40

    internal val fBold: Typeface by lazy { ResourcesCompat.getFont(this, R.font.unbounded)!! }
    internal val fSemi: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    internal val fMed: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_medium)!! }
    internal val fReg: Typeface by lazy { ResourcesCompat.getFont(this, R.font.ubuntu_regular)!! }

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    internal fun setNavVisible(v: Boolean) { panelVisible.value = v }

    internal fun saveSessionPrefs() {
        getSharedPreferences("ahem", Context.MODE_PRIVATE).edit()
            .putString("last_uid", currentUserId).putString("last_nick", currentNick).putString("last_email", currentEmail).apply()
    }
    internal fun clearSessionPrefs() {
        getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().remove("last_uid").remove("last_nick").remove("last_email").apply()
    }

    internal fun setSession(uid: String) {
        sessionUid.value = uid
        if (uid.isEmpty()) {
            currentNick = ""; currentEmail = ""; currentAvatar = ""
            viewingUserId = ""; currentProfileTargetId = ""
            chatOpen.value = false; chatPeerUid = ""; chatPeerNick = ""
            myFollowingIds.clear(); viewedLocally.clear(); statsMap.clear(); statsVer.value++
            optLiked.clear(); optCount.clear(); optViews.clear()
            postsRaw.clear(); pendingImages.clear(); peersList.clear(); chatMsgs.clear()
            profTarget.value = ""; profNick.value = ""; profName.value = ""; profBio.value = ""
            profAvatarB64.value = ""; editAvatarB64.value = ""
            profFollowers.value = 0L; profFollowing.value = 0L
            clearSessionPrefs()
        }
    }

    internal fun showTab(i: Int, keep: Boolean = false) {
        tab.value = i; panelVisible.value = true
        if (i == 3) { if (!keep) viewingUserId = currentUserId; refreshProfile(this) }
        if (i == 2) loadConversations(this)
    }

    private var authListener: FirebaseAuth.AuthStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme(this, isDark(this))
        auth = FirebaseAuth.getInstance()
        val dbInstance = if (DB_URL.isEmpty()) FirebaseDatabase.getInstance() else FirebaseDatabase.getInstance(DB_URL)
        try { dbInstance.setPersistenceEnabled(true) } catch (e: Exception) {}
        database = dbInstance.reference

        setContent { AhemRoot(this) }

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && currentUserId.isEmpty()) {
                setSession(user.uid)
                viewingUserId = user.uid
                val prefs = getSharedPreferences("ahem", Context.MODE_PRIVATE)
                val pn = prefs.getString("last_nick", "") ?: ""; val pe = prefs.getString("last_email", "") ?: ""
                if (pn.isNotEmpty()) currentNick = pn; if (pe.isNotEmpty()) currentEmail = pe
                loadMyFollowing(this) {}
                enterFeedMode(this)
                database.child("users").child(user.uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentNick = snapshot.child("nick").getValue(String::class.java) ?: currentNick.ifEmpty { "Аноним" }
                        currentAvatar = snapshot.child("avatar").getValue(String::class.java) ?: ""
                        profAvatarB64.value = currentAvatar
                        if (currentAvatar.isNotEmpty()) putAvatar(this@MainActivity, user.uid, currentAvatar)
                        currentEmail = snapshot.child("email").getValue(String::class.java) ?: currentEmail
                        saveSessionPrefs(); refreshProfile(this@MainActivity)
                    }
                    override fun onCancelled(error: DatabaseError) { dbDenied(this@MainActivity, error) }
                })
            } else if (user == null && currentUserId.isNotEmpty()) {
                setSession("")
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        authListener?.let { auth.removeAuthStateListener(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleActivityResult(this, requestCode, resultCode, data)
    }
}

@Composable
private fun AhemRoot(act: MainActivity) {
    val t by act.tab
    val panel by act.panelVisible
    val chatOpen by act.chatOpen
    val dlg by act.dialog
    val pt by act.profTarget
    val me by act.sessionUid
    val icons = intArrayOf(R.drawable.ic_feed, R.drawable.ic_video, R.drawable.ic_chat, R.drawable.ic_profile)
    val showPanel = panel && !(t == 2 && chatOpen)

    BackHandler(enabled = dlg != null || chatOpen || (t == 3 && pt.isNotEmpty() && pt != me)) {
        when {
            dlg != null -> act.dialog.value = null
            chatOpen -> closeConversation(act)
            else -> { act.viewingUserId = me; refreshProfile(act) }
        }
    }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = fontUbuntuReg)) {
        Box(Modifier.fillMaxSize().background(ic(P.bg))) {
            when (t) {
                0 -> FeedScreen(act)
                1 -> VideoScreen()
                2 -> ChatScreen(act)
                3 -> ProfileScreen(act)
            }
            if (showPanel) {
                LiquidGlass(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).height(64.dp),
                    border = ic(P.border)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        for (i in 0 until 4) {
                            val active = i == t
                            IconButton(onClick = { act.showTab(i) }, modifier = Modifier.weight(1f)) {
                                Icon(painter = painterResource(icons[i]), contentDescription = null, tint = if (active) ic(P.accent) else ic(P.navInactive))
                            }
                        }
                    }
                }
            }
            DialogHost(act)
        }
    }
}
