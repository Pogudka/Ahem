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
    fun enc(key: ByteArray, text: String): String {
        val nonce = ByteArray(12); SecureRandom().nextBytes(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return hex(nonce.plus(c.doFinal(text.toByteArray())))
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

// ================= ЛЕНТА: открытый протокол к порту 8080 =================
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
    fun post(ip: String, id: String, text: String, img: String = "") = ask(ip, "POST|$id|$text|$img")
    fun feed(ip: String, id: String) = askMulti(ip, "FEED|$id")
    fun like(ip: String, pid: String, uid: String) = ask(ip, "LIKE|$pid|$uid")
    fun follow(ip: String, nick: String, uid: String) = ask(ip, "FOLLOW|$nick|$uid")
}

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    // --- каркас вкладок ---
    private lateinit var tabs: Array<View>
    private lateinit var tabBtns: Array<Button>

    // --- лента ---
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

    // --- чат (перенос дословно из проверенной версии) ---
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

    private val PICK_IMAGE = 1001
    private var photoTarget = "chat"   // "chat" | "feed"

    private fun now() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun me() = nameField.text.toString().ifEmpty { "Аноним" }
    private fun host() = ipField.text.toString().ifEmpty { "127.0.0.1" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadAllSessions()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val feedTab = buildFeed()
        val videoTab = buildVideo()
        val chatTab = buildChat()
        val profTab = buildProfile()
        tabs = arrayOf(feedTab, videoTab, chatTab, profTab)
        tabs.forEach { content.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val titles = arrayOf("📰 Лента", "🎬 Видео", "💬 Чаты", "👤 Профиль")
        tabBtns = Array(4) { i ->
            Button(this).apply {
                text = titles[i]
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { showTab(i) }
            }
        }
        tabBtns.forEach { panel.addView(it) }

        root.addView(content); root.addView(panel)
        setContentView(root)
        showTab(0)
    }

    private fun showTab(i: Int) {
        tabs.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
        tabBtns.forEachIndexed { idx, b -> b.setBackgroundColor(if (idx == i) 0xFF222222.toInt() else 0xFFCCCCCC.toInt()) }
        if (i == 3) refreshProfile()
    }

    // ================= ЭКРАН ЛЕНТА =================
    private fun buildFeed(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        feedEmail = EditText(this).apply { hint = "email (логин ленты)" }
        feedNick = EditText(this).apply { hint = "ник" }
        feedPass = EditText(this).apply { hint = "пароль"; inputType = 1 } // textPassword
        val rowAuth = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val regBtn = Button(this).apply { text = "Регистрация"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val logBtn = Button(this).apply { text = "Вход"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        rowAuth.addView(regBtn); rowAuth.addView(logBtn)
        feedStatus = TextView(this).apply { text = "не залогинен" }
        feedPostText = EditText(this).apply { hint = "Новый пост (без символа |)" }
        val rowPost = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pubBtn = Button(this).apply { text = "Опубликовать"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f) }
        val phBtn = Button(this).apply { text = "📷"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        rowPost.addView(pubBtn); rowPost.addView(phBtn)
        val rowAct = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val feedBtn = Button(this).apply { text = "⟳ Лента"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        feedFollowNick = EditText(this).apply { hint = "@ник подписки"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f) }
        val folBtn = Button(this).apply { text = "Подписаться"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        rowAct.addView(feedBtn); rowAct.addView(feedFollowNick); rowAct.addView(folBtn)
        feedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        feedScroll = ScrollView(this).apply { addView(feedBox); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }

        regBtn.setOnClickListener {
            val e = feedEmail.text.toString(); val n = feedNick.text.toString(); val p = feedPass.text.toString()
            if (e.isEmpty() || n.isEmpty() || p.isEmpty()) { feedStatus.text = "заполни все поля"; return@setOnClickListener }
            Thread {
                val r = FeedApi.register(host(), e, n, p)
                handler.post {
                    if (r.startsWith("OK|")) { feedId = r.substring(3); feedLoggedNick = n; feedStatus.text = "залогинен: @$n (id $feedId)" }
                    else feedStatus.text = "ошибка: $r"
                }
            }.start()
        }
        logBtn.setOnClickListener {
            val e = feedEmail.text.toString(); val p = feedPass.text.toString()
            if (e.isEmpty() || p.isEmpty()) { feedStatus.text = "email + пароль"; return@setOnClickListener }
            Thread {
                val r = FeedApi.login(host(), e, p)   // OK|id|nick
                handler.post {
                    if (r.startsWith("OK|")) {
                        val parts = r.split("|"); feedId = parts[1]; feedLoggedNick = parts.getOrElse(2) { "?" }
                        feedStatus.text = "залогинен: @$feedLoggedNick (id $feedId)"
                    } else feedStatus.text = "ошибка: $r"
                }
            }.start()
        }
        pubBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход/регистрация"; return@setOnClickListener }
            val t = feedPostText.text.toString().replace("|", "/")
            Thread { FeedApi.post(host(), feedId, t); handler.post { reloadFeed() } }.start()
            feedPostText.setText("")
        }
        phBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход/регистрация"; return@setOnClickListener }
            photoTarget = "feed"; pickImage()
        }
        feedBtn.setOnClickListener { if (feedId.isEmpty()) feedStatus.text = "сначала вход" else Thread { handler.post { reloadFeed() } }.start() }
        folBtn.setOnClickListener {
            if (feedId.isEmpty()) { feedStatus.text = "сначала вход"; return@setOnClickListener }
            val n = feedFollowNick.text.toString()
            if (n.isEmpty()) return@setOnClickListener
            Thread { FeedApi.follow(host(), n, feedId); handler.post { reloadFeed() } }.start()
            feedFollowNick.setText("")
        }
        listOf(feedEmail, feedNick, feedPass, rowAuth, feedStatus, feedPostText, rowPost, rowAct, feedScroll).forEach { col.addView(it) }
        return col
    }

    private fun reloadFeed() {
        feedBox.removeAllViews()
        if (feedId.isEmpty()) { feedBox.addView(TextView(this).apply { text = "войди в ленту сверху" }); return }
        val lines = FeedApi.feed(host(), feedId)
        for (ln in lines) if (ln.startsWith("P|")) addPostCard(ln)
        if (lines.isEmpty()) feedBox.addView(TextView(this).apply { text = "лента пуста — подпишись или напиши пост" })
        feedScroll.post { feedScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addPostCard(line: String) {
        // P|pid|nick|ts|likes|text|img   (img без '|', text может содержать '|')
        val rest = line.substring(2)
        val li = rest.lastIndexOf('|')
        val img = if (li >= 0) rest.substring(li + 1) else ""
        val head = if (li >= 0) rest.substring(0, li) else rest
        val hp = head.split("|")
        if (hp.size < 5) return
        val pid = hp[0]; val nick = hp[1]; val ts = hp[2]; val likes = hp[3]
        val text = if (hp.size > 4) hp.subList(4, hp.size).joinToString("|") else ""

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        card.addView(TextView(this).apply { text = "[$pid] @$nick  $ts   ♥$likes" })
        if (text.isNotEmpty()) card.addView(TextView(this).apply { this.text = text; setPadding(0, 4, 0, 4) })
        if (img.isNotEmpty()) {
            val bytes = try { Base64.decode(img, Base64.NO_WRAP) } catch (e: Exception) { null }
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) card.addView(ImageView(this).apply {
                setImageBitmap(bmp); adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(dp(220), -2).apply { topMargin = 4 }
            })
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val likeBtn = Button(this).apply { text = "♥"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        likeBtn.setOnClickListener { if (feedId.isEmpty()) return@setOnClickListener else Thread { FeedApi.like(host(), pid, feedId); handler.post { reloadFeed() } }.start() }
        row.addView(likeBtn)
        if (nick != feedLoggedNick) {
            val folBtn = Button(this).apply { text = "+ @$nick"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f) }
            folBtn.setOnClickListener { if (feedId.isEmpty()) return@setOnClickListener else Thread { FeedApi.follow(host(), nick, feedId); handler.post { reloadFeed() } }.start() }
            row.addView(folBtn)
        }
        card.addView(row)
        feedBox.addView(card)
    }

    // ================= ЭКРАН ВИДЕО (заглушка) =================
    private fun buildVideo(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
        addView(TextView(this).apply { text = "🎬 Видео-лента (TikTok-стиль)\n\nКирпич 2: вертикальные клипы свайпом, хранение файлов на сервере по URL, ExoPlayer-плеер.\nТранскод и CDN — после переезда на ПК (для своих телефонов транскод не нужен)." })
    }

    // ================= ЭКРАН ПРОФИЛЬ (заглушка + сводка) =================
    private lateinit var profileText: TextView
    private fun buildProfile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
        profileText = TextView(this)
        addView(profileText)
    }
    private fun refreshProfile() {
        profileText.text =
            "👤 Профиль\n\n" +
            "Лента: @${if (feedLoggedNick.isEmpty()) "—" else feedLoggedNick} (id ${if (feedId.isEmpty()) "—" else feedId})\n" +
            "Чат: ${if (this::nameField.isInitialized) nameField.text else "—"}\n\n" +
            "Кирпич 3: аватар, био, настройки, привязка Google/Яндекс/Mail (OAuth) — после домена haipuch.ru + HTTPS.\n" +
            "Важно: Лента и Чаты сейчас — два отдельных входа. Чтобы выглядеть одним аккаунтом, используй одинаковый ник в обеих вкладках."
    }

    // ================= ЭКРАН ЧАТ (перенос проверенной логики) =================
    private fun buildChat(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        ipField = EditText(this).apply { setText("127.0.0.1"); hint = "IP сервера (общий для лент+чат)" }
        nameField = EditText(this).apply { hint = "Имя (чат)" }
        peerField = EditText(this).apply { hint = "Собеседник (secret)" }
        val connectBtn = Button(this).apply { text = "Подключиться" }
        val secretBtn = Button(this).apply { text = "🔐 Secret chat" }
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatBox); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        msgField = EditText(this).apply { hint = "Сообщение... (/reset ИМЯ)" }
        val imgBtn = Button(this).apply { text = "📷 Фото" }
        val sendBtn = Button(this).apply { text = "Отправить" }

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
        listOf(ipField, nameField, peerField, connectBtn, secretBtn, chatScroll, msgField, imgBtn, sendBtn).forEach { col.addView(it) }
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
    private fun addTextLine(text: String) { handler.post { chatBox.addView(TextView(this).apply { this.text = text; setPadding(0, 4, 0, 4) }); chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) } } }
    private fun addImageView(b64: String) {
        val bytes = Base64.decode(b64, Base64.NO_WRAP); val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        chatBox.addView(ImageView(this).apply { setImageBitmap(bmp); adjustViewBounds = true; layoutParams = LinearLayout.LayoutParams(dp(240), -2).apply { bottomMargin = 8 } })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
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
