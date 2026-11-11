package com.pogudka.Ahem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

private const val OPT_MAX_PX = 1080
private const val OPT_QUALITY = 70
private const val OPT_TARGET = 280 * 1024
private const val AV_MAX_PX = 256
private const val AV_QUALITY = 80
private const val AV_TARGET = 200 * 1024

internal fun schedulePhotoOpt(act: MainActivity) {
    val prefs = act.getSharedPreferences("ahem", Context.MODE_PRIVATE)
    if (prefs.getBoolean("photo_opt_v1", false)) return
    act.uiHandler.postDelayed({ runPhotoOpt(act, prefs) }, 6000)
}

private fun runPhotoOpt(act: MainActivity, prefs: android.content.SharedPreferences) {
    if (act.currentUserId.isEmpty()) return
    val tasks = ArrayList<Pair<DatabaseReference, String>>()
    act.database.child("postMedia").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            for (c in s.children) {
                val id = c.key ?: continue
                val raw = c.getValue(String::class.java) ?: continue
                if (raw.isNotEmpty()) tasks.add(Pair(act.database.child("postMedia").child(id), raw))
            }
            val ownAv = act.database.child("users").child(act.currentUserId).child("avatar")
            ownAv.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(a: DataSnapshot) {
                    val av = a.getValue(String::class.java) ?: ""
                    if (av.isNotEmpty()) tasks.add(Pair(ownAv, av))
                    optWorker(tasks, prefs)
                }
                override fun onCancelled(e: DatabaseError) { optWorker(tasks, prefs) }
            })
        }
        override fun onCancelled(e: DatabaseError) { optWorker(tasks, prefs) }
    })
}

private fun optWorker(tasks: List<Pair<DatabaseReference, String>>, prefs: android.content.SharedPreferences) {
    Thread {
        for ((ref, raw) in tasks) {
            try {
                val isAvatar = ref.path.toString().contains("/users/")
                if (isAvatar) {
                    val small = recompress(raw, AV_MAX_PX, AV_QUALITY, AV_TARGET, true)
                    if (small != null && small.length < raw.length) ref.setValue(small)
                } else {
                    val parts = raw.split(",").filter { it.isNotEmpty() }
                    val out = ArrayList<String>()
                    var changed = false
                    for (p in parts) {
                        val s = recompress(p, OPT_MAX_PX, OPT_QUALITY, OPT_TARGET, false)
                        if (s != null && s.length < p.length) { out.add(s); changed = true } else out.add(p)
                    }
                    if (changed) ref.setValue(out.joinToString(","))
                }
            } catch (e: Exception) {}
        }
        prefs.edit().putBoolean("photo_opt_v1", true).apply()
    }.start()
}

private fun recompress(b64: String, maxPx: Int, quality: Int, target: Int, square: Boolean): String? {
    return try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        if (square) {
            val side = minOf(bmp.width, bmp.height)
            val c = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            if (c !== bmp) bmp.recycle()
            bmp = c
        }
        val m = maxOf(bmp.width, bmp.height)
        if (m > maxPx) {
            val s = maxPx.toFloat() / m
            val sc = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
            if (sc !== bmp) bmp.recycle()
            bmp = sc
        }
        var q = quality
        var out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
        while (out.size() > target && q > 46) {
            q -= 8
            out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
        }
        bmp.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }
}
