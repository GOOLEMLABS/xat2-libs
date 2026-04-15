@file:OptIn(ExperimentalForeignApi::class)

package com.goolem.kmp.tor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CC_SHA512
import platform.CommonCrypto.CC_SHA512_DIGEST_LENGTH

internal actual fun sha512(input: ByteArray): ByteArray {
    val output = ByteArray(CC_SHA512_DIGEST_LENGTH.toInt())
    input.usePinned { inPin ->
        output.usePinned { outPin ->
            CC_SHA512(inPin.addressOf(0), input.size.toUInt(), outPin.addressOf(0))
        }
    }
    return output
}
