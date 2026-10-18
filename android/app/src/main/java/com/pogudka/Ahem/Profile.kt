package com.pogudka.Ahem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

internal fun refreshProfile(act: MainActivity) {
    act.profileGen += 1
    val targetId = if (act.viewingUserId.isNotEmpty()) act.viewingUserId else act.currentUserId
    act.currentProfileTargetId = targetId
    if (targetId.isEmpty()) return
    act.profTarget.value = targetId
    scheduleMigration(act)
    refreshProfileData(act)
}

internal fun openUserProfile(act: MainActivity, userId: String, nick: String) {
    act.viewingUserId = userId
    act.showTab(3, keep = true)
}

private fun refreshProfileData(act: MainActivity) {
    val gen = act.profileGen
    val targetId = act.currentProfileTargetId
    if (targetId.isEmpty()) return
    act.database.child("followers").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { if (gen == act.profileGen) act.profFollowers.value = s.children.count().toLong() }
        override fun onCancelled(e: DatabaseError) {}
    })
    act.database.child("following").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { if (gen == act.profileGen) act.profFollowing.value = s.children.count().toLong() }
        override fun onCancelled(e: DatabaseError) {}
    })
    act.database.child("users").child(targetId).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            if (gen != act.profileGen) return
            val nick = s.child("nick").getValue(String::class.java) ?: "?"
            act.profAvatarB64.value = s.child("avatar").getValue(String::class.java) ?: ""
            act.profNick.value = "@$nick"
            act.profName.value = nick
            act.profBio.value = s.child("bio").getValue(String::class.java) ?: ""
        }
        override fun onCancelled(e: DatabaseError) {
            if (gen != act.profileGen) return
            val fn = if (act.currentNick.isNotEmpty()) act.currentNick else "?"
            act.profNick.value = "@$fn"
            act.profName.value = fn
            act.profAvatarB64.value = act.currentAvatar
        }
    })
}

private fun loadProfileGrid(act: MainActivity, gen: Int, targetId: String, mode: Int, onDone: (List<DataSnapshot>, Long, Long) -> Unit) {
    var psSnap: DataSnapshot? = null
    var postsDone = false
    var statsDone = false
    fun finish() {
        if (gen != act.profileGen) return
        if (!postsDone || !statsDone) return
        var own = 0L
        var likes = 0L
        val grid = ArrayList<DataSnapshot>()
        psSnap?.let { s ->
            for (p in s.children) {
                val isMine = p.child("authorId").getValue(String::class.java) == targetId
                val liked = act.statsMap[p.key ?: ""]?.child("likedBy")?.child(targetId)?.value != null
                if (isMine) { own++; likes += (act.statsMap[p.key ?: ""]?.child("likes")?.value as? Number)?.toLong() ?: 0L }
                if (mode == 0 && isMine) grid.add(p)
                if (mode == 1 && liked) grid.add(p)
            }
        }
        onDone(grid.sortedByDescending { getTs(it) }, own, likes)
    }
    readOnce(act.database.child("posts"), act, 3,
        { s -> psSnap = s; postsDone = true; finish() },
        { psSnap = null; postsDone = true; finish() })
    readStatsInto(act) { statsDone = true; finish() }
}

