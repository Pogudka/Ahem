package com.pogudka.Ahem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun loadFeed(act: MainActivity, onDone: (() -> Unit)? = null) {
    act.feedGen += 1
    val gen = act.feedGen
    var postsSnap: DataSnapshot? = null
    var postsDone = false
    var statsDone = false
    fun finish() {
        if (gen != act.feedGen) return
        if (!postsDone || !statsDone) return
        act.postsRaw.clear()
        postsSnap?.let { s -> for (c in s.children) act.postsRaw.add(c) }
        act.statsVer.value++
        onDone?.invoke()
    }
    readOnce(act.database.child("posts"), act, 3,
        { s -> postsSnap = s; postsDone = true; finish() },
        { postsSnap = null; postsDone = true; finish() })
    readStatsInto(act) { statsDone = true; finish() }
}

internal fun enterFeedMode(act: MainActivity) {
    loadFeed(act)
    scheduleMigration(act)
}

internal fun toggleLike(act: MainActivity, postId: String) {
    if (act.currentUserId.isEmpty()) return
    val stats = act.statsMap[postId]
    val serverLiked = stats?.child("likedBy")?.child(act.currentUserId)?.value != null
    val serverCount = (stats?.child("likes")?.value as? Number)?.toLong() ?: 0L
    val curLiked = act.optLiked[postId] ?: serverLiked
    val curCount = act.optCount[postId] ?: serverCount
    val newLiked = !curLiked
    val newCount = (curCount + if (newLiked) 1 else -1).coerceAtLeast(0L)
    act.optLiked[postId] = newLiked; act.optCount[postId] = newCount; act.statsVer.value++
    txWithRetry(act.database.child("postStats").child(postId), act, 3, { md ->
        val likes = (md.child("likes").value as? Number)?.toLong() ?: 0L
        val lb = md.child("likedBy")
        if (lb.child(act.currentUserId).value != null) {
            lb.child(act.currentUserId).value = null
            md.child("likes").value = maxOf(0L, likes - 1)
        } else {
            lb.child(act.currentUserId).value = true
            md.child("likes").value = likes + 1
        }
        Transaction.success(md)
    }) { committed, data, _ ->
        if (committed && data != null) {
            act.statsMap[postId] = data
            act.optLiked.remove(postId); act.optCount.remove(postId)
            act.statsVer.value++
        }
    }
}

