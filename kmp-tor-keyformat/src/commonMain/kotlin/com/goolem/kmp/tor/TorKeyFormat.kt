package com.goolem.kmp.tor

import com.goolem.kmp.sha3.Sha3

/**
 * Converts an Ed25519 keypair to the file format used by Tor v3 hidden services
 * and derives the matching `.onion` address.
 *
 * Tor v3 hidden services store their keypair as two files:
 *
 * ```
 * hs_ed25519_secret_key  — 96 bytes: 32-byte header + 64-byte expanded scalar
 * hs_ed25519_public_key  — 64 bytes: 32-byte header + 32-byte public key
 * hostname               — <onion-address>  (e.g. abc...xyz.onion)
 * ```
 *
 * By writing these three files before starting Tor you pin the hidden service
 * identity. The `.onion` address is a deterministic function of the 32-byte
 * Ed25519 public key (see [onionAddress]), so the public key used for
 * application-layer identity can double as the hidden-service identity.
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
 * SHA3-256 for the address checksum is provided by `kmp-sha3`.
 *
 * Usage:
 * ```kotlin
 * val secretKeyFileBytes = TorKeyFormat.secretKeyFile(libsodiumSecretKey64Bytes)
 * val publicKeyFileBytes = TorKeyFormat.publicKeyFile(ed25519PublicKey32Bytes)
 * val hostname           = TorKeyFormat.onionAddress(ed25519PublicKey32Bytes)
 * ```
 */
public object TorKeyFormat {

    private val SECRET_HEADER: ByteArray =
        "== ed25519v1-secret: type0 ==".encodeToByteArray() + byteArrayOf(0, 0, 0) // 32 bytes

    private val PUBLIC_HEADER: ByteArray =
        "== ed25519v1-public: type0 ==".encodeToByteArray() + byteArrayOf(0, 0, 0) // 32 bytes

    private val ONION_CHECKSUM_PREFIX: ByteArray = ".onion checksum".encodeToByteArray()
    private const val ONION_VERSION: Byte = 0x03

    private const val BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

    /**
     * Produce the 96-byte content of `hs_ed25519_secret_key` from a libsodium
     * 64-byte secret key (seed‖pubkey layout).
     *
     * The returned array contains expanded secret scalar material. Callers that
     * want defence-in-depth against memory disclosure should zero it out after
     * writing it to disk using [zeroize].
     */
    public fun secretKeyFile(libsodiumSecretKey: ByteArray): ByteArray {
        require(libsodiumSecretKey.size == 64) {
            "Expected 64-byte libsodium secret key, got ${libsodiumSecretKey.size}"
        }
        val seed = libsodiumSecretKey.copyOfRange(0, 32)
        try {
            val expanded = expandSeed(seed)
            return SECRET_HEADER + expanded
        } finally {
            seed.fill(0)
        }
    }

    /**
     * Produce the 64-byte content of `hs_ed25519_public_key` from a 32-byte Ed25519 public key.
     */
    public fun publicKeyFile(ed25519PublicKey: ByteArray): ByteArray {
        require(ed25519PublicKey.size == 32) {
            "Expected 32-byte public key, got ${ed25519PublicKey.size}"
        }
        return PUBLIC_HEADER + ed25519PublicKey
    }

    /**
     * Derive the Tor v3 `.onion` hostname for the given 32-byte Ed25519 public key.
     *
     * Per tor rend-spec-v3 §6:
     * ```
     * onion_address = base32(PUBKEY || CHECKSUM || VERSION) + ".onion"
     * CHECKSUM      = SHA3-256(".onion checksum" || PUBKEY || VERSION)[:2]
     * VERSION       = 0x03
     * ```
     *
     * The output is lowercase, 56 address characters + ".onion" (62 total).
     */
    public fun onionAddress(ed25519PublicKey: ByteArray): String {
        require(ed25519PublicKey.size == 32) {
            "Expected 32-byte public key, got ${ed25519PublicKey.size}"
        }
        val toHash = ByteArray(ONION_CHECKSUM_PREFIX.size + 32 + 1)
        ONION_CHECKSUM_PREFIX.copyInto(toHash, 0)
        ed25519PublicKey.copyInto(toHash, ONION_CHECKSUM_PREFIX.size)
        toHash[toHash.size - 1] = ONION_VERSION
        val checksum = Sha3.sha3_256(toHash)

        val addr = ByteArray(35)
        ed25519PublicKey.copyInto(addr, 0)
        addr[32] = checksum[0]
        addr[33] = checksum[1]
        addr[34] = ONION_VERSION
        return base32Lower(addr) + ".onion"
    }

