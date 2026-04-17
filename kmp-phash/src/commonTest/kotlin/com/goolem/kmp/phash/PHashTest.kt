package com.goolem.kmp.phash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PHashTest {

    @Test
    fun computeReturnsLong() {
        val gray = ByteArray(32 * 32) { 128.toByte() }
        val hash = PHash.compute(gray)
        // Uniform image → all DCT coefficients similar → hash depends on mean
        assertTrue(hash != Long.MIN_VALUE)
    }

    @Test
    fun identicalInputsProduceSameHash() {
        val gray = ByteArray(32 * 32) { it.toByte() }
        assertEquals(PHash.compute(gray), PHash.compute(gray.copyOf()))
    }

    @Test
    fun hammingDistanceSelf() {
        assertEquals(0, PHash.hammingDistance(0x1234L, 0x1234L))
    }

    @Test
    fun hammingDistanceOpposite() {
        assertEquals(64, PHash.hammingDistance(0L, -1L))
    }

    @Test
    fun isSimilarSelf() {
        assertTrue(PHash.isSimilar(42L, 42L))
    }

    @Test
    fun wrongSizeThrows() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PHash.compute(ByteArray(100))
        }
    }

    @Test
    fun contentScannerAllowsWhenEmpty() {
        val scanner = ContentScanner()
        val result = scanner.scan(ByteArray(0))
        assertTrue(result is ContentScanner.Result.Allowed)
    }

    @Test
    fun hashBlocklistEmpty() {
        val bl = HashBlocklist()
        assertEquals(0, bl.size)
        assertEquals(null, bl.findMatch(0L))
    }

    @Test
    fun hashBlocklistExactMatch() {
        val bl = HashBlocklist()
        bl.addHash(0x1234L, "test")
        assertEquals(1, bl.size)
        val match = bl.findMatch(0x1234L)
        assertEquals("test", match?.label)
    }
}
