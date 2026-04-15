package com.goolem.kmp.phash

/**
 * Scans images for known harmful content using perceptual hashing (pHash).
 *
 * How it works (same principle as PhotoDNA/Microsoft NCMEC):
 *   1. Decode image → 32×32 grayscale pixels via [decodeToGrayscale32x32].
 *   2. Compute 64-bit pHash via 2D DCT ([PHash.compute]).
 *   3. Compare against [HashBlocklist] using Hamming distance ≤ threshold.
 *   4. Return [Result.Blocked] if any known-bad hash matches.
 *
 * The blocklist ships empty. To enable CSAM detection:
 *   - Register with NCMEC: https://www.missingkids.org/gethelpnow/cybertipline/hash-sharing
 *   - Or use Meta ThreatExchange PDQ: https://github.com/facebook/ThreatExchange/tree/main/pdq
 *   - Load hashes at startup: `HashBlocklist.Default.loadFromLines(lines)`
 *
 * Usage:
 * ```kotlin
 * val scanner = ContentScanner()
 * when (val result = scanner.scan(imageBytes)) {
 *     is ContentScanner.Result.Allowed -> upload(imageBytes)
 *     is ContentScanner.Result.Blocked -> showError(result.reason)
 * }
 * ```
 */
class ContentScanner(private val blocklist: HashBlocklist = HashBlocklist.Default) {

    sealed class Result {
        data object Allowed : Result()
        data class Blocked(val reason: String) : Result()
    }

    /**
     * Scan [imageBytes] (raw JPEG / WebP / PNG).
     *
     * Returns [Result.Allowed] on decoding errors to avoid blocking on false failures (fail-open).
     */
    fun scan(imageBytes: ByteArray): Result {
        if (blocklist.size == 0) return Result.Allowed

        val grayscale = decodeToGrayscale32x32(imageBytes) ?: return Result.Allowed
        val hash      = PHash.compute(grayscale)
        val match     = blocklist.findMatch(hash) ?: return Result.Allowed

        return Result.Blocked("Content matches known hash (${match.label})")
    }
}
