package com.pogudka.Ahem

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

private const val PHOTO_MAX_PX = 1080
private const val PHOTO_QUALITY = 70
private const val PHOTO_TARGET_BYTES = 280 * 1024
private const val AVATAR_PX = 256
private const val AVATAR_QUALITY = 80

internal fun handleActivityResult(act: MainActivity, requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode != android.app.Activity.RESULT_OK || data == null) return
    if (requestCode == act.PICK_IMAGES) {
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount.coerceAtMost(4)) addImage(act, clip.getItemAt(i).uri)
        } else {
            data.data?.let { addImage(act, it) }
        }
    } else if (requestCode == act.PICK_AVATAR) {
        data.data?.let { setAvatar(act, it) }
    }
}

// Декодируем сразу уменьшенным (inSampleSize), не создавая гигантский Bitmap в памяти.
private fun decodeSampled(act: MainActivity, uri: Uri, maxPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        act.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        act.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) { null }
}

private fun scaleTo(bmp: Bitmap, maxPx: Int): Bitmap {
    val m = maxOf(bmp.width, bmp.height)
    if (m <= maxPx) return bmp
    val s = maxPx.toFloat() / m
    return Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
}

private fun cropSquare(bmp: Bitmap): Bitmap {
    val side = minOf(bmp.width, bmp.height)
    if (side == bmp.width && side == bmp.height) return bmp
    return Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
}

private fun jpeg(bmp: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun addImage(act: MainActivity, uri: Uri) {
    if (act.pendingImages.size >= 4) return
    try {
        val decoded = decodeSampled(act, uri, PHOTO_MAX_PX) ?: return
        val scaled = scaleTo(decoded, PHOTO_MAX_PX)
        if (scaled !== decoded) decoded.recycle()
        var q = PHOTO_QUALITY
        var bytes = jpeg(scaled, q)
        while (bytes.size > PHOTO_TARGET_BYTES && q > 46) {
            q -= 8
            bytes = jpeg(scaled, q)
        }
        scaled.recycle()
        act.pendingImages.add(Base64.encodeToString(bytes, Base64.NO_WRAP))
    } catch (e: Exception) {}
}

private fun setAvatar(act: MainActivity, uri: Uri) {
    try {
        val decoded = decodeSampled(act, uri, AVATAR_PX) ?: return
        val sq = cropSquare(decoded)
        if (sq !== decoded) decoded.recycle()
        val scaled = scaleTo(sq, AVATAR_PX)
        if (scaled !== sq) sq.recycle()
        val bytes = jpeg(scaled, AVATAR_QUALITY)
        scaled.recycle()
        act.editAvatarB64.value = Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {}
}
