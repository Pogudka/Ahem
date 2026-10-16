package com.pogudka.Ahem

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

internal fun heartSet(iv: ImageView, liked: Boolean) {
    iv.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
    iv.setColorFilter(if (liked) P.heartOn else P.textSec)
}

internal fun readOnce(
    ref: DatabaseReference,
    act: MainActivity,
    left: Int,
    onOk: (DataSnapshot) -> Unit,
    onFail: () -> Unit
) {
    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            onOk(snapshot)
        }
        override fun onCancelled(error: DatabaseError) {
            if (left > 1) {
                act.uiHandler.postDelayed({ readOnce(ref, act, left - 1, onOk, onFail) }, 600)
            } else {
                onFail()
            }
        }
    })
}

internal fun readStatsInto(act: MainActivity, onDone: () -> Unit) {
    attemptStats(act, 3, onDone)
}

private fun attemptStats(act: MainActivity, left: Int, onDone: () -> Unit) {
    act.database.child("postStats").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (s in snapshot.children) {
                val k = s.key ?: continue
                act.statsMap[k] = s
            }
            onDone()
        }
        override fun onCancelled(error: DatabaseError) {
            if (left > 1) {
                act.uiHandler.postDelayed({ attemptStats(act, left - 1, onDone) }, 600)
            } else {
                onDone()
            }
        }
    })
}

internal fun txWithRetry(
    ref: DatabaseReference,
    act: MainActivity,
    attempts: Int,
    body: (MutableData) -> Transaction.Result,
    onResult: (Boolean, DataSnapshot?, DatabaseError?) -> Unit
) {
    ref.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            return body(mutableData)
        }
        override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
            if (committed || error == null || attempts <= 1) {
                onResult(committed, data, error)
            } else {
                act.uiHandler.postDelayed({
                    txWithRetry(ref, act, attempts - 1, body, onResult)
                }, 700)
            }
        }
    })
}