internal fun incViews(act: MainActivity, postId: String) {
    if (act.currentUserId.isEmpty() || postId in act.viewedLocally) return
    val stats = act.statsMap[postId]
    val serverViews = (stats?.child("views")?.value as? Number)?.toLong() ?: 0L
    val curViews = act.optViews[postId] ?: serverViews
    act.optViews[postId] = curViews + 1; act.statsVer.value++
    txWithRetry(act.database.child("postStats").child(postId), act, 3, { md ->
        val views = (md.child("views").value as? Number)?.toLong() ?: 0L
        val vb = md.child("viewedBy")
        if (vb.child(act.currentUserId).value != null) { act.viewedLocally.add(postId); return@txWithRetry Transaction.success(md) }
        vb.child(act.currentUserId).value = true
        md.child("views").value = views + 1
        Transaction.success(md)
    }) { committed, data, _ ->
        if (committed && data != null) {
            act.viewedLocally.add(postId); act.statsMap[postId] = data
            act.optViews.remove(postId); act.statsVer.value++
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun FeedScreen(act: MainActivity) {
    val me by act.sessionUid
    val query by act.feedQuery
    val listState = rememberLazyListState()
    val pr = rememberPullRefreshState(
        refreshing = act.refreshing.value,
        onRefresh = { act.refreshing.value = true; loadFeed(act) { act.refreshing.value = false } },
        refreshThreshold = 150.dp
    )
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset / 600) {
        delay(220)
        act.feedScrollTick.value++
    }
    Column(Modifier.fillMaxSize().background(ic(P.bg))) {
        if (me.isEmpty()) {
            AuthForm(act)
        } else {
            Row(Modifier.fillMaxWidth().padding(18.dp, 14.dp, 18.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ahem", color = ic(P.text), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = fontUnbounded)
            }
            Composer(act)
            SearchRow(act)
            val sorted = act.postsRaw.toList()
                .sortedByDescending { getTs(it) }
                .filter { p ->
                    if (query.isEmpty()) true
                    else {
                        val q = query.lowercase().removePrefix("@")
                        val t = (p.child("text").getValue(String::class.java) ?: "").lowercase()
                        val n = (p.child("authorNick").getValue(String::class.java) ?: "").lowercase()
                        t.contains(q) || n.contains(q)
                    }
                }
                .take(act.FEED_LIMIT)
            Box(Modifier.fillMaxSize().pullRefresh(pr)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sorted.isEmpty()) {
                        item {
                            Text(
                                if (query.isEmpty()) "Лента пуста" else "Ничего не найдено",
                                color = ic(P.textSec), fontSize = 15.sp,
                                modifier = Modifier.padding(0.dp, 20.dp)
                            )
                        }
                    }
                    items(sorted, key = { it.key ?: "" }) { p -> PostCard(act, p) }
                }
                RefreshIndicator(pr, act.refreshing.value, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun RefreshIndicator(pr: PullRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val dens = LocalDensity.current
    val prog = pr.progress.coerceIn(0f, 1f)
    val top = with(dens) { (prog * 56f).toDp() }
    Box(modifier.padding(top = top + 8.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = null,
            tint = ic(P.textSec),
            modifier = Modifier.size((20f + prog * 12f).dp).scale(if (refreshing) 1f else 0.6f + prog * 0.4f)
        )
    }
}

@Composable
private fun SearchRow(act: MainActivity) {
    val q by act.feedQuery
    Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f)
                .background(ic(P.fieldBg), RoundedCornerShape(14.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(14.dp))
                .padding(16.dp, 13.dp)
        ) {
            BasicTextField(
                value = q, onValueChange = { act.feedQuery.value = it }, singleLine = true,
                textStyle = TextStyle(color = ic(P.text), fontSize = 15.sp, fontFamily = fontUbuntuReg),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (q.isEmpty()) Text("Поиск: текст или @ник", color = ic(P.textSec), fontSize = 15.sp); inner() }
            )
        }
        if (q.isNotEmpty()) GlassIconButton(R.drawable.ic_close, Modifier.padding(start = 8.dp)) { act.feedQuery.value = "" }
    }
}

@Composable
private fun Composer(act: MainActivity) {
    val txt = remember { mutableStateOf("") }
    CardSurface(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp), 18, true) {
        Box(
            Modifier.fillMaxWidth().padding(14.dp, 14.dp, 14.dp, 10.dp)
                .background(ic(P.fieldBg), RoundedCornerShape(14.dp))
                .border(1.dp, ic(P.border), RoundedCornerShape(14.dp))
                .padding(16.dp, 13.dp)
        ) {
            BasicTextField(
                value = txt.value, onValueChange = { txt.value = it },
                textStyle = TextStyle(color = ic(P.text), fontSize = 15.sp, fontFamily = fontUbuntuReg),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (txt.value.isEmpty()) Text("Подпись к посту (до 4 фото)", color = ic(P.textSec), fontSize = 15.sp); inner() }
            )
        }
        if (act.pendingImages.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (im in act.pendingImages.toList()) Thumb(im, 84)
            }
        }
        Row(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ic_camera) { pickImages(act) }
            Spacer(Modifier.width(10.dp))
            AccentButton("Опубликовать", Modifier.weight(1f)) {
                val text = txt.value
                if (text.isEmpty() && act.pendingImages.isEmpty()) return@AccentButton
                val postId = act.database.child("posts").push().key ?: return@AccentButton
                val post = HashMap<String, Any>()
                post["authorId"] = act.currentUserId
                post["authorNick"] = act.currentNick
                post["text"] = text
                post["timestamp"] = System.currentTimeMillis()
                act.database.child("posts").child(postId).setValue(post)
                if (act.pendingImages.isNotEmpty()) {
                    val joined = act.pendingImages.joinToString(",")
                    act.mediaCache[postId] = joined
                    act.database.child("postMedia").child(postId).setValue(joined)
                }
                val stats = HashMap<String, Any>()
                stats["likes"] = 0; stats["views"] = 0
                stats["likedBy"] = HashMap<String, Boolean>(); stats["viewedBy"] = HashMap<String, Boolean>()
                act.database.child("postStats").child(postId).setValue(stats)
                txt.value = ""
                act.pendingImages.clear()
                loadFeed(act)
            }
        }
    }
}

