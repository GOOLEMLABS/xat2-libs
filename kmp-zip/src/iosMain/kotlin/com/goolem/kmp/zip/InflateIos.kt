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
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate as zlibInflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

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

    val output = ByteArray(uncompressedSize)
    val compressed = data.copyOfRange(offset, offset + compressedSize)

    var initRc = 0
    var infRc = 0
    var produced = 0
    memScoped {
        val stream = alloc<z_stream>()
        compressed.usePinned { pinIn ->
            output.usePinned { pinOut ->
                stream.next_in = pinIn.addressOf(0).reinterpret()
                stream.avail_in = compressedSize.toUInt()
                stream.next_out = pinOut.addressOf(0).reinterpret()
                stream.avail_out = uncompressedSize.toUInt()
                initRc = inflateInit2(stream.ptr, -15) // -15 = raw deflate
                if (initRc == Z_OK) {
                    infRc = zlibInflate(stream.ptr, Z_FINISH)
                    produced = uncompressedSize - stream.avail_out.toInt()
                    inflateEnd(stream.ptr)
                }
            }
        }
    }

    if (initRc != Z_OK) {
        throw ZipFormatException("inflateInit2 failed: rc=$initRc")
    }
    if (infRc != Z_STREAM_END) {
        throw ZipFormatException("inflate failed: rc=$infRc produced=$produced")
    }
    if (produced != uncompressedSize) {
        throw ZipFormatException("inflate short: expected=$uncompressedSize got=$produced")
    }
    return output
}
