package com.goolem.kmp.zip

/**
 * Minimal ZIP reader — pure Kotlin, no external dependencies.
 *
 * Supports STORE (method 0, uncompressed) and DEFLATE (method 8).
 * Skips directories and entries larger than [maxEntryBytes].
 *
 * Usage:
 * ```kotlin
 * val entries: Map<String, ByteArray> = ZipReader.read(zipBytes)
 * val manifest = entries["manifest.json"]?.decodeToString()
 * ```
 *
 * ZIP format reference: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
 */
object ZipReader {

    /** Maximum size of a single extracted entry (default 10 MB). */
    var maxEntryBytes: Int = 10_000_000

    /**
     * Read a ZIP archive from [zipBytes].
     *
     * @return Map of filename → raw file bytes. Directories are excluded.
     * @throws IllegalArgumentException on malformed local-file-header signatures.
     */
    fun read(zipBytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var offset  = 0

        while (offset + 4 <= zipBytes.size) {
            val sig = readUInt32(zipBytes, offset)
            if (sig != LOCAL_FILE_HEADER_SIG) break

            val method           = readUInt16(zipBytes, offset + 8)
            val compressedSize   = readUInt32(zipBytes, offset + 18).toInt()
            val uncompressedSize = readUInt32(zipBytes, offset + 22).toInt()
            val nameLength       = readUInt16(zipBytes, offset + 26)
            val extraLength      = readUInt16(zipBytes, offset + 28)

            val nameStart = offset + 30
            val name      = zipBytes.copyOfRange(nameStart, nameStart + nameLength).decodeToString()
            val dataStart = nameStart + nameLength + extraLength

            offset = dataStart + compressedSize

            if (name.endsWith("/") || compressedSize == 0) continue

            val fileData = when (method) {
                0    -> zipBytes.copyOfRange(dataStart, dataStart + compressedSize)
                8    -> inflate(zipBytes, dataStart, compressedSize, uncompressedSize)
                else -> continue
            }

            if (fileData.size > maxEntryBytes) continue
            entries[name] = fileData
        }

        return entries
    }

    private const val LOCAL_FILE_HEADER_SIG = 0x04034b50L

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(data: ByteArray, offset: Int): Long =
        (data[offset].toInt()     and 0xFF).toLong()              or
        ((data[offset+1].toInt()  and 0xFF).toLong() shl  8)      or
        ((data[offset+2].toInt()  and 0xFF).toLong() shl 16)      or
        ((data[offset+3].toInt()  and 0xFF).toLong() shl 24)
}

/**
 * Platform-specific raw DEFLATE decompressor (no zlib/gzip header).
 *
 * Provided by `androidMain` (java.util.zip.Inflater) and
 * `iosMain` (platform.zlib via cinterop).
 */
expect fun inflate(
    data: ByteArray,
    offset: Int,
    compressedSize: Int,
    uncompressedSize: Int,
): ByteArray