@Composable
internal fun ProfileScreen(act: MainActivity) {
    val target by act.profTarget
    val isMe = target == act.currentUserId
    var mode by remember { mutableStateOf(0) }
    val grid = remember { mutableStateListOf<DataSnapshot>() }
    var postsCount by remember { mutableStateOf(0L) }
    var likesCount by remember { mutableStateOf(0L) }
    LaunchedEffect(target) { mode = 0 }
    LaunchedEffect(target, mode, act.currentUserId, act.statsVer.value) {
        if (target.isEmpty()) return@LaunchedEffect
        val gen = act.profileGen
        loadProfileGrid(act, gen, target, mode) { g, pc, lc ->
            if (gen == act.profileGen) { grid.clear(); grid.addAll(g); postsCount = pc; likesCount = lc }
        }
    }
    Column(Modifier.fillMaxSize().background(ic(P.bg)).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(act.profNick.value.ifEmpty { "@" }, color = ic(P.text), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isMe) GlassIconButton(R.drawable.ic_settings) { act.dialog.value = "settings" }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp, 0.dp, 18.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(act.profAvatarB64.value, act.profName.value, 84.dp)
            Spacer(Modifier.width(20.dp))
            StatCol(postsCount, "посты", Modifier.weight(1f))
            StatCol(act.profFollowers.value, "подписчики", Modifier.weight(1f), onClick = { act.dialogArg.value = act.currentProfileTargetId; act.dialog.value = "followers" })
            StatCol(act.profFollowing.value, "подписки", Modifier.weight(1f), onClick = { act.dialogArg.value = act.currentProfileTargetId; act.dialog.value = "following" })
            StatCol(likesCount, "лайки", Modifier.weight(1f))
        }
        if (act.profName.value.isNotEmpty()) Text(act.profName.value, color = ic(P.text), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(18.dp, 0.dp, 18.dp, 2.dp))
        if (act.profBio.value.isNotEmpty()) Text(act.profBio.value, color = ic(P.text), fontSize = 14.sp, modifier = Modifier.padding(18.dp, 0.dp, 18.dp, 14.dp))
        Row(Modifier.fillMaxWidth().padding(18.dp, 0.dp, 18.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isMe) OutlineButton("Редактировать профиль", Modifier.weight(1f)) { act.dialog.value = "edit" }
            else OutlineButton("Написать", Modifier.weight(1f)) { openChat(act, act.currentProfileTargetId, act.profName.value) }
            OutlineButton("Поделиться", Modifier.weight(1f)) {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("nick", "@" + act.profName.value))
                android.widget.Toast.makeText(act, "Ник скопирован", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 0.dp, 18.dp, 2.dp)
                .background(ic(P.surface), RoundedCornerShape(14.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            Box(Modifier.weight(1f).height(38.dp).clickable { mode = 0 }, contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_grid), contentDescription = null, tint = if (mode == 0) ic(P.text) else ic(P.navInactive), modifier = Modifier.size(22.dp))
            }
            Box(Modifier.weight(1f).height(38.dp).clickable { mode = 1 }, contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_heart), contentDescription = null, tint = if (mode == 1) ic(P.accent) else ic(P.navInactive), modifier = Modifier.size(22.dp))
            }
        }
        ProfileGrid(act, grid.toList(), mode)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ProfileGrid(act: MainActivity, grid: List<DataSnapshot>, mode: Int) {
    if (grid.isEmpty()) {
        Text(
            if (mode == 0) "Нет постов" else "Нет лайков",
            color = ic(P.textSec), fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(0.dp, 40.dp)
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(18.dp, 0.dp, 18.dp, 0.dp)) {
        var i = 0
        while (i < grid.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 3) {
                    val idx = i + c
                    if (idx < grid.size) GridTile(act, grid[idx], Modifier.weight(1f)) else Spacer(Modifier.weight(1f).height(110.dp))
                }
            }
            i += 3
        }
    }
}

