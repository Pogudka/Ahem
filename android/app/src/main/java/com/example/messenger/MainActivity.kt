package com.example.messenger

import android.app.Activity
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
        val s = Socket(ip, FEED_PORT)
        try {
            s.getOutputStream().write((req + "\n").toByteArray()); s.getOutputStream().flush()
            val acc = StringBuilder(); return readLine(acc, s.getInputStream()) ?: "ERR|eof"
        } finally { try { s.close() } catch (_: Exception) {} }
    }
    private fun askMulti(ip: String, req: String): List<String> {
        val s = Socket(ip, FEED_PORT)
        val out = ArrayList<String>()
        try {
            s.getOutputStream().write((req + "\n").toByteArray()); s.getOutputStream().flush()
            val acc = StringBuilder()
            while (true) { val l = readLine(acc, s.getInputStream()) ?: break; if (l == "END") break; out.add(l) }
        } finally { try { s.close() } catch (_: Exception) {} }
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

    private lateinit var tabs: Array<View>
    private lateinit var tabBtns: Array<Button>

    // лента
    private lateinit var feedEmail: EditText
    private lateinit var feedNick: EditText
    private lateinit var feedPass: EditText
    private lateinit var feedStatus: TextView
    private lateinit var feedPostText: EditText
    private lateinit var feedFollowNick: EditText
    private lateinit var feedBox: LinearLayout
    private lateinit var feedScroll: ScrollView
    private var feedId = ""
    private var feedLoggedNick = ""

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
    private val PICK_IMAGE = 1001
    private var photoTarget = "chat"

    private fun now() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun me() = nameField.text.toString().ifEmpty { "Аноним" }
    private fun host() = ipField.text.toString().ifEmpty { "127.0.0.1" }
    private fun lp(w: Int, h: Int, margin: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h); p.bottomMargin = margin; return p
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadAllSessions()

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL
        val content = FrameLayout(this); content.layoutParams = lp(-1, 0, 0)
        (content.layoutParams as LinearLayout.LayoutParams).weight = 1f
        val panel = LinearLayout(this); panel.orientation = LinearLayout.HORIZONTAL

        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChat()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        for (t in tabs) content.addView(t, FrameLayout.LayoutParams(-1, -1))

        val titles = arrayOf("📰 Лента", "🎬 Видео", "💬 Чаты", "👤 Профиль")
        tabBtns = Array(4) { i ->
            val b = Button(this)
            b.text = titles[i]
            val blp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            b.layoutParams = blp
            b.setOnClickListener { showTab(i) }
            b
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

    // ================= ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)

        feedEmail = EditText(this); feedEmail.hint = "email (логин ленты)"
        feedNick = EditText(this); feedNick.hint = "ник"
        feedPass = EditText(this); feedPass.hint = "пароль"; feedPass.inputType = 1
        val rowAuth = LinearLayout(this); rowAuth.orientation = LinearLayout.HORIZONTAL
        val regBtn = Button(this); regBtn.text = "Регистрация"; regBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (regBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        val logBtn = Button(this); logBtn.text = "Вход"; logBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (logBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        rowAuth.addView(regBtn); rowAuth.addView(logBtn)
        feedStatus = TextView(this); feedStatus.text = "не залогинен"
        feedPostText = EditText(this); feedPostText.hint = "Новый пост (без символа |)"
        val rowPost = LinearLayout(this); rowPost.orientation = LinearLayout.HORIZONTAL
        val pubBtn = Button(this); pubBtn.text = "Опубликовать"; pubBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (pubBtn.layoutParams as LinearLayout.LayoutParams).weight = 2f
        val phBtn = Button(this); phBtn.text = "📷"; phBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (phBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        rowPost.addView(pubBtn); rowPost.addView(phBtn)
        val rowAct = LinearLayout(this); rowAct.orientation = LinearLayout.HORIZONTAL
        val feedBtn = Button(this); feedBtn.text = "⟳ Лента"; feedBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (feedBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        feedFollowNick = EditText(this); feedFollowNick.hint = "@ник подписки"; feedFollowNick.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (feedFollowNick.layoutParams as LinearLayout.LayoutParams).weight = 2f
        val folBtn = Button(this); folBtn.text = "Подписаться"; folBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (folBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        rowAct.addView(feedBtn); rowAct.addView(feedFollowNick); rowAct.addView(folBtn)
        feedBox = LinearLayout(this); feedBox.orientation = LinearLayout.VERTICAL
        feedScroll = ScrollView(this); feedScroll.addView(feedBox)
        feedScroll.layoutParams = lp(-1, 0, 0); (feedScroll.layoutParams as LinearLayout.LayoutParams).weight = 1f

        regBtn.setOnClickListener {
            val e = feedEmail.text.toString(); val n = feedNick.text.toString(); val pw = feedPass.text.toString()
            if (e.isEmpty() || n.isEmpty() || pw.isEmpty()) { feedStatus.text = "заполни все поля"; return@setOnClickListener }
            Thread {
                val r = FeedApi.register(host(), e, n, pw)
                handler.post {
                    if (r.startsWith("OK|")) { feedId = r.substring(3); feedLoggedNick = n; feedStatus.text = "залогинен: @$n (id $feedId)" }
                    else feedStatus.text = "ошибка: $r"
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
                        feedStatus.text = "залогинен: @$feedLoggedNick (id $feedId)"
                    } else feedStatus.text = "ошибка: $r"
                }
            }.start()
        }
        pubBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход/регистрация"; return@setOnClickListener }
            val body = feedPostText.text.toString().replace("|", "/")
            Thread { FeedApi.post(host(), feedId, body); handler.post { reloadFeed() } }.start()
            feedPostText.setText("")
        }
        phBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход/регистрация"; return@setOnClickListener }
            photoTarget = "feed"; pickImage()
        }
        feedBtn.setOnClickListener {
            if (feedId.isEmpty()) feedStatus.text = "сначала вход" else Thread { handler.post { reloadFeed() } }.start()
        }
        folBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход"; return@setOnClickListener }
            val n = feedFollowNick.text.toString()
            if (n.isEmpty()) return@setOnClickListener
            Thread { FeedApi.follow(host(), n, feedId); handler.post { reloadFeed() } }.start()
            feedFollowNick.setText("")
        }

        col.addView(feedEmail); col.addView(feedNick); col.addView(feedPass); col.addView(rowAuth)
        col.addView(feedStatus); col.addView(feedPostText); col.addView(rowPost); col.addView(rowAct); col.addView(feedScroll)
        return col
    }

    private fun reloadFeed() {
        feedBox.removeAllViews()
        if (feedId.isEmpty()) { val tv = TextView(this); tv.text = "войди в ленту сверху"; feedBox.addView(tv); return }
        val lines = FeedApi.feed(host(), feedId)
        for (ln in lines) if (ln.startsWith("P|")) addPostCard(ln)
        if (lines.isEmpty()) { val tv = TextView(this); tv.text = "лента пуста — подпишись или напиши пост"; feedBox.addView(tv) }
        feedScroll.post { feedScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addPostCard(line: String) {
        val rest = line.substring(2)
        val li = rest.lastIndexOf('|')
        val postImg = if (li >= 0) rest.substring(li + 1) else ""
        val head = if (li >= 0) rest.substring(0, li) else rest
        val hp = head.split("|")
        if (hp.size < 5) return
        val postId = hp[0]; val postNick = hp[1]; val postTs = hp[2]; val postLikes = hp[3]
        val postBody = if (hp.size > 4) hp.subList(4, hp.size).joinToString("|") else ""

        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setPadding(8, 8, 8, 8)
        card.layoutParams = lp(-1, LinearLayout.LayoutParams.WRAP_CONTENT, 8); card.setBackgroundColor(0xFFEEEEEE.toInt())

        val title = TextView(this); title.text = "[$postId] @$postNick  $postTs   ♥$postLikes"; card.addView(title)
        if (postBody.isNotEmpty()) { val bt = TextView(this); bt.text = postBody; bt.setPadding(0, 4, 0, 4); card.addView(bt) }
        if (postImg.isNotEmpty()) {
            val bytes = try { Base64.decode(postImg, Base64.NO_WRAP) } catch (e: Exception) { null }
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) {
                val iv = ImageView(this); iv.setImageBitmap(bmp); iv.adjustViewBounds = true
                iv.layoutParams = lp(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT, 4); card.addView(iv)
            }
        }
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        val likeBtn = Button(this); likeBtn.text = "♥"; likeBtn.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
        (likeBtn.layoutParams as LinearLayout.LayoutParams).weight = 1f
        likeBtn.setOnClickListener { if (feedId.isEmpty()) return@setOnClickListener else Thread { FeedApi.like(host(), postId, feedId); handler.post { reloadFeed() } }.start() }
        row.addView(likeBtn)
        if (postNick != feedLoggedNick) {
            val fb = Button(this); fb.text = "+ @$postNick"; fb.layoutParams = lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0)
            (fb.layoutParams as LinearLayout.LayoutParams).weight = 2f
            fb.setOnClickListener { if (feedId.isEmpty()) return@setOnClickListener else Thread { FeedApi.follow(host(), postNick, feedId); handler.post { reloadFeed() } }.start() }
            row.addView(fb)
        }
        card.addView(row)
        feedBox.addView(card)
    }

    // ================= ВИДЕО =================
    private fun buildVideo(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        val tv = TextView(this)
        tv.text = "🎬 Видео-лента (TikTok-стиль)\n\nКирпич 2: вертикальные клипы свайпом, хранение файлов на сервере по URL, ExoPlayer-плеер.\nТранскод и CDN — после переезда на ПК (для своих телефонов транскод не нужен)."
        col.addView(tv)
        return col
    }

    // ================= ПРОФИЛЬ =================
    private fun buildProfile(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        profileText = TextView(this); col.addView(profileText)
        return col
    }
    private fun refreshProfile() {
        val chatName = if (::nameField.isInitialized) nameField.text.toString() else "—"
        profileText.text =
            "👤 Профиль\n\n" +
            "Лента: @${if (feedLoggedNick.isEmpty()) "—" else feedLoggedNick} (id ${if (feedId.isEmpty()) "—" else feedId})\n" +
            "Чат: ${if (chatName.isEmpty()) "—" else chatName}\n\n" +
            "Кирпич 3: аватар, био, настройки, привязка Google/Яндекс/Mail (OAuth) — после домена haipuch.ru + HTTPS.\n" +
            "Важно: Лента и Чаты сейчас — два отдельных входа. Чтобы выглядеть одним аккаунтом, используй одинаковый ник в обеих вкладках."
    }

    // ================= ЧАТ =================
    private fun buildChat(): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(16, 16, 16, 16)
        ipField = EditText(this); ipField.setText("127.0.0.1"); ipField.hint = "IP сервера (общий для лент+чат)"
        nameField = EditText(this); nameField.hint = "Имя (чат)"
        peerField = EditText(this); peerField.hint = "Собеседник (secret)"
        val connectBtn = Button(this); connectBtn.text = "Подключиться"
        val secretBtn = Button(this); secretBtn.text = "🔐 Secret chat"
        chatBox = LinearLayout(this); chatBox.orientation = LinearLayout.VERTICAL
        chatScroll = ScrollView(this); chatScroll.addView(chatBox)
        chatScroll.layoutParams = lp(-1, 0, 0); (chatScroll.layoutParams as LinearLayout.LayoutParams).weight = 1f
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
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data?.data != null) {
            try {
                val bmp = MediaStore.Images.Media.getBitmap(contentResolver, data.data!!)
                val maxW = 320
                val scaled = if (bmp.width > maxW) Bitmap.createScaledBitmap(bmp, maxW, maxW * bmp.height / bmp.width, true) else bmp
                val baos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (photoTarget == "feed") {
                    if (feedId.isEmpty()) { feedStatus.text = "сначала вход в ленту"; return }
                    Thread { FeedApi.post(host(), feedId, "", b64); handler.post { reloadFeed() } }.start()
                } else {
                    if (out == null) { addTextLine("=== нет подключения ==="); return }
                    dispatch("[${now()}] [${me()}] ", b64)
                }
            } catch (e: Exception) { if (photoTarget == "feed") feedStatus.text = "ошибка фото" else addTextLine("=== ошибка фото ===") }
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
        val bytes = Base64.decode(b64, Base64.NO_WRAP); val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
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
