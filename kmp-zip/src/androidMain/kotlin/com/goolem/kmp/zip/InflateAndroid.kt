package com.goolem.kmp.zip

import java.util.zip.DataFormatException
import java.util.zip.Inflater

public actual fun inflate(
    data: ByteArray,
    offset: Int,
    compressedSize: Int,
    uncompressedSize: Int,
): ByteArray {
    require(offset >= 0 && compressedSize >= 0 && uncompressedSize >= 0) {
        "negative size or offset"
    }
    require(offset.toLong() + compressedSize <= data.size) { "compressed range OOB" }

    val inflater = Inflater(true) // raw deflate — no zlib header
    try {
        inflater.setInput(data, offset, compressedSize)
        val output = ByteArray(uncompressedSize)
        var written = 0
        while (written < uncompressedSize) {
            val n = try {
                inflater.inflate(output, written, uncompressedSize - written)
            } catch (e: DataFormatException) {
                throw ZipFormatException("inflate: malformed stream (${e.message})")
            }
            if (n == 0) {
                if (inflater.finished()) break
                if (inflater.needsInput() || inflater.needsDictionary()) {
                    throw ZipFormatException("inflate: truncated or needs dictionary")
                }
            }
            written += n
        }
        if (written != uncompressedSize || !inflater.finished()) {
            throw ZipFormatException(
                "inflate: expected $uncompressedSize bytes, got $written"
            )
        }
        return output
    } finally {
        inflater.end()
    }
}