@Composable
private fun GridTile(act: MainActivity, p: DataSnapshot, modifier: Modifier = Modifier) {
    val postId = p.key ?: ""
    val txt = p.child("text").getValue(String::class.java) ?: ""
    val stats = act.statsMap[postId]
    val liked = stats?.child("likedBy")?.child(act.currentUserId)?.value != null
    var media by remember(postId, act.statsVer.value) { mutableStateOf<String?>(null) }
    LaunchedEffect(postId) {
        try { val s = awaitOnce(act.database.child("postMedia").child(postId)); media = s.getValue(String::class.java) ?: "" } catch (t: Throwable) { media = "" }
    }
    val first = (media ?: "").split(",").firstOrNull { it.isNotEmpty() }
    var bmp by remember(first, postId) { mutableStateOf<android.graphics.Bitmap?>(if (first != null) decodeScaled(first, 360) else null) }
    if (bmp == null && first != null) LaunchedEffect(first) { bmp = decodeScaled(first, 360) }
    val shown = bmp
    Box(
        modifier.height(110.dp).clip(RoundedCornerShape(6.dp)).background(ic(P.surface))
            .clickable { act.dialogArg.value = postId; act.dialog.value = "post" }
    ) {
        if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(txt, color = ic(P.text), fontSize = 11.sp, maxLines = 6, modifier = Modifier.padding(6.dp))
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clickable { toggleLike(act, postId) }) {
            Icon(painter = painterResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline), contentDescription = null, tint = if (liked) ic(P.heartOn) else ic(P.textSec), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun DialogHost(act: MainActivity) {
    val d by act.dialog
    when (d) {
        "settings" -> SettingsDialog(act)
        "accounts" -> AccountsDialog(act)
        "edit" -> EditDialog(act)
        "followers" -> FollowListDialog(act, true)
        "following" -> FollowListDialog(act, false)
        "post" -> PostDetailDialog(act, act.dialogArg.value)
        "viewer" -> ViewerDialog(act)
    }
}

@Composable
private fun SettingsDialog(act: MainActivity) {
    val darkNow = isDark(act)
    Dialog(onDismissRequest = { act.dialog.value = null }) {
        Column(
            Modifier.fillMaxWidth().background(ic(P.surface), RoundedCornerShape(20.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(20.dp)).padding(20.dp, 18.dp, 20.dp, 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки", color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GlassIconButton(R.drawable.ic_close) { act.dialog.value = null }
            }
            Spacer(Modifier.height(16.dp))
            Text("Тема оформления", color = ic(P.textSec), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            ThemeOpt("Светлая", !darkNow) { setThemeAndRestart(act, false); act.dialog.value = null }
            Spacer(Modifier.height(8.dp))
            ThemeOpt("Тёмная", darkNow) { setThemeAndRestart(act, true); act.dialog.value = null }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ic(P.border)))
            Spacer(Modifier.height(16.dp))
            Text("Аккаунт", color = ic(P.textSec), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            OutlineButton("Сменить аккаунт", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { act.dialog.value = "accounts" }
            OutlineButton("Выйти из аккаунта", Modifier.fillMaxWidth()) {
                act.dialog.value = null
                act.viewingUserId = ""
                act.auth.signOut()
                act.showTab(0)
            }
        }
    }
}

@Composable
private fun ThemeOpt(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) ic(P.surface) else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, ic(P.border), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (selected) ic(P.accent) else ic(P.border)))
        Spacer(Modifier.width(12.dp))
        Text(label, color = ic(P.text), fontSize = 15.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun AccountsDialog(act: MainActivity) {
    val accounts = remember { loadAccounts(act) }
    Dialog(onDismissRequest = { act.dialog.value = null }) {
        Column(
            Modifier.fillMaxWidth().background(ic(P.surface), RoundedCornerShape(20.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(20.dp)).padding(20.dp, 18.dp, 20.dp, 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(R.drawable.ic_back) { act.dialog.value = "settings" }
                Spacer(Modifier.width(12.dp))
                Text("Аккаунты", color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (accounts.isEmpty()) Text("Нет сохранённых аккаунтов", color = ic(P.textSec), fontSize = 14.sp)
            for (acc in accounts) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .background(if (acc.uid == act.currentUserId) ic(P.surface) else Color.Transparent, RoundedCornerShape(12.dp))
                        .border(1.dp, ic(P.border), RoundedCornerShape(12.dp))
                        .clickable { act.dialog.value = null; switchToAccount(act, acc) }
                        .padding(14.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar("", acc.nick, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(acc.nick, color = ic(P.text), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(acc.email, color = ic(P.textSec), fontSize = 12.sp)
                    }
                    if (acc.uid == act.currentUserId) Text("сейчас", color = ic(P.textSec), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            AccentButton("Добавить аккаунт", Modifier.fillMaxWidth()) { act.dialog.value = null; switchToManual(act) }
        }
    }
}

@Composable
private fun EditDialog(act: MainActivity) {
    val nick = remember { mutableStateOf(act.currentNick) }
    val bio = remember { mutableStateOf(act.profBio.value) }
    Dialog(onDismissRequest = { act.dialog.value = null }) {
        Column(
            Modifier.fillMaxWidth().background(ic(P.surface), RoundedCornerShape(20.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(20.dp)).padding(20.dp, 18.dp, 20.dp, 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(R.drawable.ic_close) { act.dialog.value = null }
                Spacer(Modifier.width(12.dp))
                Text("Редактировать", color = ic(P.text), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Avatar(act.currentAvatar, act.currentNick, 96.dp)
                Spacer(Modifier.height(12.dp))
                OutlineButton("Сменить аватар", Modifier.fillMaxWidth()) {
                    act.startActivityForResult(
                        android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                        act.PICK_AVATAR
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            EditField(nick.value, "Ник (мин. 5 символов)") { nick.value = it }
            Spacer(Modifier.height(10.dp))
            EditField(bio.value, "Био") { bio.value = it }
            Spacer(Modifier.height(14.dp))
            AccentButton("Сохранить", Modifier.fillMaxWidth()) {
                val newNick = nick.value.trim()
                val newBio = bio.value.trim()
                if (newNick.length < 5) { android.widget.Toast.makeText(act, "Ник минимум 5 символов", android.widget.Toast.LENGTH_SHORT).show(); return@AccentButton }
                isNickTaken(act, newNick, act.currentUserId) { taken ->
                    if (taken) { android.widget.Toast.makeText(act, "Ник уже занят", android.widget.Toast.LENGTH_SHORT).show(); return@isNickTaken }
                    if (newNick != act.currentNick) {
                        act.database.child("users").child(act.currentUserId).child("nick").setValue(newNick)
                        act.currentNick = newNick
                        saveAccount(act, act.currentUserId, newNick, act.currentEmail, "")
                        updatePostsNick(act, newNick)
                    }
                    act.database.child("users").child(act.currentUserId).child("bio").setValue(newBio)
                    act.dialog.value = null
                    refreshProfile(act)
                    loadFeed(act)
                }
            }
        }
    }
}

@Composable
private fun EditField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(ic(P.fieldBg), RoundedCornerShape(14.dp))
            .border(1.dp, ic(P.border), RoundedCornerShape(14.dp)).padding(16.dp, 13.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(color = ic(P.text), fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = ic(P.textSec), fontSize = 15.sp); inner() }
        )
    }
}

@Composable
private fun FollowListDialog(act: MainActivity, followers: Boolean) {
    val target = act.dialogArg.value
    val items = remember { mutableStateListOf<Pair<String, String>>() }
    val title = remember { mutableStateOf(if (followers) "Подписчики" else "Подписки") }
    LaunchedEffect(target, followers) {
        items.clear()
        val node = if (followers) "followers" else "following"
        act.database.child(node).child(target).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                items.clear()
                title.value = (if (followers) "Подписчики " else "Подписки ") + s.children.count()
                for (c in s.children) {
                    val uid = c.key ?: continue
                    val nick = c.child("nick").getValue(String::class.java) ?: "?"
                    items.add(Pair(uid, nick))
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }
    Dialog(onDismissRequest = { act.dialog.value = null }) {
        Column(Modifier.fillMaxSize().background(ic(P.bg))) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(R.drawable.ic_back) { act.dialog.value = null }
                Spacer(Modifier.width(12.dp))
                Text(title.value, color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) item { Text("Пусто", color = ic(P.textSec), fontSize = 14.sp, modifier = Modifier.padding(4.dp, 20.dp)) }
                items(items) { (uid, nick) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(ic(P.surface), RoundedCornerShape(14.dp))
                            .border(1.dp, ic(P.border), RoundedCornerShape(14.dp))
                            .clickable { act.dialog.value = null; openUserProfile(act, uid, nick) }
                            .padding(12.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AuthorAvatar(uid, nick, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("@$nick", color = ic(P.text), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

internal fun switchToAccount(act: MainActivity, acc: Acc) {
    act.viewingUserId = ""
    act.currentUserId = ""; act.currentNick = ""; act.currentEmail = ""; act.currentAvatar = ""
    act.myFollowingIds.clear()
    act.showTab(0)
    act.authScreen.value = true
    act.isLoggingIn = true
    act.auth.signInWithEmailAndPassword(acc.email, acc.pass).addOnCompleteListener { task ->
        act.isLoggingIn = false
        if (task.isSuccessful) {
            val uid = act.auth.currentUser?.uid ?: ""
            act.currentUserId = uid; act.currentEmail = acc.email; act.viewingUserId = uid
            enterFeedMode(act)
            loadMyFollowing(act) {}
            act.database.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    act.currentNick = s.child("nick").getValue(String::class.java) ?: acc.nick
                    act.currentAvatar = s.child("avatar").getValue(String::class.java) ?: ""
                    act.saveSessionPrefs()
                    refreshProfile(act)
                }
                override fun onCancelled(e: DatabaseError) { dbDenied(act, e) }
            })
        } else {
            act.emailInput.value = acc.email
            act.feedStatus.value = if (isAuthNetworkError(task.exception)) authErrorText(task.exception) else "Введите пароль для ${acc.email}"
        }
    }
}

internal fun switchToManual(act: MainActivity) {
    act.viewingUserId = ""
    act.currentUserId = ""; act.currentNick = ""; act.currentEmail = ""; act.currentAvatar = ""
    act.myFollowingIds.clear()
    act.showTab(0)
    act.authScreen.value = true
    act.feedStatus.value = "вход / регистрация"
    act.emailInput.value = ""; act.nickInput.value = ""; act.passInput.value = ""
}

internal fun setThemeAndRestart(act: MainActivity, dark: Boolean) {
    act.getSharedPreferences("ahem", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
    act.recreate()
}
