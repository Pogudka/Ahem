package com.example.messenger

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val MASTER_KEY = "ludarex-babylon-2026"
const val CHAT_PORT = 9999
const val FEED_PORT = 8080
const val FEED_MAX_W = 1280
const val FEED_QUALITY = 85

class Session(var sendCk: ByteArray, var recvCk: ByteArray, var sendN: Long = 0, var recvN: Long = 0)

object Crypto {
    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    fun hmac(key: ByteArray, msg: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(key, "HmacSHA256")); return m.doFinal(msg)
    }
    fun groupKey(): ByteArray = sha256(MASTER_KEY.toByteArray())
    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun unhex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun step(ck: ByteArray, n: Long): Pair<ByteArray, ByteArray> {
        val base = "ratchet:$n".toByteArray()
        return hmac(ck, base + byteArrayOf(1)) to hmac(ck, base + byteArrayOf(2))
    }
    fun makeSession(sharedHex: String): Session {
        val ck0 = hmac(sha256(sharedHex.toByteArray()), "chain-init".toByteArray())
        return Session(ck0.copyOf(), ck0.copyOf())
    }
    fun enc(key: ByteArray, plain: String): String {
        val nonce = ByteArray(12); SecureRandom().nextBytes(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return hex(nonce.plus(c.doFinal(plain.toByteArray())))
    }
    fun dec(key: ByteArray, s: String): String? = try {
        val d = unhex(s)
        if (d.size < 28) null else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, d.copyOfRange(0, 12)))
            String(c.doFinal(d.copyOfRange(12, d.size)))
        }
    } catch (e: Exception) { null }
    fun fingerprint(key: ByteArray): String = hex(sha256(key).copyOfRange(0, 4))
    fun safePrime(bits: Int): BigInteger {
        val r = SecureRandom()
        while (true) {
            val q = BigInteger(bits - 1, r).setBit(bits - 2).setBit(0).nextProbablePrime()
            val p = q.shiftLeft(1).add(BigInteger.ONE)
            if (p.bitLength() == bits && p.isProbablePrime(64)) return p
        }
    }
}

