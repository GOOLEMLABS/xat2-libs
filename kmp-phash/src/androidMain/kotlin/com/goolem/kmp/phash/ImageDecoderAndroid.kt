package com.goolem.kmp.phash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

actual fun decodeToGrayscale32x32(imageBytes: ByteArray): ByteArray? {
    val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
    val scaled   = Bitmap.createScaledBitmap(original, 32, 32, true)
    if (original !== scaled) original.recycle()

    val out = ByteArray(32 * 32)
    for (y in 0 until 32) for (x in 0 until 32) {
        val pixel = scaled.getPixel(x, y)
        val luma  = (0.299 * Color.red(pixel) +
                     0.587 * Color.green(pixel) +
                     0.114 * Color.blue(pixel)).toInt().coerceIn(0, 255)
        out[y * 32 + x] = luma.toByte()
    }
    scaled.recycle()
    return out
}
