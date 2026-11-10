package com.pogudka.Ahem

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

internal fun handleActivityResult(act: MainActivity, requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode != android.app.Activity.RESULT_OK || data == null) return
    if (requestCode == act.PICK_IMAGES) {
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount.coerceAtMost(4)) {
                val uri = clip.getItemAt(i).uri
                addImage(act, uri)
            }
        } else {
            val u = data.data
            if (u != null) addImage(act, u)
        }
    } else if (requestCode == act.PICK_AVATAR) {
        val uri = data.data ?: return
        try {
            val ins = act.contentResolver.openInputStream(uri) ?: return
            val bmp = BitmapFactory.decodeStream(ins) ?: run { ins.close(); return }
            ins.close()
            val out = ByteArrayOutputStream()
            val scaled = Bitmap.createScaledBitmap(bmp, 512, 512, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            act.editAvatarB64.value = b64
        } catch (e: Exception) {}
    }
}

private fun addImage(act: MainActivity, uri: Uri) {
    if (act.pendingImages.size >= 4) return
    try {
        val ins = act.contentResolver.openInputStream(uri) ?: return
        val bmp = BitmapFactory.decodeStream(ins) ?: run { ins.close(); return }
        ins.close()
        val out = ByteArrayOutputStream()
        val w = bmp.width
        val h = bmp.height
        val scale = if (w > 2048 || h > 2048) 2048f / maxOf(w, h).toFloat() else 1f
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        act.pendingImages.add(b64)
    } catch (e: Exception) {}
}
