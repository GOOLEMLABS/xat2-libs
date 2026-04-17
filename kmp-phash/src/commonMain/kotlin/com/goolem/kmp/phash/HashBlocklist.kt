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
public class HashBlocklist {

    public data class BlockedHash(val hash: Long, val label: String)

    private val hashes = mutableListOf<BlockedHash>()
    private val exactSet = mutableSetOf<Long>()

    public fun addHash(hash: Long, label: String = "blocked") {
        hashes.add(BlockedHash(hash, label))
        exactSet.add(hash)
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
    public fun loadFromLines(lines: List<String>) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val parts = trimmed.split(',', limit = 2)
            runCatching {
                val hash  = parts[0].trim().toLong(16)
                val label = if (parts.size > 1) parts[1].trim() else "blocked"
                hashes.add(BlockedHash(hash, label))
                exactSet.add(hash)
            }
        }
    }

    public val size: Int get() = hashes.size

    /**
     * Find the first match for [hash] within [threshold] Hamming bits.
     * Returns null if no match is found.
     *
     * Fast path: exact matches (threshold 0) are checked via HashSet O(1).
     * Fuzzy matches iterate linearly — for very large blocklists (>100k entries)
     * consider multi-index hashing or partitioned lookup.
     */
    public fun findMatch(hash: Long, threshold: Int = 10): BlockedHash? {
        if (exactSet.contains(hash)) {
            return hashes.first { it.hash == hash }
        }
        if (threshold == 0) return null
        return hashes.firstOrNull { PHash.isSimilar(it.hash, hash, threshold) }
    }

    public companion object {
        public val Default: HashBlocklist = HashBlocklist()
    }
}