    /**
     * Validate that [hostname] (with or without trailing `.onion`) was produced
     * from [ed25519PublicKey]. Useful when loading a Tor identity from disk to
     * confirm the `hostname` file matches `hs_ed25519_public_key`.
     */
    public fun verifyOnionAddress(hostname: String, ed25519PublicKey: ByteArray): Boolean {
        val normalized = hostname.trim().lowercase().removeSuffix(".onion")
        if (normalized.length != 56) return false
        return normalized == onionAddress(ed25519PublicKey).removeSuffix(".onion")
    }

    /**
     * Overwrite [buffer] with zero bytes. Defence-in-depth only — the runtime
     * may have copied the contents elsewhere; callers that truly need to
     * protect secrets should use platform keystores instead of heap buffers.
     */
    public fun zeroize(buffer: ByteArray) {
        buffer.fill(0)
    }

    private fun expandSeed(seed: ByteArray): ByteArray {
        val h = sha512(seed) // platform expect
        h[0] = (h[0].toInt() and 0xF8).toByte() // clear bits 0,1,2
        h[31] = (h[31].toInt() and 0x7F).toByte() // clear bit 255
        h[31] = (h[31].toInt() or 0x40).toByte() // set   bit 254
        return h
    }

    private fun base32Lower(input: ByteArray): String {
        val out = StringBuilder((input.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in input) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer ushr (bitsLeft - 5)) and 0x1F
                out.append(BASE32_ALPHABET[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(BASE32_ALPHABET[idx])
        }
        return out.toString()
    }
}

/**
 * Pure Kotlin SHA-512 (FIPS 180-4). No platform dependencies.
 * Only used for Ed25519 seed expansion (32-byte input → 64-byte output).
 */
internal fun sha512(input: ByteArray): ByteArray {
    val msg = padSha512(input)
    var h0 = 0x6a09e667f3bcc908uL.toLong()
    var h1 = 0xbb67ae8584caa73buL.toLong()
    var h2 = 0x3c6ef372fe94f82buL.toLong()
    var h3 = 0xa54ff53a5f1d36f1uL.toLong()
    var h4 = 0x510e527fade682d1uL.toLong()
    var h5 = 0x9b05688c2b3e6c1fuL.toLong()
    var h6 = 0x1f83d9abfb41bd6buL.toLong()
    var h7 = 0x5be0cd19137e2179uL.toLong()

    val w = LongArray(80)
    for (blockStart in msg.indices step 128) {
        for (t in 0 until 16) {
            var v = 0L
            for (b in 0 until 8) v = (v shl 8) or (msg[blockStart + t * 8 + b].toLong() and 0xFF)
            w[t] = v
        }
        for (t in 16 until 80) {
            w[t] = smallSig1(w[t - 2]) + w[t - 7] + smallSig0(w[t - 15]) + w[t - 16]
        }

        var a = h0; var b = h1; var c = h2; var d = h3
        var e = h4; var f = h5; var g = h6; var h = h7
        for (t in 0 until 80) {
            val t1 = h + bigSig1(e) + ch(e, f, g) + K512[t] + w[t]
            val t2 = bigSig0(a) + maj(a, b, c)
            h = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h0 += a; h1 += b; h2 += c; h3 += d
        h4 += e; h5 += f; h6 += g; h7 += h
    }

    val out = ByteArray(64)
    for ((i, v) in longArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).withIndex()) {
        for (b in 7 downTo 0) out[i * 8 + (7 - b)] = (v ushr (b * 8)).toByte()
    }
    return out
}

private fun padSha512(input: ByteArray): ByteArray {
    val bitLen = input.size.toLong() * 8
    val padLen = (128 - ((input.size + 17) % 128)) % 128
    val msg = ByteArray(input.size + 1 + padLen + 16)
    input.copyInto(msg)
    msg[input.size] = 0x80.toByte()
    for (b in 0 until 8) msg[msg.size - 1 - b] = (bitLen ushr (b * 8)).toByte()
    return msg
}

private fun Long.rotr(n: Int): Long = (this ushr n) or (this shl (64 - n))
private fun ch(x: Long, y: Long, z: Long): Long = (x and y) xor (x.inv() and z)
private fun maj(x: Long, y: Long, z: Long): Long = (x and y) xor (x and z) xor (y and z)
private fun bigSig0(x: Long): Long = x.rotr(28) xor x.rotr(34) xor x.rotr(39)
private fun bigSig1(x: Long): Long = x.rotr(14) xor x.rotr(18) xor x.rotr(41)
private fun smallSig0(x: Long): Long = x.rotr(1) xor x.rotr(8) xor (x ushr 7)
private fun smallSig1(x: Long): Long = x.rotr(19) xor x.rotr(61) xor (x ushr 6)

@OptIn(ExperimentalUnsignedTypes::class)
private val K512: LongArray = ulongArrayOf(
    0x428a2f98d728ae22uL, 0x7137449123ef65cduL, 0xb5c0fbcfec4d3b2fuL, 0xe9b5dba58189dbbcuL,
    0x3956c25bf348b538uL, 0x59f111f1b605d019uL, 0x923f82a4af194f9buL, 0xab1c5ed5da6d8118uL,
    0xd807aa98a3030242uL, 0x12835b0145706fbeuL, 0x243185be4ee4b28cuL, 0x550c7dc3d5ffb4e2uL,
    0x72be5d74f27b896fuL, 0x80deb1fe3b1696b1uL, 0x9bdc06a725c71235uL, 0xc19bf174cf692694uL,
    0xe49b69c19ef14ad2uL, 0xefbe4786384f25e3uL, 0x0fc19dc68b8cd5b5uL, 0x240ca1cc77ac9c65uL,
    0x2de92c6f592b0275uL, 0x4a7484aa6ea6e483uL, 0x5cb0a9dcbd41fbd4uL, 0x76f988da831153b5uL,
    0x983e5152ee66dfabuL, 0xa831c66d2db43210uL, 0xb00327c898fb213fuL, 0xbf597fc7beef0ee4uL,
    0xc6e00bf33da88fc2uL, 0xd5a79147930aa725uL, 0x06ca6351e003826fuL, 0x142929670a0e6e70uL,
    0x27b70a8546d22ffcuL, 0x2e1b21385c26c926uL, 0x4d2c6dfc5ac42aeduL, 0x53380d139d95b3dfuL,
    0x650a73548baf63deuL, 0x766a0abb3c77b2a8uL, 0x81c2c92e47edaee6uL, 0x92722c851482353buL,
    0xa2bfe8a14cf10364uL, 0xa81a664bbc423001uL, 0xc24b8b70d0f89791uL, 0xc76c51a30654be30uL,
    0xd192e819d6ef5218uL, 0xd69906245565a910uL, 0xf40e35855771202auL, 0x106aa07032bbd1b8uL,
    0x19a4c116b8d2d0c8uL, 0x1e376c085141ab53uL, 0x2748774cdf8eeb99uL, 0x34b0bcb5e19b48a8uL,
    0x391c0cb3c5c95a63uL, 0x4ed8aa4ae3418acbuL, 0x5b9cca4f7763e373uL, 0x682e6ff3d6b2b8a3uL,
    0x748f82ee5defb2fcuL, 0x78a5636f43172f60uL, 0x84c87814a1f0ab72uL, 0x8cc702081a6439ecuL,
    0x90befffa23631e28uL, 0xa4506cebde82bde9uL, 0xbef9a3f7b2c67915uL, 0xc67178f2e372532buL,
    0xca273eceea26619cuL, 0xd186b8c721c0c207uL, 0xeada7dd6cde0eb1euL, 0xf57d4f7fee6ed178uL,
    0x06f067aa72176fbauL, 0x0a637dc5a2c898a6uL, 0x113f9804bef90daeuL, 0x1b710b35131c471buL,
    0x28db77f523047d84uL, 0x32caab7b40c72493uL, 0x3c9ebe0a15c9bebcuL, 0x431d67c49c100d4cuL,
    0x4cc5d4becb3e42b6uL, 0x597f299cfc657e2auL, 0x5fcb6fab3ad6faecuL, 0x6c44198c4a475817uL,
).asLongArray()