object FeedApi {
    private fun readLine(acc: StringBuilder, ins: java.io.InputStream): String? {
        while (true) {
            val nl = acc.indexOf("\n")
            if (nl >= 0) { val r = acc.substring(0, nl); acc.delete(0, nl + 1); return r }
            val b = ins.read(); if (b == -1) return null; acc.append(b.toChar())
        }
    }
    private fun ask(ip: String, req: String): String {
        var s: Socket? = null
        try {
            s = Socket(ip, FEED_PORT)
            s.getOutputStream().write((req + "\n").toByteArray()); s.getOutputStream().flush()
            val acc = StringBuilder(); return readLine(acc, s.getInputStream()) ?: "ERR|eof"
        } catch (e: Exception) { return "ERR|connect" }
        finally { try { s?.close() } catch (_: Exception) {} }
    }
    private fun askMulti(ip: String, req: String): List<String> {
        var s: Socket? = null
        val out = ArrayList<String>()
        try {
            s = Socket(ip, FEED_PORT)
            s.getOutputStream().write((req + "\n").toByteArray()); s.getOutputStream().flush()
            val acc = StringBuilder()
            while (true) { val l = readLine(acc, s.getInputStream()) ?: break; if (l == "END") break; out.add(l) }
        } catch (e: Exception) { }
        finally { try { s?.close() } catch (_: Exception) {} }
        return out
    }
    fun register(ip: String, email: String, nick: String, pass: String) = ask(ip, "REG|$email|$nick|$pass")
    fun login(ip: String, email: String, pass: String) = ask(ip, "LOGIN|$email|$pass")
    fun post(ip: String, id: String, body: String, img: String = "") = ask(ip, "POST|$id|$body|$img")
    fun feed(ip: String, id: String) = askMulti(ip, "FEED|$id")
    fun like(ip: String, pid: String, uid: String) = ask(ip, "LIKE|$pid|$uid")
    fun follow(ip: String, nick: String, uid: String) = ask(ip, "FOLLOW|$nick|$uid")
}

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences

    private lateinit var tabs: Array<View>
    private lateinit var tabBtns: Array<Button>

    // лента
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
    private var feedId = ""
    private var feedLoggedNick = ""
    private val pendingImages = ArrayList<String>()

    // чат
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var ipField: EditText
    private lateinit var nameField: EditText
    private lateinit var peerField: EditText
    private lateinit var msgField: EditText
    @Volatile private var out: java.io.OutputStream? = null
    @Volatile private var socket: Socket? = null
    private val buf = StringBuilder()
    private val sessions = mutableMapOf<String, Session>()
    private val pendingA = mutableMapOf<String, BigInteger>()
    private val pendingP = mutableMapOf<String, BigInteger>()
    private var currentPeer = ""

    private lateinit var profileText: TextView
    private val PICK_IMAGE = 1001      // чат: одиночное
    private val PICK_IMAGES = 1002     // лента: несколько
    private var photoTarget = "chat"

    private fun now() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun me() = nameField.text.toString().ifEmpty { "Аноним" }
    private fun host() = ipField.text.toString().ifEmpty { "127.0.0.1" }
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h); p.bottomMargin = margin; return p
    }
    private fun wt(v: View, w: Float) { (v.layoutParams as LinearLayout.LayoutParams).weight = w }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lud", MODE_PRIVATE)
        loadAllSessions()

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL
        val content = FrameLayout(this); content.layoutParams = lp(-1, 0, 0); wt(content, 1f)
        val panel = LinearLayout(this); panel.orientation = LinearLayout.HORIZONTAL

        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChat()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))

        val titles = arrayOf("📰 Лента", "🎬 Видео", "💬 Чаты", "👤 Профиль")
        tabBtns = Array(4) { i ->
            val b = Button(this); b.text = titles[i]
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            b.setOnClickListener { showTab(i) }; b
        }
        for (b in tabBtns) panel.addView(b)

        root.addView(content); root.addView(panel)
        setContentView(root)
        showTab(0)
    }

    private fun showTab(i: Int) {
        for (idx in tabs.indices) tabs[idx].visibility = if (idx == i) View.VISIBLE else View.GONE
        for (idx in tabBtns.indices) tabBtns[idx].setBackgroundColor(if (idx == i) 0xFF222222.toInt() else 0xFFCCCCCC.toInt())
        if (i == 3) refreshProfile()
    }

    // ---------- декод с downsampling (миниатюры не жрут память) ----------
    private fun decodeSampled(bytes: ByteArray, reqW: Int): Bitmap? {
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / s / 2 >= reqW) s *= 2
        val o2 = BitmapFactory.Options(); o2.inSampleSize = s
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }
    private fun decodeB64(b64: String, reqW: Int): Bitmap? {
        val bytes = try { Base64.decode(b64, Base64.NO_WRAP) } catch (e: Exception) { return null }
        return decodeSampled(bytes, reqW)
    }
    // сжать uri -> high-res base64 (до FEED_MAX_W, q FEED_QUALITY)
    private fun compressUri(uri: android.net.Uri, maxW: Int, quality: Int): String? = try {
        val ins = contentResolver.openInputStream(uri) ?: return null
        val bmp = BitmapFactory.decodeStream(ins); ins.close()
        val scaled = if (bmp.width > maxW) Bitmap.createScaledBitmap(bmp, maxW, maxW * bmp.height / bmp.width, true) else bmp
        val baos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }

    // ================= ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)

        authBlock = LinearLayout(this); authBlock.orientation = LinearLayout.VERTICAL
        feedEmail = EditText(this); feedEmail.hint = "email (логин ленты)"; feedEmail.setText(prefs.getString("feed_email", ""))
        feedNick = EditText(this); feedNick.hint = "ник"; feedNick.setText(prefs.getString("feed_nick", ""))
        feedPass = EditText(this); feedPass.hint = "пароль"; feedPass.inputType = 1
        val rowAuth = LinearLayout(this); rowAuth.orientation = LinearLayout.HORIZONTAL
        val regBtn = Button(this); regBtn.text = "Регистрация"; regBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(regBtn, 1f)
        val logBtn = Button(this); logBtn.text = "Вход"; logBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(logBtn, 1f)
        rowAuth.addView(regBtn); rowAuth.addView(logBtn)
        feedStatus = TextView(this); feedStatus.text = "не залогинен"
        authBlock.addView(feedEmail); authBlock.addView(feedNick); authBlock.addView(feedPass)
        authBlock.addView(rowAuth); authBlock.addView(feedStatus)

        feedBlock = LinearLayout(this); feedBlock.orientation = LinearLayout.VERTICAL
        val headerRow = LinearLayout(this); headerRow.orientation = LinearLayout.HORIZONTAL
        feedHeader = TextView(this); feedHeader.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(feedHeader, 1f)
        val switchBtn = Button(this); switchBtn.text = "сменить"; switchBtn.layoutParams = lp(-2, -2, 0)
        headerRow.addView(feedHeader); headerRow.addView(switchBtn)

        // предпросмотр выбранных фото
        pendingWrap = LinearLayout(this); pendingWrap.orientation = LinearLayout.VERTICAL; pendingWrap.visibility = View.GONE
        val pendHead = LinearLayout(this); pendHead.orientation = LinearLayout.HORIZONTAL
        pendingInfo = TextView(this); pendingInfo.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(pendingInfo, 1f)
        val clearBtn = Button(this); clearBtn.text = "✖"; clearBtn.layoutParams = lp(-2, -2, 0)
        pendHead.addView(pendingInfo); pendHead.addView(clearBtn)
        val pendScroll = HorizontalScrollView(this); pendScroll.layoutParams = lp(-1, -2, 0)
        pendingRow = LinearLayout(this); pendingRow.orientation = LinearLayout.HORIZONTAL; pendScroll.addView(pendingRow)
        pendingWrap.addView(pendHead); pendingWrap.addView(pendScroll)

        feedPostText = EditText(this); feedPostText.hint = "Подпись к посту (без символа |)"
        val rowPost = LinearLayout(this); rowPost.orientation = LinearLayout.HORIZONTAL
        val pubBtn = Button(this); pubBtn.text = "Опубликовать"; pubBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(pubBtn, 2f)
        val phBtn = Button(this); phBtn.text = "📷 несколько"; phBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(phBtn, 1f)
        rowPost.addView(pubBtn); rowPost.addView(phBtn)
        val rowAct = LinearLayout(this); rowAct.orientation = LinearLayout.HORIZONTAL
        val feedBtn = Button(this); feedBtn.text = "⟳ Лента"; feedBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(feedBtn, 1f)
        feedFollowNick = EditText(this); feedFollowNick.hint = "@ник подписки"; feedFollowNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(feedFollowNick, 2f)
        val folBtn = Button(this); folBtn.text = "Подписаться"; folBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(folBtn, 1f)
        rowAct.addView(feedBtn); rowAct.addView(feedFollowNick); rowAct.addView(folBtn)
        feedBox = LinearLayout(this); feedBox.orientation = LinearLayout.VERTICAL
        feedScroll = ScrollView(this); feedScroll.addView(feedBox); feedScroll.layoutParams = lp(-1, 0, 0); wt(feedScroll, 1f)
        feedBlock.addView(headerRow); feedBlock.addView(pendingWrap); feedBlock.addView(feedPostText)
        feedBlock.addView(rowPost); feedBlock.addView(rowAct); feedBlock.addView(feedScroll)
        feedBlock.visibility = View.GONE

        regBtn.setOnClickListener {
            val e = feedEmail.text.toString(); val n = feedNick.text.toString(); val pw = feedPass.text.toString()
            if (e.isEmpty() || n.isEmpty() || pw.isEmpty()) { feedStatus.text = "заполни все поля"; return@setOnClickListener }
            Thread {
                val r = FeedApi.register(host(), e, n, pw)
                handler.post {
                    if (r.startsWith("OK|")) {
                        feedId = r.substring(3); feedLoggedNick = n
                        prefs.edit().putString("feed_email", e).putString("feed_nick", n).apply()
                        enterFeedMode()
                    } else feedStatus.text = if (r == "ERR|connect") "нет связи с сервером" else "ошибка: $r"
                }
            }.start()
        }
        logBtn.setOnClickListener {
            val e = feedEmail.text.toString(); val pw = feedPass.text.toString()
            if (e.isEmpty() || pw.isEmpty()) { feedStatus.text = "email + пароль"; return@setOnClickListener }
            Thread {
                val r = FeedApi.login(host(), e, pw)
                handler.post {
                    if (r.startsWith("OK|")) {
                        val parts = r.split("|"); feedId = parts[1]; feedLoggedNick = if (parts.size > 2) parts[2] else "?"
                        prefs.edit().putString("feed_email", e).putString("feed_nick", feedLoggedNick).apply()
                        enterFeedMode()
                    } else feedStatus.text = if (r == "ERR|connect") "нет связи с сервером" else "ошибка: $r"
                }
            }.start()
        }
        switchBtn.setOnClickListener { exitFeedMode() }
        clearBtn.setOnClickListener { pendingImages.clear(); renderPending() }
        phBtn.setOnClickListener {
            if (feedId.isEmpty()) return@setOnClickListener
            photoTarget = "feed"; pickImages()
        }
        pubBtn.setOnClickListener {
            if (feedId.isEmpty()) return@setOnClickListener
            val body = feedPostText.text.toString().replace("|", "/")
            val img = pendingImages.joinToString(",")
            if (body.isEmpty() && img.isEmpty()) return@setOnClickListener
            Thread { FeedApi.post(host(), feedId, body, img); handler.post { pendingImages.clear(); renderPending(); refreshFeedAsync() } }.start()
            feedPostText.setText("")
        }
        feedBtn.setOnClickListener { if (feedId.isNotEmpty()) refreshFeedAsync() }
        folBtn.setOnClickListener {
            if (feedId.isEmpty()) return@setOnClickListener
            val n = feedFollowNick.text.toString(); if (n.isEmpty()) return@setOnClickListener
            Thread { FeedApi.follow(host(), n, feedId); handler.post { refreshFeedAsync() } }.start()
            feedFollowNick.setText("")
        }

        col.addView(authBlock); col.addView(feedBlock)
        return col
    }

    private fun enterFeedMode() {
        authBlock.visibility = View.GONE; feedBlock.visibility = View.VISIBLE
        feedHeader.text = "@$feedLoggedNick (id $feedId)"; refreshFeedAsync()
    }
    private fun exitFeedMode() {
        feedBlock.visibility = View.GONE; authBlock.visibility = View.VISIBLE
        feedStatus.text = "не залогинен"; feedId = ""; feedLoggedNick = ""
        pendingImages.clear(); renderPending()
    }
    private fun renderPending() {
        pendingRow.removeAllViews()
        if (pendingImages.isEmpty()) { pendingWrap.visibility = View.GONE; return }
        pendingWrap.visibility = View.VISIBLE
        pendingInfo.text = "выбрано фото: ${pendingImages.size}"
        for (i in pendingImages.indices) {
            val bmp = decodeB64(pendingImages[i], 160) ?: continue
            val iv = ImageView(this); iv.setImageBitmap(bmp); iv.adjustViewBounds = true
            iv.layoutParams = lp(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT, 4)
            val idx = i
            iv.setOnClickListener { openViewer(pendingImages, idx) }
            pendingRow.addView(iv)
        }
    }
    private fun refreshFeedAsync() {
        if (feedId.isEmpty()) return
        Thread { val lines = FeedApi.feed(host(), feedId); handler.post { renderFeed(lines) } }.start()
    }
    private fun renderFeed(lines: List<String>) {
        feedBox.removeAllViews(); var any = false
        for (ln in lines) if (ln.startsWith("P|")) { addPostCard(ln); any = true }
        if (!any) { val tv = TextView(this); tv.text = "лента пуста (или нет связи) — подпишись / напиши пост"; feedBox.addView(tv) }
        feedScroll.post { feedScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addPostCard(line: String) {
        val rest = line.substring(2)
        val li = rest.lastIndexOf('|')
        val rawImg = if (li >= 0) rest.substring(li + 1) else ""
        val head = if (li >= 0) rest.substring(0, li) else rest
        val hp = head.split("|")
        if (hp.size < 5) return
        val postId = hp[0]; val postNick = hp[1]; val postTs = hp[2]; val postLikes = hp[3]
        val postBody = if (hp.size > 4) hp.subList(4, hp.size).joinToString("|") else ""
        val images = rawImg.split(",").filter { it.isNotEmpty() }

        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setPadding(8, 8, 8, 8)
        card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8); card.setBackgroundColor(0xFFEEEEEE.toInt())
        val title = TextView(this); title.text = "[$postId] @$postNick  $postTs   ♥$postLikes"; card.addView(title)
        if (postBody.isNotEmpty()) { val bt = TextView(this); bt.text = postBody; bt.setPadding(0, 4, 0, 4); card.addView(bt) }
        if (images.isNotEmpty()) {
            val sc = HorizontalScrollView(this); sc.layoutParams = lp(-1, -2, 4)
            val rowIv = LinearLayout(this); rowIv.orientation = LinearLayout.HORIZONTAL; sc.addView(rowIv)
            for (i in images.indices) {
                val bmp = decodeB64(images[i], 240) ?: continue
                val iv = ImageView(this); iv.setImageBitmap(bmp); iv.adjustViewBounds = true
                iv.layoutParams = lp(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT, 4)
                val idx = i
                iv.setOnClickListener { openViewer(images, idx) }
                rowIv.addView(iv)
            }
            card.addView(sc)
        }
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val likeBtn = Button(this); likeBtn.text = "♥"; likeBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(likeBtn, 1f)
        likeBtn.setOnClickListener { if (feedId.isNotEmpty()) Thread { FeedApi.like(host(), postId, feedId); handler.post { refreshFeedAsync() } }.start() }
        row.addView(likeBtn)
        if (postNick != feedLoggedNick) {
            val fb = Button(this); fb.text = "+ @$postNick"; fb.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0); wt(fb, 2f)
            fb.setOnClickListener { if (feedId.isNotEmpty()) Thread { FeedApi.follow(host(), postNick, feedId); handler.post { refreshFeedAsync() } }.start() }
            row.addView(fb)
        }
        card.addView(row)
        feedBox.addView(card)
    }

    // ---------- полноэкранный просмотрщик с ◀ ▶ ----------
    private fun openViewer(images: List<String>, start: Int) {
        if (images.isEmpty()) return
        val wrap = LinearLayout(this); wrap.orientation = LinearLayout.VERTICAL; wrap.setPadding(8, 8, 8, 8)
        val img = ImageView(this); img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.layoutParams = lp(-1, 0, 0); wt(img, 1f)
        val bar = LinearLayout(this); bar.orientation = LinearLayout.HORIZONTAL
        val prev = Button(this); prev.text = "◀"; prev.layoutParams = lp(0, -2, 0); wt(prev, 1f)
        val cnt = TextView(this); cnt.gravity = Gravity.CENTER; cnt.layoutParams = lp(0, -2, 0); wt(cnt, 2f)
        val next = Button(this); next.text = "▶"; next.layoutParams = lp(0, -2, 0); wt(next, 1f)
        bar.addView(prev); bar.addView(cnt); bar.addView(next)
        wrap.addView(img); wrap.addView(bar)

        var idx = start.coerceIn(0, images.size - 1)
        fun show() {
            val bmp = decodeB64(images[idx], FEED_MAX_W)   // полное high-res качество
            if (bmp != null) img.setImageBitmap(bmp)
            cnt.text = "${idx + 1} / ${images.size}"
            prev.isEnabled = idx > 0; next.isEnabled = idx < images.size - 1
        }
        prev.setOnClickListener { if (idx > 0) { idx--; show() } }
        next.setOnClickListener { if (idx < images.size - 1) { idx++; show() } }
        show()
        AlertDialog.Builder(this).setView(wrap).setOnDismissListener { }.show()
    }

    // ================= ВИДЕО =================
    private fun buildVideo(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        val tv = TextView(this)
        tv.text = "🎬 Видео-лента (TikTok-стиль)\n\nКирпич 2: вертикальные клипы свайпом, файлы на сервере по URL, ExoPlayer.\nТранскод/CDN — после переезда на ПК."
        col.addView(tv); return col
    }

    // ================= ПРОФИЛЬ =================
    private fun buildProfile(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        profileText = TextView(this); col.addView(profileText); return col
    }
    private fun refreshProfile() {
        val chatName = if (::nameField.isInitialized) nameField.text.toString() else ""
        profileText.text =
            "👤 Профиль\n\n" +
            "Лента: @${if (feedLoggedNick.isEmpty()) "—" else feedLoggedNick} (id ${if (feedId.isEmpty()) "—" else feedId})\n" +
            "Чат: ${if (chatName.isEmpty()) "—" else chatName}\n\n" +
            "Кирпич 3: аватар, био, OAuth Google/Яндекс/Mail — после haipuch.ru + HTTPS.\n" +
            "Лента и Чаты — два входа; держи одинаковый ник, чтобы выглядеть одним аккаунтом."
    }

    // ================= ЧАТ (без изменений) =================
    private fun buildChat(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        ipField = EditText(this); ipField.hint = "IP сервера"; ipField.setText(prefs.getString("chat_ip", "127.0.0.1"))
        nameField = EditText(this); nameField.hint = "Имя (чат)"; nameField.setText(prefs.getString("chat_name", ""))
        peerField = EditText(this); peerField.hint = "Собеседник (secret)"
        val connectBtn = Button(this); connectBtn.text = "Подключиться"
        val secretBtn = Button(this); secretBtn.text = "🔐 Secret chat"
        chatBox = LinearLayout(this); chatBox.orientation = LinearLayout.VERTICAL
        chatScroll = ScrollView(this); chatScroll.addView(chatBox); chatScroll.layoutParams = lp(-1, 0, 0); wt(chatScroll, 1f)
        msgField = EditText(this); msgField.hint = "Сообщение... (/reset ИМЯ)"
        val imgBtn = Button(this); imgBtn.text = "📷 Фото"
        val sendBtn = Button(this); sendBtn.text = "Отправить"

        secretBtn.setOnClickListener {
            val peer = peerField.text.toString()
            if (peer.isEmpty()) { addTextLine("укажи собеседника"); return@setOnClickListener }
            if (sessions.containsKey(peer)) { currentPeer = peer; addTextLine("сессия с $peer уже активна (с диска)"); return@setOnClickListener }
            pendingA.remove(peer); pendingP.remove(peer); currentPeer = peer
            Thread {
                val p = Crypto.safePrime(1024); val g = BigInteger.valueOf(2)
                val a = BigInteger(256, SecureRandom()); val A = g.modPow(a, p)
                pendingA[peer] = a; pendingP[peer] = p
                sendLine("@$peer DHREQ ${me()} ${p.toString(16)} ${g.toString(16)} ${A.toString(16)}")
                addTextLine("предложение ключа отправлено $peer...")
            }.start()
        }
        imgBtn.setOnClickListener { photoTarget = "chat"; pickImage() }
        connectBtn.setOnClickListener {
            val ip = ipField.text.toString(); val n = me()
            prefs.edit().putString("chat_ip", ip).putString("chat_name", n).apply()
            handler.post { chatBox.removeAllViews() }; buf.setLength(0)
            Thread {
                try {
                    val s = Socket(ip, CHAT_PORT); socket = s; out = s.getOutputStream()
                    sendLine("REG $n"); addTextLine("=== подключено ===")
                    val b = ByteArray(8192)
                    while (true) { val nn = s.getInputStream().read(b); if (nn <= 0) break; handler.post { addText(String(b, 0, nn)) } }
                    out = null; socket = null; addTextLine("=== оффлайн ===")
                } catch (e: Exception) { out = null; socket = null; addTextLine("=== ошибка: " + e.message + " ===") }
            }.start()
        }
        sendBtn.setOnClickListener {
            val t = msgField.text.toString(); if (t.isEmpty()) return@setOnClickListener
            if (t == "/exit") { try { socket?.close() } catch (_: Exception) {}; out = null; socket = null; addTextLine("=== ты вышел ==="); msgField.setText(""); return@setOnClickListener }
            if (t.startsWith("/reset ")) { val peer = t.substring(7); sessions.remove(peer); pendingA.remove(peer); pendingP.remove(peer); sessionFile(peer).delete(); if (currentPeer == peer) currentPeer = ""; addTextLine("сессия с $peer сброшена"); msgField.setText(""); return@setOnClickListener }
            if (out == null) { addTextLine("=== нет подключения ==="); return@setOnClickListener }
            dispatch("[${now()}] [${me()}] $t"); msgField.setText("")
        }

        col.addView(ipField); col.addView(nameField); col.addView(peerField); col.addView(connectBtn); col.addView(secretBtn)
        col.addView(chatScroll); col.addView(msgField); col.addView(imgBtn); col.addView(sendBtn)
        return col
    }

    private fun pickImage() { startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_IMAGE) }
    private fun pickImages() {
        val i = Intent(Intent.ACTION_GET_CONTENT); i.type = "image/*"; i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(i, "Выбери фото"), PICK_IMAGES)
    }

    private fun dispatch(body: String, b64: String? = null) {
        val payload = if (b64 != null) body + "[IMG:$b64]" else body
        val s = sessions[currentPeer]
        if (currentPeer.isNotEmpty() && s != null) {
            s.sendN++; val (next, mk) = Crypto.step(s.sendCk, s.sendN); s.sendCk = next
            sendLine("@$currentPeer DM ${me()} ${s.sendN} ${Crypto.enc(mk, payload)}")
            saveSession(currentPeer, s)
            if (b64 != null) handler.post { addImageView(b64) } else addTextLine(payload)
        } else sendLine(Crypto.enc(Crypto.groupKey(), payload))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == PICK_IMAGE) {
            val uri = data.data ?: return
            if (out == null) { addTextLine("=== нет подключения ==="); return }
            val b64 = compressUri(uri, 320, 60) ?: return
            dispatch("[${now()}] [${me()}] ", b64)
        } else if (requestCode == PICK_IMAGES) {
            val uris = ArrayList<android.net.Uri>()
            val clip = data.clipData
            if (clip != null) { for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri) }
            else data.data?.let { uris.add(it) }
            if (uris.isEmpty()) return
            for (u in uris) { val b64 = compressUri(u, FEED_MAX_W, FEED_QUALITY); if (b64 != null) pendingImages.add(b64) }
            renderPending()
        }
    }

    private fun sendLine(payload: String) { val o = out ?: return; Thread { try { o.write((payload + "\n").toByteArray()); o.flush() } catch (_: Exception) {} }.start() }
    private fun addTextLine(msg: String) {
        handler.post {
            val tv = TextView(this); tv.text = msg; tv.setPadding(0, 4, 0, 4); chatBox.addView(tv)
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
    private fun addImageView(b64: String) {
        val bmp = decodeB64(b64, 240) ?: return
        val iv = ImageView(this); iv.setImageBitmap(bmp); iv.adjustViewBounds = true
        iv.layoutParams = lp(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT, 8)
        chatBox.addView(iv); chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun addText(chunk: String) { buf.append(chunk); var i = buf.indexOf("\n"); while (i >= 0) { val line = buf.substring(0, i); buf.delete(0, i + 1); render(line); i = buf.indexOf("\n") } }
    private fun render(line: String) {
        if (line.isEmpty()) return
        val parts = line.split(" ")
        when (parts[0]) {
            "DHREQ" -> { if (parts.size < 5) return; val from = parts[1]; if (sessions.containsKey(from)) { addTextLine("🔑 сессия с $from уже есть"); return }
                val p = BigInteger(parts[2], 16); val g = BigInteger(parts[3], 16); val A = BigInteger(parts[4], 16)
                Thread { val b = BigInteger(256, SecureRandom()); val B = g.modPow(b, p); val shared = A.modPow(b, p)
                    val sh = Crypto.makeSession(shared.toString(16)); sessions[from] = sh; saveSession(from, sh)
                    sendLine("@$from DHRES ${me()} ${B.toString(16)}"); addTextLine("🔐 ratchet-сессия с $from, отпечаток: ${Crypto.fingerprint(sh.sendCk)}") }.start() }
            "DHRES" -> { if (parts.size < 3) return; val from = parts[1]; val B = BigInteger(parts[2], 16)
                val a = pendingA[from] ?: return; val p = pendingP[from] ?: return; val shared = B.modPow(a, p)
                val sh = Crypto.makeSession(shared.toString(16)); sessions[from] = sh; saveSession(from, sh)
                pendingA.remove(from); pendingP.remove(from); addTextLine("🔐 ratchet-сессия с $from, отпечаток: ${Crypto.fingerprint(sh.sendCk)}") }
            "DM" -> { if (parts.size < 4) return; val from = parts[1]; val n = parts[2].toLongOrNull() ?: return; val ct = parts[3]
                val s = sessions[from]; if (s == null) { addTextLine("🔒 нет сессии с $from"); return }
                val d = decryptDM(s, n, ct); saveSession(from, s)
                if (d == null) addTextLine("🔒 рассинхрон с $from — /reset $from") else showContent(d) }
            "===" -> addTextLine(line)
            else -> { val d = Crypto.dec(Crypto.groupKey(), line); if (d == null) addTextLine("🔒 группа (len=${line.length})") else showContent(d) }
        }
    }
    private fun decryptDM(s: Session, target: Long, ct: String): String? {
        if (target <= s.recvN) return null; var mk = ByteArray(0)
        while (s.recvN < target) { s.recvN++; val (next, k) = Crypto.step(s.recvCk, s.recvN); s.recvCk = next; if (s.recvN == target) mk = k }
        return Crypto.dec(mk, ct)
    }
    private fun showContent(d: String) {
        val m = Regex("\\[IMG:([^\\]]+)\\]").find(d)
        if (m != null) { val prefix = d.substring(0, m.range.first).trimEnd(); if (prefix.isNotEmpty()) addTextLine(prefix); addImageView(m.groupValues[1]) } else addTextLine(d)
    }
    private fun sessionFile(peer: String) = File(filesDir, "ratchet_$peer.dat")
    private fun saveSession(peer: String, s: Session) { sessionFile(peer).writeText("${Crypto.hex(s.sendCk)} ${Crypto.hex(s.recvCk)} ${s.sendN} ${s.recvN}") }
    private fun loadAllSessions() {
        filesDir.listFiles()?.forEach { f -> val nm = f.name
            if (nm.startsWith("ratchet_") && nm.endsWith(".dat") && nm.length > 12) {
                val peer = nm.substring(8, nm.length - 4); val parts = f.readText().trim().split(" ")
                if (parts.size == 4) sessions[peer] = Session(Crypto.unhex(parts[0]), Crypto.unhex(parts[1]), parts[2].toLong(), parts[3].toLong())
            } }
    }
}
