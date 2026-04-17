package com.goolem.kmp.phash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

private const val MAX_DIMENSION = 4096

public actual fun decodeToGrayscale32x32(imageBytes: ByteArray): ByteArray? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, opts)
    val w = opts.outWidth
    val h = opts.outHeight
    if (w <= 0 || h <= 0) return null
    if (w > MAX_DIMENSION || h > MAX_DIMENSION) return null

    var sample = 1
    var tw = w; var th = h
    while (tw > 256 || th > 256) { sample *= 2; tw /= 2; th /= 2 }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
        ?: return null
    val scaled = Bitmap.createScaledBitmap(original, 32, 32, true)
    if (original !== scaled) original.recycle()

    val out = ByteArray(32 * 32)
    for (y in 0 until 32) for (x in 0 until 32) {
        val pixel = scaled.getPixel(x, y)
        val luma = (0.299 * Color.red(pixel) +
                    0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)).toInt().coerceIn(0, 255)
        out[y * 32 + x] = luma.toByte()
    }
    scaled.recycle()
    return out
}
