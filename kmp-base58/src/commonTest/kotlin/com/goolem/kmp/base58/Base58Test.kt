package com.goolem.kmp.base58

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base58Test {

    @Test
    fun emptyRoundtrip() {
        assertEquals("", Base58.encode(ByteArray(0)))
        assertEquals(0, Base58.decode("").size)
    }

    @Test
    fun singleByte() {
        val input = byteArrayOf(0x61)
        val encoded = Base58.encode(input)
        val decoded = Base58.decode(encoded)
        assertEquals(input.toList(), decoded.toList())
    }

    @Test
    fun leadingZeros() {
        val input = byteArrayOf(0, 0, 0, 1)
        val encoded = Base58.encode(input)
        assertEquals("1112", encoded)
        val decoded = Base58.decode(encoded)
        assertEquals(input.toList(), decoded.toList())
    }

    @Test
    fun bitcoinVector() {
        // "Hello World" → Base58
        val input = "Hello World".encodeToByteArray()
        val encoded = Base58.encode(input)
        assertEquals("JxF12TrwUP45BMd", encoded)
        val decoded = Base58.decode(encoded)
        assertEquals("Hello World", decoded.decodeToString())
    }

    @Test
    fun allZeros() {
        val input = ByteArray(5)
        val encoded = Base58.encode(input)
        assertEquals("11111", encoded)
        val decoded = Base58.decode(encoded)
        assertEquals(input.toList(), decoded.toList())
    }

    @Test
    fun hexVector() {
        // 0x0001 → "12"
        val input = byteArrayOf(0x00, 0x01)
        val encoded = Base58.encode(input)
        assertEquals("12", encoded)
        assertEquals(input.toList(), Base58.decode(encoded).toList())
    }

    @Test
    fun largePayloadRoundtrip() {
        val input = ByteArray(64) { it.toByte() }
        val encoded = Base58.encode(input)
        val decoded = Base58.decode(encoded)
        assertEquals(input.toList(), decoded.toList())
    }

    @Test
    fun invalidCharacterThrows() {
        assertFailsWith<IllegalArgumentException> {
            Base58.decode("Invalid0Char")
        }
    }
}
