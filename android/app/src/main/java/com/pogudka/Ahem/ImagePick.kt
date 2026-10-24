package com.pogudka.Ahem

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

internal fun handleActivityResult(act: MainActivity, requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode != Activity.RESULT_OK || data == null) return
    if (requestCode == act.PICK_IMAGES) {
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) { for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri) } else { data.data?.let { uris.add(it) } }
        if (uris.isEmpty()) return
        act.pendingImages.clear()
        for (u in uris) {
            if (act.pendingImages.size >= 4) break
            try {
                val ins = act.contentResolver.openInputStream(u)
                val bounds = BitmapFactory.Options(); bounds.inJustDecodeBounds = true
                BitmapFactory.decodeStream(ins, null, bounds); ins?.close()
                var sample = 1; while (bounds.outWidth / sample / 2 > 720) sample *= 2
                val ins2 = act.contentResolver.openInputStream(u)
                val opts = BitmapFactory.Options(); opts.inSampleSize = sample
                val bmp = BitmapFactory.decodeStream(ins2, null, opts); ins2?.close()
                if (bmp != null) { val baos = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos); act.pendingImages.add(Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)) }
            } catch (e: Exception) {}
        }
    } else if (requestCode == act.PICK_AVATAR) {
        val uri = data.data ?: return
        try {
            val ins = act.contentResolver.openInputStream(uri)
            val bounds = BitmapFactory.Options(); bounds.inJustDecodeBounds = true
            BitmapFactory.decodeStream(ins, null, bounds); ins?.close()
            var sample = 1; while (bounds.outWidth / sample / 2 > 400) sample *= 2
            val ins2 = act.contentResolver.openInputStream(uri)
            val opts = BitmapFactory.Options(); opts.inSampleSize = sample
            val bmp = BitmapFactory.decodeStream(ins2, null, opts); ins2?.close()
            if (bmp != null) {
                val side = minOf(bmp.width, bmp.height)
                val cropped = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                val scaled = Bitmap.createScaledBitmap(cropped, 240, 240, true)
                val baos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                val avatarB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                act.database.child("users").child(act.currentUserId).child("avatar").setValue(avatarB64)
                act.currentAvatar = avatarB64; act.profAvatarB64.value = avatarB64; act.editAvatarB64.value = avatarB64
            }
        } catch (e: Exception) {}
    } else if (requestCode == act.PICK_VIDEO) {
        act.pendingVideoUri.value = data.data
    }
}
