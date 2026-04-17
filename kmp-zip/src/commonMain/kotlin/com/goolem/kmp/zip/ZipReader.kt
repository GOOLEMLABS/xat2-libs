package com.goolem.kmp.zip

/**
 * Minimal ZIP reader — pure Kotlin, no external dependencies.
 *
 * Parses the **central directory** (authoritative file list) rather than walking
 * local file headers, which prevents ZIP-smuggling attacks where two tools
 * disagree about an archive's contents.
 *
 * Supports STORE (method 0, uncompressed) and DEFLATE (method 8).
 * ZIP64 is not supported — archives with entries or offsets larger than 2^32 - 1
 * throw [ZipFormatException].
 *
 * Defence-in-depth limits (tune via the companion `Limits`):
 *  - `maxEntryBytes`     — per-entry uncompressed ceiling (zip-bomb defence).
 *  - `maxTotalBytes`     — sum of uncompressed bytes across the archive.
 *  - `maxEntries`        — entry count ceiling.
 *  - `maxCompressionRatio` — per-entry uncompressed / compressed cap.
 *
 * Usage:
 * ```kotlin
 * val entries: Map<String, ByteArray> = ZipReader.read(zipBytes)
 * val manifest = entries["manifest.json"]?.decodeToString()
 * ```
 *
 * ZIP format reference: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
 */
public object ZipReader {

    /** Per-entry uncompressed size ceiling. */
    public var maxEntryBytes: Int = 10_000_000

    /** Total uncompressed bytes across all entries in a single archive. */
    public var maxTotalBytes: Long = 100_000_000L

    /** Total entry count ceiling. */
    public var maxEntries: Int = 4_096

    /** Maximum ratio uncompressed/compressed per entry (0 disables). */
    public var maxCompressionRatio: Int = 200

    /**
     * Read a ZIP archive from [zipBytes].
     *
     * @return Map of filename → raw file bytes. Directories are excluded.
     * @throws ZipFormatException on malformed archives, unsupported features,
     *         or policy violations (size, ratio, unsafe paths).
     */
    public fun read(zipBytes: ByteArray): Map<String, ByteArray> {
        if (zipBytes.size < EOCD_MIN_SIZE) throw ZipFormatException("archive too small")

        val eocd = findEocd(zipBytes)
        val totalEntries = readUInt16(zipBytes, eocd + 10)
        val cdSize = readUInt32(zipBytes, eocd + 12)
        val cdOffset = readUInt32(zipBytes, eocd + 16)

        if (totalEntries > maxEntries) {
            throw ZipFormatException("too many entries ($totalEntries > $maxEntries)")
        }
        if (cdOffset > Int.MAX_VALUE.toLong() || cdSize > Int.MAX_VALUE.toLong()) {
            throw ZipFormatException("ZIP64 not supported")
        }
        val cdStart = cdOffset.toInt()
        val cdEnd = cdStart + cdSize.toInt()
        if (cdStart < 0 || cdEnd > zipBytes.size || cdEnd < cdStart) {
            throw ZipFormatException("central directory out of bounds")
        }

        val entries = LinkedHashMap<String, ByteArray>(totalEntries)
        var totalBytes = 0L
        var cursor = cdStart
        var seen = 0

        while (cursor < cdEnd) {
            if (cursor + CDH_MIN_SIZE > cdEnd) {
                throw ZipFormatException("truncated central directory header")
            }
            if (readUInt32(zipBytes, cursor) != CDH_SIG) {
                throw ZipFormatException("bad central directory signature")
            }

            val method = readUInt16(zipBytes, cursor + 10)
            val compressedRaw = readUInt32(zipBytes, cursor + 20)
            val uncompressedRaw = readUInt32(zipBytes, cursor + 24)
            val nameLen = readUInt16(zipBytes, cursor + 28)
            val extraLen = readUInt16(zipBytes, cursor + 30)
            val commentLen = readUInt16(zipBytes, cursor + 32)
            val lfhOffsetRaw = readUInt32(zipBytes, cursor + 42)

            if (compressedRaw == 0xFFFFFFFFL ||
                uncompressedRaw == 0xFFFFFFFFL ||
                lfhOffsetRaw == 0xFFFFFFFFL
            ) {
                throw ZipFormatException("ZIP64 not supported")
            }
            if (compressedRaw > Int.MAX_VALUE.toLong() ||
                uncompressedRaw > Int.MAX_VALUE.toLong() ||
                lfhOffsetRaw > Int.MAX_VALUE.toLong()
            ) {
                throw ZipFormatException("entry size exceeds Int.MAX_VALUE")
            }
            val compressedSize = compressedRaw.toInt()
            val uncompressedSize = uncompressedRaw.toInt()
            val lfhOffset = lfhOffsetRaw.toInt()

            val nameStart = cursor + CDH_MIN_SIZE
            if (nameStart + nameLen > cdEnd) {
                throw ZipFormatException("truncated central directory entry name")
            }
            val rawName = zipBytes.copyOfRange(nameStart, nameStart + nameLen).decodeToString()
            cursor = nameStart + nameLen + extraLen + commentLen

            seen++

            val isDir = rawName.endsWith("/") || rawName.endsWith("\\")
            if (isDir) continue
            if (rawName.isEmpty()) continue

            val safeName = sanitizePath(rawName)
                ?: throw ZipFormatException("unsafe entry path: ${rawName.take(32)}")

            if (uncompressedSize > maxEntryBytes) {
                throw ZipFormatException("entry '$safeName' exceeds maxEntryBytes")
            }
            if (maxCompressionRatio > 0 && compressedSize > 0 &&
                uncompressedSize / compressedSize > maxCompressionRatio
            ) {
                throw ZipFormatException("entry '$safeName' exceeds compression ratio cap")
            }
            val nextTotal = totalBytes + uncompressedSize
            if (nextTotal > maxTotalBytes || nextTotal < totalBytes) {
                throw ZipFormatException("archive total exceeds maxTotalBytes")
            }

            if (method != METHOD_STORE && method != METHOD_DEFLATE) continue

            val (dataStart, dataSize) = resolveDataRegion(
                zipBytes, lfhOffset, compressedSize, safeName
            )

            val fileData = when (method) {
                METHOD_STORE -> {
                    if (compressedSize != uncompressedSize) {
                        throw ZipFormatException("STORE size mismatch for '$safeName'")
                    }
                    zipBytes.copyOfRange(dataStart, dataStart + dataSize)
                }
                METHOD_DEFLATE -> inflate(zipBytes, dataStart, dataSize, uncompressedSize)
                else -> continue
            }

            if (fileData.size != uncompressedSize) {
                throw ZipFormatException("inflated size mismatch for '$safeName'")
            }
            entries[safeName] = fileData
            totalBytes = nextTotal
        }

        if (seen != totalEntries) {
            throw ZipFormatException("central directory entry count mismatch")
        }
        return entries
    }

