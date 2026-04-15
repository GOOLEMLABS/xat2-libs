package com.goolem.kmp.tor

import java.security.MessageDigest

internal actual fun sha512(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-512").digest(input)
