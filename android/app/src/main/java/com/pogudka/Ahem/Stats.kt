package com.pogudka.Ahem

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

internal fun readOnce(ref: DatabaseReference, act: MainActivity, left: Int, onOk: (DataSnapshot) -> Unit, onFail: () -> Unit) {
    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { onOk(snapshot) }
        override fun onCancelled(error: DatabaseError) {
            if (left > 1) act.uiHandler.postDelayed({ readOnce(ref, act, left - 1, onOk, onFail) }, 600) else onFail()
        }
    })
}
internal fun readStatsInto(act: MainActivity, onDone: () -> Unit) = attemptStats(act, 3, onDone)
private fun attemptStats(act: MainActivity, left: Int, onDone: () -> Unit) {
    act.database.child("postStats").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { for (s in snapshot.children) { val k = s.key ?: continue; act.statsMap[k] = s }; act.statsVer.value++; onDone() }
        override fun onCancelled(error: DatabaseError) { if (left > 1) act.uiHandler.postDelayed({ attemptStats(act, left - 1, onDone) }, 600) else onDone() }
    })
}
internal fun txWithRetry(ref: DatabaseReference, act: MainActivity, attempts: Int, body: (MutableData) -> Transaction.Result, onResult: (Boolean, DataSnapshot?, DatabaseError?) -> Unit) {
    ref.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result = body(mutableData)
        override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
            if (committed || error == null || attempts <= 1) onResult(committed, data, error)
            else act.uiHandler.postDelayed({ txWithRetry(ref, act, attempts - 1, body, onResult) }, 700)
        }
    })
}
internal fun migrateStats(act: MainActivity, onDone: () -> Unit) {
    val prefs = act.getSharedPreferences("ahem", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("migrated_v4", false) || act.migrationRunning) { onDone(); return }
    act.migrationRunning = true
    readOnce(act.database.child("posts"), act, 3, { snapshot ->
        var total = 0; var doneCnt = 0; var failed = 0
        fun oneDone(ok: Boolean) { if (!ok) failed++; doneCnt++; if (doneCnt >= total) { act.migrationRunning = false; if (failed == 0) prefs.edit().putBoolean("migrated_v4", true).apply(); onDone() } }
        for (p in snapshot.children) {
            val id = p.key ?: continue
            val images = p.child("images").getValue(String::class.java) ?: ""
            val likes = p.child("likes").value as? Number; val views = p.child("views").value as? Number
            val hasLiked = p.child("likedBy").hasChildren(); val hasViewed = p.child("viewedBy").hasChildren()
            if (images.isNotEmpty()) { total++; act.database.child("postMedia").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(ms: DataSnapshot) { if (!ms.exists()) act.database.child("postMedia").child(id).setValue(images); p.ref.child("images").setValue(null); oneDone(true) }
                override fun onCancelled(error: DatabaseError) { oneDone(false) } }) }
            if (likes != null || views != null || hasLiked || hasViewed) { total++
                val lb = HashMap<String, Any>(); for (c in p.child("likedBy").children) c.key?.let { lb[it] = true }
                val vb = HashMap<String, Any>(); for (c in p.child("viewedBy").children) c.key?.let { vb[it] = true }
                txWithRetry(act.database.child("postStats").child(id), act, 2, { md ->
                    if (md.child("likes").value is Number) return@txWithRetry Transaction.success(md)
                    md.child("likes").value = (likes ?: 0).toLong(); md.child("views").value = (views ?: 0).toLong()
                    for ((k, v) in lb) md.child("likedBy").child(k).value = v; for ((k, v) in vb) md.child("viewedBy").child(k).value = v
                    Transaction.success(md)
                }) { committed, _, _ -> oneDone(committed) } }
        }
        if (total == 0) { act.migrationRunning = false; prefs.edit().putBoolean("migrated_v4", true).apply(); onDone() }
    }, { act.migrationRunning = false; onDone() })
}
internal fun scheduleMigration(act: MainActivity) {
    if (act.migrationScheduled) return
    act.migrationScheduled = true
    act.uiHandler.postDelayed({ migrateStats(act) { } }, 8000)
}
