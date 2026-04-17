package com.goolem.kmp.tor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class TorKeyFormatTest {

    @Test
    fun secretKeyFileSize() {
        val sk = ByteArray(64) { it.toByte() }
        val file = TorKeyFormat.secretKeyFile(sk)
        assertEquals(96, file.size)
    }

    @Test
    fun secretKeyFileHeader() {
        val sk = ByteArray(64)
        val file = TorKeyFormat.secretKeyFile(sk)
        val header = file.copyOfRange(0, 32).decodeToString()
        assertEquals("== ed25519v1-secret: type0 ==\u0000\u0000\u0000", header)
    }

    @Test
    fun publicKeyFileSize() {
        val pk = ByteArray(32) { it.toByte() }
        val file = TorKeyFormat.publicKeyFile(pk)
        assertEquals(64, file.size)
    }

    @Test
    fun publicKeyFileHeader() {
        val pk = ByteArray(32)
        val file = TorKeyFormat.publicKeyFile(pk)
        val header = file.copyOfRange(0, 32).decodeToString()
        assertEquals("== ed25519v1-public: type0 ==\u0000\u0000\u0000", header)
    }

    @Test
    fun publicKeyPreserved() {
        val pk = ByteArray(32) { (it + 42).toByte() }
        val file = TorKeyFormat.publicKeyFile(pk)
        val embedded = file.copyOfRange(32, 64)
        assertEquals(pk.toList(), embedded.toList())
    }

    @Test
    fun onionAddressLength() {
        val pk = ByteArray(32) { it.toByte() }
        val addr = TorKeyFormat.onionAddress(pk)
        assertTrue(addr.endsWith(".onion"))
        assertEquals(62, addr.length) // 56 base32 + ".onion"
    }

    @Test
    fun onionAddressDeterministic() {
        val pk = ByteArray(32) { 0xAB.toByte() }
        val addr1 = TorKeyFormat.onionAddress(pk)
        val addr2 = TorKeyFormat.onionAddress(pk)
        assertEquals(addr1, addr2)
    }

    @Test
    fun onionAddressLowercase() {
        val pk = ByteArray(32) { 0xFF.toByte() }
        val addr = TorKeyFormat.onionAddress(pk)
        assertEquals(addr, addr.lowercase())
    }

    @Test
    fun verifyOnionAddressMatchesDerived() {
        val pk = ByteArray(32) { (it * 7).toByte() }
        val addr = TorKeyFormat.onionAddress(pk)
        assertTrue(TorKeyFormat.verifyOnionAddress(addr, pk))
    }

    @Test
    fun verifyOnionAddressRejectsMismatch() {
        val pk1 = ByteArray(32) { 0x01 }
        val pk2 = ByteArray(32) { 0x02 }
        val addr = TorKeyFormat.onionAddress(pk1)
        assertFalse(TorKeyFormat.verifyOnionAddress(addr, pk2))
    }

    @Test
    fun wrongSizeSecretKeyThrows() {
        assertFailsWith<IllegalArgumentException> {
            TorKeyFormat.secretKeyFile(ByteArray(32))
        }
    }

    @Test
    fun wrongSizePublicKeyThrows() {
        assertFailsWith<IllegalArgumentException> {
            TorKeyFormat.publicKeyFile(ByteArray(16))
        }
    }

    @Test
    fun zeroize() {
        val buf = ByteArray(32) { 0xFF.toByte() }
        TorKeyFormat.zeroize(buf)
        assertTrue(buf.all { it == 0.toByte() })
    }
}