@Composable
private fun Thumb(b64: String, dpSize: Int) {
    val ctx = LocalContext.current
    val px = (dpSize * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(2)
    val shown = rememberDecoded(b64, px)
    Box(
        Modifier.size(dpSize.dp).clip(RoundedCornerShape(12.dp))
            .background(ic(P.surface)).border(1.dp, ic(P.border), RoundedCornerShape(12.dp))
    ) {
        if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun PostCard(act: MainActivity, post: DataSnapshot) {
    val postId = post.key ?: ""
    val authorId = post.child("authorId").getValue(String::class.java) ?: ""
    val authorNick = post.child("authorNick").getValue(String::class.java) ?: "?"
    val text = post.child("text").getValue(String::class.java) ?: ""
    val ts = getTs(post)
    val ver by act.statsVer
    val stats = remember(postId, ver) { act.statsMap[postId] }
    val serverLiked = stats?.child("likedBy")?.child(act.currentUserId)?.value != null
    val serverCount = (stats?.child("likes")?.value as? Number)?.toLong() ?: 0L
    val serverViews = (stats?.child("views")?.value as? Number)?.toLong() ?: 0L
    val liked = act.optLiked[postId] ?: serverLiked
    val likeCount = act.optCount[postId] ?: serverCount
    val viewCount = act.optViews[postId] ?: serverViews
    val timeStr = remember(ts) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)) }
    CardSurface(Modifier.fillMaxWidth(), 18, true) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { openUserProfile(act, authorId, authorNick) }) { AuthorAvatar(authorId, authorNick, 40.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { openUserProfile(act, authorId, authorNick) }) {
                Text(authorNick, color = ic(P.text), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(timeStr, color = ic(P.textSec), fontSize = 12.sp)
            }
        }
        if (text.isNotEmpty()) LinkText(text, color = ic(P.text), fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp))
        MediaBlock(act, postId)
        Row(Modifier.fillMaxWidth().padding(14.dp, 4.dp, 14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clickable { toggleLike(act, postId) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline), contentDescription = null, tint = if (liked) ic(P.heartOn) else ic(P.textSec), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(fmtNum(likeCount), color = ic(P.text), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(16.dp))
            Icon(painter = painterResource(R.drawable.ic_eye), contentDescription = null, tint = ic(P.textSec), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(fmtNum(viewCount), color = ic(P.textSec), fontSize = 13.sp)
            if (authorId != act.currentUserId && authorId.isNotEmpty() && authorId !in act.myFollowingIds) {
                Spacer(Modifier.weight(1f))
                OutlineButton("+ @$authorNick") { followUser(act, authorId, authorNick) }
            }
        }
    }
}

@Composable
private fun MediaBlock(act: MainActivity, postId: String) {
    var str by remember(postId) { mutableStateOf<String?>(act.mediaCache[postId]) }
    LaunchedEffect(postId) {
        if (act.mediaCache.containsKey(postId)) { str = act.mediaCache[postId]; return@LaunchedEffect }
        try {
            val s = awaitOnce(act.database.child("postMedia").child(postId))
            val v = s.getValue(String::class.java) ?: ""
            act.mediaCache[postId] = v
            str = v
        } catch (t: Throwable) { str = "" }
    }
    val images = (str ?: "").split(",").filter { it.isNotEmpty() }
    if (images.isEmpty()) return
    val ctx = LocalContext.current
    val imgW = ctx.resources.displayMetrics.widthPixels - act.dp(28)
    if (images.size == 1) SingleImage(act, images[0], imgW, images, 0) else GridImages(act, images, imgW)
}

