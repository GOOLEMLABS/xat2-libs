package com.goolem.kmp.tor

/**
 * Converts an Ed25519 keypair to the file format used by Tor v3 hidden services.
 *
 * Tor v3 hidden services store their keypair as two files:
 *
 * ```
 * hs_ed25519_secret_key  — 96 bytes: 32-byte header + 64-byte expanded scalar
 * hs_ed25519_public_key  — 64 bytes: 32-byte header + 32-byte public key
 * hostname               — .onion address (written by Tor automatically)
 * ```
 *
 * By writing these files before starting Tor you force a specific hidden service identity.
 * The resulting .onion address is deterministic from the Ed25519 keypair, so the same
 * public key used for messaging becomes the .onion address — no separate Tor key needed.
 *
 * Key expansion follows RFC 8032 §5.1.5:
 * ```
 * h = SHA-512(seed)    where seed = first 32 bytes of the libsodium 64-byte secret key
 * h[0]  &= 0xF8        clear bits 0, 1, 2
 * h[31] &= 0x7F        clear bit 255
 * h[31] |= 0x40        set bit 254
 * ```
 *
 * No external dependencies — SHA-512 is provided by platform APIs
 * (`java.security.MessageDigest` on Android, `CommonCrypto` on iOS).
 *
 * Usage:
 * ```kotlin
 * val secretKeyFileBytes = TorKeyFormat.secretKeyFile(libsodiumSecretKey64Bytes)
 * val publicKeyFileBytes = TorKeyFormat.publicKeyFile(ed25519PublicKey32Bytes)
 * ```
 */
@OptIn(ExperimentalUnsignedTypes::class)
object TorKeyFormat {

    private val SECRET_HEADER: ByteArray =
        "== ed25519v1-secret: type0 ==".encodeToByteArray() + byteArrayOf(0, 0, 0) // 32 bytes

    private val PUBLIC_HEADER: ByteArray =
        "== ed25519v1-public: type0 ==".encodeToByteArray() + byteArrayOf(0, 0, 0) // 32 bytes

    /**
     * Produce the 96-byte content of `hs_ed25519_secret_key` from a libsodium
     * 64-byte secret key (seed‖pubkey layout).
     */
    fun secretKeyFile(libsodiumSecretKey: ByteArray): ByteArray {
        require(libsodiumSecretKey.size == 64) {
            "Expected 64-byte libsodium secret key, got ${libsodiumSecretKey.size}"
        }
        val seed     = libsodiumSecretKey.copyOfRange(0, 32)
        val expanded = expandSeed(seed)
        return SECRET_HEADER + expanded
    }

    /**
     * Produce the 64-byte content of `hs_ed25519_public_key` from a 32-byte Ed25519 public key.
     */
    fun publicKeyFile(ed25519PublicKey: ByteArray): ByteArray {
        require(ed25519PublicKey.size == 32) {
            "Expected 32-byte public key, got ${ed25519PublicKey.size}"
        }
        return PUBLIC_HEADER + ed25519PublicKey
    }

    private fun expandSeed(seed: ByteArray): ByteArray {
        val h = sha512(seed)                              // platform expect
        h[0]  = (h[0].toInt()  and 0xF8).toByte()        // clear bits 0,1,2
        h[31] = (h[31].toInt() and 0x7F).toByte()        // clear bit 255
        h[31] = (h[31].toInt() or  0x40).toByte()        // set   bit 254
        return h
    }
}

/** Platform SHA-512. Android: MessageDigest. iOS: CommonCrypto CC_SHA512. */
internal expect fun sha512(input: ByteArray): ByteArray