internal fun attachLike(
    act: MainActivity,
    root: View,
    countTv: TextView,
    heartIv: ImageView,
    postId: String,
    initialLiked: Boolean
) {
    val liked = booleanArrayOf(initialLiked)
    heartSet(heartIv, initialLiked)
    root.setOnClickListener {
        if (act.currentUserId.isEmpty()) return@setOnClickListener
        val prevLiked = liked[0]
        val prevCount = parseCount(countTv)
        liked[0] = !prevLiked
        countTv.text = "♥ " + if (liked[0]) fmtNum(prevCount + 1L) else fmtNum(maxOf(0, prevCount - 1).toLong())
        heartSet(heartIv, liked[0])
        txWithRetry(act.database.child("postStats").child(postId), act, 3, { mutableData ->
            val likes = (mutableData.child("likes").value as? Number)?.toInt() ?: 0
            val likedBy = mutableData.child("likedBy")
            if (likedBy.child(act.currentUserId).value != null) {
                likedBy.child(act.currentUserId).value = null
                mutableData.child("likes").value = maxOf(0, likes - 1)
            } else {
                likedBy.child(act.currentUserId).value = true
                mutableData.child("likes").value = likes + 1
            }
            Transaction.success(mutableData)
        }) { committed, data, error ->
            if (committed && data != null) {
                val newCount = (data.child("likes").value as? Number)?.toInt() ?: 0
                liked[0] = data.child("likedBy").child(act.currentUserId).value != null
                countTv.text = "♥ " + fmtNum(newCount.toLong())
                heartSet(heartIv, liked[0])
                act.statsMap[postId] = data
            } else {
                liked[0] = prevLiked
                countTv.text = "♥ " + fmtNum(prevCount.toLong())
                heartSet(heartIv, prevLiked)
                Toast.makeText(act, "Лайк не сохранился: " + (error?.message ?: "нет ответа сервера"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun incrementViews(act: MainActivity, postId: String, onDone: ((Long) -> Unit)?) {
    if (act.currentUserId.isEmpty()) return
    if (postId in act.viewedLocally) {
        val cur = ((act.statsMap[postId]?.child("views")?.value as? Number)?.toLong()) ?: 0L
        act.viewsTvMap[postId]?.forEach { it.text = fmtNum(cur) }
        onDone?.invoke(cur)
        return
    }
    txWithRetry(act.database.child("postStats").child(postId), act, 3, { mutableData ->
        val views = (mutableData.child("views").value as? Number)?.toLong() ?: 0L
        val viewedBy = mutableData.child("viewedBy")
        if (viewedBy.child(act.currentUserId).value != null) {
            act.viewedLocally.add(postId)
            return@txWithRetry Transaction.success(mutableData)
        }
        viewedBy.child(act.currentUserId).value = true
        mutableData.child("views").value = views + 1
        Transaction.success(mutableData)
    }) { committed, data, _ ->
        if (committed && data != null) {
            val nv = (data.child("views").value as? Number)?.toLong() ?: 0L
            act.viewedLocally.add(postId)
            act.statsMap[postId] = data
            act.viewsTvMap[postId]?.forEach { it.text = fmtNum(nv) }
            onDone?.invoke(nv)
        }
    }
}

internal fun migrateStats(act: MainActivity, onDone: () -> Unit) {
    val prefs = act.getSharedPreferences("ahem", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("migrated_v4", false)) {
        onDone()
        return
    }
    if (act.migrationRunning) {
        onDone()
        return
    }
    act.migrationRunning = true
    readOnce(act.database.child("posts"), act, 3, { snapshot ->
        var total = 0
        var doneCnt = 0
        var failed = 0
        fun oneDone(ok: Boolean) {
            if (!ok) failed++
            doneCnt++
            if (doneCnt >= total) {
                act.migrationRunning = false
                if (failed == 0) prefs.edit().putBoolean("migrated_v4", true).apply()
                onDone()
            }
        }
        for (p in snapshot.children) {
            val id = p.key ?: continue
            val images = p.child("images").getValue(String::class.java) ?: ""
            val likes = p.child("likes").value as? Number
            val views = p.child("views").value as? Number
            val hasLiked = p.child("likedBy").hasChildren()
            val hasViewed = p.child("viewedBy").hasChildren()
            if (images.isNotEmpty()) {
                total++
                act.database.child("postMedia").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(ms: DataSnapshot) {
                        if (ms.exists()) {
                            p.ref.child("images").setValue(null)
                            oneDone(true)
                        } else {
                            act.database.child("postMedia").child(id).setValue(images)
                            p.ref.child("images").setValue(null)
                            oneDone(true)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) { oneDone(false) }
                })
            }
            if (likes != null || views != null || hasLiked || hasViewed) {
                total++
                val lb = HashMap<String, Any>()
                for (c in p.child("likedBy").children) c.key?.let { lb[it] = true }
                val vb = HashMap<String, Any>()
                for (c in p.child("viewedBy").children) c.key?.let { vb[it] = true }
                txWithRetry(act.database.child("postStats").child(id), act, 2, { mutableData ->
                    if (mutableData.child("likes").value is Number) return@txWithRetry Transaction.success(mutableData)
                    mutableData.child("likes").value = (likes ?: 0).toLong()
                    mutableData.child("views").value = (views ?: 0).toLong()
                    for ((k, v) in lb) mutableData.child("likedBy").child(k).value = v
                    for ((k, v) in vb) mutableData.child("viewedBy").child(k).value = v
                    Transaction.success(mutableData)
                }) { committed, _, _ ->
                    oneDone(committed)
                }
            }
        }
        if (total == 0) {
            act.migrationRunning = false
            prefs.edit().putBoolean("migrated_v4", true).apply()
            onDone()
        }
    }, {
        act.migrationRunning = false
        onDone()
    })
}
