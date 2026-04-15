package com.goolem.kmp.phash

/**
 * Blocklist of known-bad perceptual hashes for PhotoDNA-style content moderation.
 *
 * Ships empty. Operators populate it by registering with NCMEC or Meta ThreatExchange
 * and loading hashes at app startup via [loadFromLines].
 *
 * NCMEC registration: https://www.missingkids.org/gethelpnow/cybertipline/hash-sharing
 * PDQ open-source:    https://github.com/facebook/ThreatExchange/tree/main/pdq
 *
 * Usage:
 * ```kotlin
 * HashBlocklist.Default.loadFromLines(File("hashes.txt").readLines())
 * val match = HashBlocklist.Default.findMatch(hash)
 * ```
 */
class HashBlocklist {

    /** A single known-bad hash entry. */
    data class BlockedHash(val hash: Long, val label: String)

    private val hashes = mutableListOf<BlockedHash>()

    /** Add a single known-bad hash. */
    fun addHash(hash: Long, label: String = "blocked") {
        hashes.add(BlockedHash(hash, label))
    }

    /**
     * Load hashes from a plain-text list.
     *
     * Each non-empty, non-comment line must be:
     * ```
     * <hex_hash>[,label]
     * ```
     * Lines starting with `#` are ignored.
     */
    fun loadFromLines(lines: List<String>) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val parts = trimmed.split(',', limit = 2)
            runCatching {
                val hash  = parts[0].trim().toLong(16)
                val label = if (parts.size > 1) parts[1].trim() else "blocked"
                hashes.add(BlockedHash(hash, label))
            }
        }
    }

    /** Number of hashes currently loaded. */
    val size: Int get() = hashes.size

    /**
     * Find the first match for [hash] within [threshold] Hamming bits.
     * Returns null if no match is found.
     */
    fun findMatch(hash: Long, threshold: Int = 10): BlockedHash? =
        hashes.firstOrNull { PHash.isSimilar(it.hash, hash, threshold) }

    companion object {
        /** Shared default instance used by [ContentScanner]. */
        val Default = HashBlocklist()
    }
}
