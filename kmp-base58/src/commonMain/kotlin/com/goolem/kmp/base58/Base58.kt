package com.goolem.kmp.base58

/**
 * Base58 encoding and decoding (Bitcoin alphabet) — pure Kotlin, no dependencies.
 *
 * The Bitcoin Base58 alphabet omits visually ambiguous characters
 * (0, O, I, l) to reduce human transcription errors.
 *
 * Usage:
 * ```kotlin
 * val encoded: String = Base58.encode(byteArrayOf(1, 2, 3))
 * val decoded: ByteArray = Base58.decode(encoded)
 * ```
 */
public object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private val INDEXES = IntArray(128) { -1 }.also { arr ->
        for ((i, c) in ALPHABET.withIndex()) arr[c.code] = i
    }

    /** Encode [input] to a Base58 string. Empty input returns an empty string. */
    public fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros] == 0.toByte()) zeros++
        val copy = input.copyOf()
        val encoded = CharArray(copy.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        while (inputStart < copy.size) {
            outputStart--
            encoded[outputStart] = ALPHABET[divmod(copy, inputStart, 256, 58)]
            if (copy[inputStart] == 0.toByte()) inputStart++
        }
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) outputStart++
        repeat(zeros) { outputStart--; encoded[outputStart] = '1' }
        return encoded.concatToString(outputStart, encoded.size)
    }

    /**
     * Decode a Base58 string back to bytes.
     *
     * @throws IllegalArgumentException if [encoded] contains characters outside the alphabet.
     */
    public fun decode(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)
        var zeros = 0
        while (zeros < encoded.length && encoded[zeros] == '1') zeros++

        val input = ByteArray(encoded.length)
        for ((i, c) in encoded.withIndex()) {
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid Base58 character: '$c' at position $i" }
            input[i] = digit.toByte()
        }

        val decoded = ByteArray(encoded.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input.size) {
            outputStart--
            decoded[outputStart] = divmod(input, inputStart, 58, 256).toByte()
            if (input[inputStart] == 0.toByte()) inputStart++
        }
        while (outputStart < decoded.size && decoded[outputStart] == 0.toByte()) outputStart++
        val leading = ByteArray(zeros)
        return leading + decoded.copyOfRange(outputStart, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder.toLong() * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = (temp % divisor).toInt()
        }
        return remainder
    }
}
