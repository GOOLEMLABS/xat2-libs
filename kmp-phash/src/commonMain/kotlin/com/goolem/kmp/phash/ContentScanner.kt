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
public class ContentScanner(
    private val blocklist: HashBlocklist = HashBlocklist.Default,
    private val failOpen: Boolean = true,
) {

    public sealed class Result {
        public data object Allowed : Result()
        public data class Blocked(val reason: String) : Result()
    }

    /**
     * Scan [imageBytes] (raw JPEG / WebP / PNG).
     *
     * If the image cannot be decoded, behaviour depends on [failOpen]:
     *  - `true` (default): returns [Result.Allowed] to avoid blocking on invalid images.
     *  - `false`: returns [Result.Blocked] with reason — use this when false negatives
     *    are more dangerous than false positives (e.g. CSAM scanning).
     */
    public fun scan(imageBytes: ByteArray): Result {
        if (blocklist.size == 0) return Result.Allowed

        val grayscale = decodeToGrayscale32x32(imageBytes)
        if (grayscale == null) {
            return if (failOpen) Result.Allowed
            else Result.Blocked("Image could not be decoded for content scanning")
        }

        val hash  = PHash.compute(grayscale)
        val match = blocklist.findMatch(hash) ?: return Result.Allowed

        return Result.Blocked("Content matches known hash (${match.label})")
    }
}
