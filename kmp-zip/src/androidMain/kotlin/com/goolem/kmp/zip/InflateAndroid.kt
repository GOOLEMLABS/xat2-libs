package com.goolem.kmp.zip

import java.util.zip.Inflater

actual fun inflate(data: ByteArray, offset: Int, compressedSize: Int, uncompressedSize: Int): ByteArray {
    val inflater = Inflater(true) // raw deflate — no zlib header
    inflater.setInput(data, offset, compressedSize)
    val output = ByteArray(uncompressedSize)
    inflater.inflate(output)
    inflater.end()
    return output
}
