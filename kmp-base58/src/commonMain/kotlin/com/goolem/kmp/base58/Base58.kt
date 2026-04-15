package com.goolem.kmp.base58

/**
 * Base58 encoding (Bitcoin alphabet) — pure Kotlin, no dependencies.
 *
 * The Bitcoin Base58 alphabet omits visually ambiguous characters
 * (0, O, I, l) to reduce human transcription errors.
 *
 * Usage:
 * ```kotlin
 * val encoded: String = Base58.encode(byteArrayOf(1, 2, 3))
 * ```
 */
object Base58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    /** Encode [input] to a Base58 string. Empty input returns an empty string. */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros] == 0.toByte()) zeros++
        val copy    = input.copyOf()
        val encoded = CharArray(copy.size * 2)
        var outputStart = encoded.size
        var inputStart  = zeros
        while (inputStart < copy.size) {
            outputStart--
            encoded[outputStart] = ALPHABET[divmod(copy, inputStart, 256, 58)]
            if (copy[inputStart] == 0.toByte()) inputStart++
        }
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) outputStart++
        repeat(zeros) { outputStart--; encoded[outputStart] = '1' }
        return encoded.concatToString(outputStart, encoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val temp = remainder * base + (number[i].toInt() and 0xFF)
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
