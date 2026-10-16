package com.pogudka.Ahem

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

internal fun heartSet(iv: ImageView, liked: Boolean) {
    iv.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
    iv.setColorFilter(if (liked) P.heartOn else P.textSec)
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
        act.database.child("postStats").child(postId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val likes = (mutableData.child("likes").value as? Number)?.toInt() ?: 0
                val likedBy = mutableData.child("likedBy")
                if (likedBy.child(act.currentUserId).value != null) {
                    likedBy.child(act.currentUserId).value = null
                    mutableData.child("likes").value = maxOf(0, likes - 1)
                } else {
                    likedBy.child(act.currentUserId).value = true
                    mutableData.child("likes").value = likes + 1
                }
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
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
                }
            }
        })
    }
}

internal fun incrementViews(act: MainActivity, postId: String, onDone: ((Long) -> Unit)?) {
    if (act.currentUserId.isEmpty()) return
    if (postId in act.viewedLocally) {
        onDone?.invoke(((act.statsMap[postId]?.child("views")?.value as? Number)?.toLong()) ?: 0L)
        return
    }
    act.database.child("postStats").child(postId).runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val views = (mutableData.child("views").value as? Number)?.toLong() ?: 0L
            val viewedBy = mutableData.child("viewedBy")
            if (viewedBy.child(act.currentUserId).value != null) {
                act.viewedLocally.add(postId)
                return Transaction.success(mutableData)
            }
            viewedBy.child(act.currentUserId).value = true
            mutableData.child("views").value = views + 1
            return Transaction.success(mutableData)
        }
        override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
            if (committed && data != null) {
                val nv = (data.child("views").value as? Number)?.toLong() ?: 0L
                act.viewedLocally.add(postId)
                act.statsMap[postId] = data
                onDone?.invoke(nv)
            }
        }
    })
}
