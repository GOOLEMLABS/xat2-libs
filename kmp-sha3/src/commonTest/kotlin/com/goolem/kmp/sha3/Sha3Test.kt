package com.goolem.kmp.sha3

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIST SHA3-256 Known Answer Tests (KAT) — from
 * https://csrc.nist.gov/projects/cryptographic-algorithm-validation-program/Secure-Hashing
 *
 * These vectors protect the `rotateLeft`, padding, round constants, and
 * state-mixing pipeline against regressions.
 */
class Sha3Test {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun emptyInput() {
        assertEquals(
            "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a",
            hex(Sha3.sha3_256(ByteArray(0)))
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532",
            hex(Sha3.sha3_256("abc".encodeToByteArray()))
        )
    }

    @Test
    fun lengthMedium() {
        // NIST vector: 448-bit message
        val msg = ("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
            .encodeToByteArray()
        assertEquals(
            "41c0dba2a9d6240849100376a8235e2c82e1b9998a999e21db32dd97496d3376",
            hex(Sha3.sha3_256(msg))
        )
    }

    @Test
    fun lengthLong() {
        // NIST vector: 896-bit message
        val msg = ("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmn" +
                   "hijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu")
            .encodeToByteArray()
        assertEquals(
            "916f6061fe879741ca6469b43971dfdb28b1a32dc36cb3254e812be27aad1d18",
            hex(Sha3.sha3_256(msg))
        )
    }

    @Test
    fun oneMillionA() {
        val msg = ByteArray(1_000_000) { 'a'.code.toByte() }
        assertEquals(
            "5c8875ae474a3634ba4fd55ec85bffd661f32aca75c6d699d0cdcb6c115891c1",
            hex(Sha3.sha3_256(msg))
        )
    }

    @Test
    fun blockBoundary() {
        // Exactly one RATE-sized block (136 bytes) exercises padding edge.
        val msg = ByteArray(136) { 0x61 }
        val digest = Sha3.sha3_256(msg)
        assertEquals(32, digest.size)
    }
}
