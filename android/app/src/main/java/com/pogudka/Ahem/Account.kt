package com.pogudka.Ahem

import android.widget.Toast
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

internal data class Acc(
    val uid: String,
    val nick: String,
    val email: String,
    val pass: String
)

internal fun authErrorCode(e: Exception?): String {
    if (e is FirebaseAuthException) return e.errorCode ?: ""
    return ""
}

internal fun isAuthNetworkError(e: Exception?): Boolean {
    if (e == null) return false
    if (authErrorCode(e) == "ERROR_NETWORK_REQUEST_FAILED") return true
    val low = (e.message ?: "").lowercase()
    return low.contains("json conversion failed") ||
        low.contains("<!doctype") ||
        low.contains("403") ||
        low.contains("forbidden") ||
        low.contains("internal error has occurred") ||
        low.contains("network") ||
        low.contains("timeout") ||
        low.contains("unavailable") ||
        low.contains("connection")
}

internal fun authErrorText(e: Exception?): String {
    if (e == null) return "Неизвестная ошибка входа"
    val code = authErrorCode(e)
    val msg = e.message ?: ""
    val low = msg.lowercase()
    if (code == "ERROR_NETWORK_REQUEST_FAILED" ||
        low.contains("json conversion failed") ||
        low.contains("<!doctype") ||
        low.contains("403") ||
        low.contains("forbidden") ||
        low.contains("internal error has occurred")) {
        return "Серверы Google недоступны из этой сети (блокировка/403). Включи VPN или смени сеть и повтори."
    }
    if (code == "ERROR_WEAK_PASSWORD") return "Пароль слишком простой (минимум 6 символов)."
    if (code == "ERROR_INVALID_CREDENTIAL" || code == "ERROR_WRONG_PASSWORD" || code == "ERROR_USER_NOT_FOUND" || code == "ERROR_INVALID_EMAIL") {
        return "Неверный email или пароль."
    }
    if (code == "ERROR_EMAIL_ALREADY_IN_USE" || code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") {
        return "Этот email уже зарегистрирован."
    }
    if (code == "ERROR_TOO_MANY_REQUESTS" || low.contains("too many requests") || low.contains("quota") || low.contains("rate limit")) {
        return "Слишком много попыток. Подожди минуту и повтори."
    }
    if (code == "ERROR_OPERATION_NOT_ALLOWED") {
        return "Вход по email/паролю отключён в консоли Firebase."
    }
    if (low.contains("network") || low.contains("timeout") || low.contains("unavailable") || low.contains("connection")) {
        return "Проблема с сетью до Google. Включи VPN или смени сеть."
    }
    return "Ошибка входа: ${e.javaClass.simpleName}"
}

internal fun saveAccount(act: MainActivity, uid: String, nick: String, email: String, pass: String) {
    if (uid.isEmpty()) return
    val prefs = act.getSharedPreferences("ahem", android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString("accounts", "") ?: ""
    val list = ArrayList<Acc>()
    for (rec in raw.split("\u0002")) {
        if (rec.isEmpty()) continue
        val parts = rec.split("\u0001")
        if (parts.size == 4) {
            list.add(Acc(parts[0], parts[1], parts[2], parts[3]))
        }
    }
    val filtered = ArrayList<Acc>()
    for (acc in list) {
        if (acc.uid != uid) filtered.add(acc)
    }
    filtered.add(Acc(uid, nick, email, pass))
    val out = filtered.joinToString("\u0002") {
        "${it.uid}\u0001${it.nick}\u0001${it.email}\u0001${it.pass}"
    }
    prefs.edit().putString("accounts", out).apply()
}

internal fun loadAccounts(act: MainActivity): List<Acc> {
    val prefs = act.getSharedPreferences("ahem", android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString("accounts", "") ?: ""
    val list = ArrayList<Acc>()
    for (rec in raw.split("\u0002")) {
        if (rec.isEmpty()) continue
        val parts = rec.split("\u0001")
        if (parts.size == 4) list.add(Acc(parts[0], parts[1], parts[2], parts[3]))
    }
    return list
}

internal fun loadMyFollowing(act: MainActivity, onDone: (() -> Unit)?) {
    if (act.currentUserId.isEmpty()) {
        onDone?.invoke()
        return
    }
    act.database.child("following").child(act.currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            act.myFollowingIds.clear()
            for (c in snapshot.children) c.key?.let { act.myFollowingIds.add(it) }
            onDone?.invoke()
        }
        override fun onCancelled(error: DatabaseError) { onDone?.invoke() }
    })
}

internal fun followUser(act: MainActivity, targetUid: String, targetNick: String) {
    if (targetUid.isEmpty() || targetUid == act.currentUserId) return
    if (targetNick.equals(act.currentNick, ignoreCase = true)) return
    val now = System.currentTimeMillis()
    val f = act.database.child("following").child(act.currentUserId).child(targetUid)
    f.child("nick").setValue(targetNick)
    f.child("ts").setValue(now)
    val fr = act.database.child("followers").child(targetUid).child(act.currentUserId)
    fr.child("nick").setValue(act.currentNick)
    fr.child("ts").setValue(now)
    act.myFollowingIds.add(targetUid)
    Toast.makeText(act, "Подписаны на $targetNick", Toast.LENGTH_SHORT).show()
}

internal fun followByNick(act: MainActivity, nickRaw: String) {
    val n = nickRaw.trim().removePrefix("@")
    if (n.isEmpty()) return
    if (n.equals(act.currentNick, ignoreCase = true)) {
        Toast.makeText(act, "Нельзя подписаться на себя", Toast.LENGTH_SHORT).show()
        return
    }
    act.database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            var foundUid = ""
            for (u in snapshot.children) {
                val un = u.child("nick").getValue(String::class.java) ?: ""
                if (un.equals(n, ignoreCase = true)) {
                    foundUid = u.key ?: ""
                    break
                }
            }
            if (foundUid.isEmpty()) {
                Toast.makeText(act, "Пользователь не найден", Toast.LENGTH_SHORT).show()
            } else {
                followUser(act, foundUid, n)
                loadFeed(act)
            }
        }
        override fun onCancelled(error: DatabaseError) {}
    })
}

internal fun updatePostsNick(act: MainActivity, newNick: String) {
    act.database.child("posts").orderByChild("authorId").equalTo(act.currentUserId)
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (p in snapshot.children) p.ref.child("authorNick").setValue(newNick)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
}

internal fun isNickTaken(act: MainActivity, nick: String, excludeUid: String, callback: (Boolean) -> Unit) {
    act.database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
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
