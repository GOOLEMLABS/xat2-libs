package com.goolem.kmp.phash

/**
 * Platform-specific image decoder for pHash input.
 *
 * Returns exactly 32×32 = 1024 grayscale bytes (row-major, Rec.601 luminance),
 * or null if [imageBytes] cannot be decoded.
 *
 * Provided by `androidMain` (android.graphics.BitmapFactory) and
 * `iosMain` (CoreGraphics CGBitmapContext).
 */
expect fun decodeToGrayscale32x32(imageBytes: ByteArray): ByteArray?