@Composable
private fun SingleImage(act: MainActivity, b64: String, px: Int, all: List<String>, start: Int) {
    val shown = rememberDecoded(b64, px) ?: return
    val hDp = (px / act.resources.displayMetrics.density)
    Box(
        Modifier.fillMaxWidth().height(hDp.dp).padding(14.dp, 0.dp, 14.dp, 12.dp)
            .clip(RoundedCornerShape(14.dp)).background(ic(P.surface))
            .clickable { openViewer(act, all, start) }
    ) {
        Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun GridImages(act: MainActivity, images: List<String>, imgW: Int) {
    val cell = (imgW - act.dp(4)) / 2
    val cellDp = (cell / act.resources.displayMetrics.density)
    Column(Modifier.fillMaxWidth().padding(14.dp, 0.dp, 14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < images.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 2) {
                    val idx = i + c
                    if (idx < images.size) GridCell(act, images[idx], cell, cellDp, images, idx, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f).height(cellDp.dp))
                }
            }
            i += 2
        }
    }
}

@Composable
private fun GridCell(act: MainActivity, b64: String, px: Int, cellDp: Float, all: List<String>, idx: Int, modifier: Modifier = Modifier) {
    val shown = rememberDecoded(b64, px)
    Box(
        modifier.height(cellDp.dp).clip(RoundedCornerShape(12.dp)).background(ic(P.surface))
            .clickable { openViewer(act, all, idx) }
    ) {
        if (shown != null) Image(bitmap = shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

internal fun openViewer(act: MainActivity, images: List<String>, start: Int) {
    if (images.isEmpty()) return
    act.viewerImages.value = images
    act.viewerStart.value = start
    act.dialog.value = "viewer"
}

@Composable
internal fun PostDetailDialog(act: MainActivity, postId: String) {
    val post = act.postsRaw.toList().firstOrNull { it.key == postId }
    LaunchedEffect(postId) { incViews(act, postId) }
    Dialog(onDismissRequest = { act.dialog.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(ic(P.bg))) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp, 14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(R.drawable.ic_back) { act.dialog.value = null }
                Spacer(Modifier.width(12.dp))
                Text("Пост", color = ic(P.text), fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = fontUnbounded)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 6.dp, 0.dp, 20.dp)) {
                if (post != null) item { PostCard(act, post) }
                else item { Text("Не удалось загрузить пост", color = ic(P.textSec), fontSize = 14.sp, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ViewerDialog(act: MainActivity) {
    val images = act.viewerImages.value
    if (images.isEmpty()) { LaunchedEffect(Unit) { act.dialog.value = null }; return }
    val pager = rememberPagerState(initialPage = act.viewerStart.value.coerceIn(0, images.size - 1)) { images.size }
    Dialog(onDismissRequest = { act.dialog.value = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page -> ZoomImage(images[page]) }
            Text(
                "${pager.currentPage + 1} / ${images.size}",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun ZoomImage(b64: String) {
    val ctx = LocalContext.current
    val req = maxOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)
    val shown = rememberDecoded(b64, req)
    val dens = LocalDensity.current
    var scale by remember(b64) { mutableStateOf(1f) }
    var pan by remember(b64) { mutableStateOf(Offset.Zero) }
    val base = Modifier.fillMaxSize().pointerInput(b64) {
        detectTapGestures(onDoubleTap = {
            if (scale > 1.05f) { scale = 1f; pan = Offset.Zero } else { scale = 2.2f; pan = Offset.Zero }
        })
    }
    val zoomed = if (scale > 1.05f) base.pointerInput(b64) {
        detectTransformGestures { _, panBy, zoom, _ ->
            val ns = (scale * zoom).coerceIn(1f, 3f)
            pan += panBy
            scale = ns
            if (scale <= 1.02f) { scale = 1f; pan = Offset.Zero }
        }
    } else base
    Box(zoomed, contentAlignment = Alignment.Center) {
        if (shown != null) Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().scale(scale).offset(with(dens) { pan.x.toDp() }, with(dens) { pan.y.toDp() })
        )
    }
}

private fun pickImages(act: MainActivity) {
    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
    intent.type = "image/*"
    intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
    act.startActivityForResult(android.content.Intent.createChooser(intent, "Выбери фото"), act.PICK_IMAGES)
}

@Composable
private fun AuthForm(act: MainActivity) {
    val email by act.emailInput
    val nick by act.nickInput
    val pass by act.passInput
    val status by act.feedStatus
    Column(Modifier.fillMaxSize().background(ic(P.bg)).padding(24.dp, 48.dp, 24.dp, 0.dp)) {
        Text("Ahem", color = ic(P.text), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = fontUnbounded)
        Text("social", color = ic(P.textSec), fontSize = 13.sp, modifier = Modifier.padding(bottom = 32.dp))
        AuthField(email, "Email") { act.emailInput.value = it }
        Spacer(Modifier.height(10.dp))
        AuthField(nick, "Ник (мин. 5 символов)") { act.nickInput.value = it }
        Spacer(Modifier.height(10.dp))
        AuthField(pass, "Пароль") { act.passInput.value = it }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentButton("Регистрация", Modifier.weight(1f)) { doRegister(act) }
            OutlineButton("Вход", Modifier.weight(1f)) { doLogin(act) }
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = ic(P.textSec), fontSize = 13.sp)
    }
}

@Composable
private fun AuthField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(ic(P.fieldBg), RoundedCornerShape(14.dp))
            .border(1.dp, ic(P.border), RoundedCornerShape(14.dp)).padding(16.dp, 13.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = ic(P.text), fontSize = 15.sp, fontFamily = fontUbuntuReg),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = ic(P.textSec), fontSize = 15.sp); inner() }
        )
    }
}

private fun doRegister(act: MainActivity) {
    if (act.isRegistering) return
    val email = act.emailInput.value.trim()
    val nick = act.nickInput.value.trim()
    val pass = act.passInput.value
    if (email.isEmpty() || nick.isEmpty() || pass.isEmpty()) { act.feedStatus.value = "Заполни все поля"; return }
    if (nick.length < 5) { act.feedStatus.value = "Ник минимум 5 символов"; return }
    act.isRegistering = true
    isNickTaken(act, nick, "") { taken ->
        if (taken) { act.feedStatus.value = "Ник уже занят"; act.isRegistering = false; return@isNickTaken }
        act.auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            act.isRegistering = false
            if (task.isSuccessful) {
                val userId = act.auth.currentUser?.uid ?: return@addOnCompleteListener
                act.database.child("users").child(userId).child("email").setValue(email)
                act.database.child("users").child(userId).child("nick").setValue(nick)
                act.setSession(userId)
                act.currentNick = nick; act.currentEmail = email; act.viewingUserId = userId
                saveAccount(act, userId, nick, email, pass)
                act.saveSessionPrefs()
                enterFeedMode(act)
                loadMyFollowing(act) {}
            } else {
                act.feedStatus.value = authErrorText(task.exception)
            }
        }
    }
}

private fun doLogin(act: MainActivity) {
    if (act.isLoggingIn) return
    val email = act.emailInput.value.trim()
    val pass = act.passInput.value
    if (email.isEmpty() || pass.isEmpty()) { act.feedStatus.value = "Email + пароль"; return }
    act.isLoggingIn = true
    act.auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
        act.isLoggingIn = false
        if (task.isSuccessful) {
            val userId = act.auth.currentUser?.uid ?: return@addOnCompleteListener
            act.setSession(userId)
            act.currentEmail = email; act.viewingUserId = userId
            enterFeedMode(act)
            loadMyFollowing(act) {}
            act.database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    act.currentNick = s.child("nick").getValue(String::class.java) ?: "Аноним"
                    act.currentAvatar = s.child("avatar").getValue(String::class.java) ?: ""
                    act.profAvatarB64.value = act.currentAvatar
                    if (act.currentAvatar.isNotEmpty()) putAvatar(act, userId, act.currentAvatar)
                    saveAccount(act, userId, act.currentNick, email, pass)
                    act.saveSessionPrefs()
                }
                override fun onCancelled(e: DatabaseError) { dbDenied(act, e) }
            })
        } else {
            act.feedStatus.value = authErrorText(task.exception)
        }
    }
}
