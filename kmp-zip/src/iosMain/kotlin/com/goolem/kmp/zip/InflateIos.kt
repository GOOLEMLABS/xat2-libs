@file:OptIn(ExperimentalForeignApi::class)

package com.goolem.kmp.zip

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.inflate as zlibInflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

actual fun inflate(data: ByteArray, offset: Int, compressedSize: Int, uncompressedSize: Int): ByteArray {
    val output     = ByteArray(uncompressedSize)
    val compressed = data.copyOfRange(offset, offset + compressedSize)

    memScoped {
        val stream = alloc<z_stream>()
        compressed.usePinned { pinIn ->
            output.usePinned { pinOut ->
                stream.next_in   = pinIn.addressOf(0).reinterpret()
                stream.avail_in  = compressedSize.toUInt()
                stream.next_out  = pinOut.addressOf(0).reinterpret()
                stream.avail_out = uncompressedSize.toUInt()
                inflateInit2(stream.ptr, -15) // -15 = raw deflate
                zlibInflate(stream.ptr, Z_FINISH)
                inflateEnd(stream.ptr)
            }
        }
    }
    return output
}
