package com.goolem.kmp.phash

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Perceptual hash (pHash) for image similarity detection — pure Kotlin, no dependencies.
 *
 * Computes a 64-bit fingerprint that is robust to resizing, minor color shifts,
 * and JPEG re-compression. The same principle used by PhotoDNA (Microsoft/NCMEC).
 *
 * Algorithm:
 *   1. Reduce image to 32×32 grayscale.
 *   2. Apply 2D DCT-II.
 *   3. Take top-left 8×8 DCT coefficients (excluding DC at [0][0]).
 *   4. Bit i = 1 if coefficient[i] > mean, else 0.
 *   → Result: 64-bit Long.
 *
 * Reference: https://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 *
 * Usage:
 * ```kotlin
 * val grayscale = decodeToGrayscale32x32(imageBytes) ?: return
 * val hash = PHash.compute(grayscale)
 * val similar = PHash.isSimilar(hash, knownHash)
 * ```
 */
object PHash {

    private const val SIZE = 32
    private const val KEEP = 8

    /**
     * Compute pHash from a 32×32 grayscale byte array (row-major, one byte per pixel).
     * Use [decodeToGrayscale32x32] to obtain the correct input from raw image bytes.
     */
    fun compute(grayscale32x32: ByteArray): Long {
        require(grayscale32x32.size == SIZE * SIZE) {
            "Expected ${SIZE * SIZE} bytes, got ${grayscale32x32.size}"
        }
        val pixels = Array(SIZE) { row ->
            FloatArray(SIZE) { col -> (grayscale32x32[row * SIZE + col].toInt() and 0xFF).toFloat() }
        }
        val dct    = dct2d(pixels)
        val coeffs = FloatArray(KEEP * KEEP)
        var idx    = 0
        for (r in 0 until KEEP) for (c in 0 until KEEP) {
            if (r == 0 && c == 0) { idx++; continue }
            coeffs[idx++] = dct[r][c]
        }
        val avg = coeffs.average().toFloat()
        var hash = 0L
        for (i in coeffs.indices) if (coeffs[i] > avg) hash = hash or (1L shl i)
        return hash
    }

    /** Number of differing bits between two hashes (0 = identical, 64 = completely different). */
    fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

    /**
     * Returns true if two hashes are visually similar within [threshold] Hamming bits.
     * A threshold of 10 is generous (more matches); 6 is stricter (fewer false positives).
     */
    fun isSimilar(a: Long, b: Long, threshold: Int = 10): Boolean =
        hammingDistance(a, b) <= threshold

    private fun dct2d(input: Array<FloatArray>): Array<FloatArray> {
        val n      = input.size
        val rowDct = Array(n) { r -> dct1d(input[r]) }
        val result = Array(n) { FloatArray(n) }
        for (col in 0 until n) {
            val colDct = dct1d(FloatArray(n) { row -> rowDct[row][col] })
            for (row in 0 until n) result[row][col] = colDct[row]
        }
        return result
    }

    private fun dct1d(input: FloatArray): FloatArray {
        val n   = input.size
        val out = FloatArray(n)
        val pi  = kotlin.math.PI
        for (k in 0 until n) {
            var sum = 0.0
            for (x in 0 until n) sum += input[x] * cos(pi * k * (2.0 * x + 1.0) / (2.0 * n))
            out[k] = (if (k == 0) sqrt(1.0 / n) else sqrt(2.0 / n) * sum).toFloat()
        }
        return out
    }
}