    /**
     * Validate the local file header at [lfhOffset] matches and return the
     * (offset, size) of the compressed payload inside [zipBytes].
     */
    private fun resolveDataRegion(
        zipBytes: ByteArray,
        lfhOffset: Int,
        compressedSize: Int,
        name: String,
    ): Pair<Int, Int> {
        if (lfhOffset < 0 || lfhOffset + LFH_MIN_SIZE > zipBytes.size) {
            throw ZipFormatException("local header out of bounds for '$name'")
        }
        if (readUInt32(zipBytes, lfhOffset) != LFH_SIG) {
            throw ZipFormatException("bad local header signature for '$name'")
        }
        val lfhNameLen = readUInt16(zipBytes, lfhOffset + 26)
        val lfhExtraLen = readUInt16(zipBytes, lfhOffset + 28)
        val dataStart = lfhOffset + LFH_MIN_SIZE + lfhNameLen + lfhExtraLen
        val dataEnd = dataStart + compressedSize
        if (dataStart < 0 || dataEnd > zipBytes.size || dataEnd < dataStart) {
            throw ZipFormatException("entry data out of bounds for '$name'")
        }
        return dataStart to compressedSize
    }

    /**
     * Returns a normalised safe path or `null` if the entry name is unsafe.
     *
     * Rejects: absolute paths (`/foo`, `C:\foo`), parent traversal (`..`),
     * backslashes, NUL bytes, and empty segments.
     */
    private fun sanitizePath(name: String): String? {
        if (name.isEmpty() || name.length > 4_096) return null
        if (name.contains('\u0000')) return null
        if (name.contains('\\')) return null
        if (name.startsWith('/')) return null
        if (name.length >= 2 && name[1] == ':') return null
        val parts = name.split('/')
        for (p in parts) {
            if (p.isEmpty() || p == "." || p == "..") return null
        }
        return name
    }

    private fun findEocd(bytes: ByteArray): Int {
        val searchStart = maxOf(0, bytes.size - EOCD_MIN_SIZE - MAX_COMMENT)
        var i = bytes.size - EOCD_MIN_SIZE
        while (i >= searchStart) {
            if (readUInt32(bytes, i) == EOCD_SIG) {
                val commentLen = readUInt16(bytes, i + 20)
                if (i + EOCD_MIN_SIZE + commentLen == bytes.size) return i
            }
            i--
        }
        throw ZipFormatException("end of central directory not found")
    }

    private const val LFH_SIG = 0x04034b50L
    private const val CDH_SIG = 0x02014b50L
    private const val EOCD_SIG = 0x06054b50L
    private const val LFH_MIN_SIZE = 30
    private const val CDH_MIN_SIZE = 46
    private const val EOCD_MIN_SIZE = 22
    private const val MAX_COMMENT = 65_535
    private const val METHOD_STORE = 0
    private const val METHOD_DEFLATE = 8

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(data: ByteArray, offset: Int): Long =
        (data[offset].toInt()     and 0xFF).toLong()              or
        ((data[offset + 1].toInt() and 0xFF).toLong() shl  8)     or
        ((data[offset + 2].toInt() and 0xFF).toLong() shl 16)     or
        ((data[offset + 3].toInt() and 0xFF).toLong() shl 24)
}

/** Thrown on any ZIP parsing or policy error. */
public class ZipFormatException(message: String) : RuntimeException(message)

/**
 * Platform-specific raw DEFLATE decompressor (no zlib/gzip header).
 *
 * Must either return exactly [uncompressedSize] bytes or throw
 * [ZipFormatException] — returning fewer bytes indicates a malformed stream.
 */
public expect fun inflate(
    data: ByteArray,
    offset: Int,
    compressedSize: Int,
    uncompressedSize: Int,
): ByteArray
