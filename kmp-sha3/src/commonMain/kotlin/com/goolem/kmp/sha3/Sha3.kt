package com.goolem.kmp.sha3

/**
 * Pure Kotlin SHA3-256 (Keccak) implementation — no dependencies.
 *
 * Implements SHA3-256 per FIPS 202 (rate = 1088 bits, capacity = 512 bits, output = 256 bits).
 * Useful in Kotlin Multiplatform projects that need SHA3 without pulling in JVM-only libraries.
 *
 * Usage:
 * ```kotlin
 * val hash: ByteArray = Sha3.sha3_256("hello".encodeToByteArray())
 * ```
 */
object Sha3 {

    private const val RATE          = 136  // 1088 bits / 8
    private const val OUTPUT_LENGTH = 32   // 256 bits

    /** Compute SHA3-256 of [input]. Returns 32 bytes. */
    fun sha3_256(input: ByteArray): ByteArray {
        val state  = LongArray(25)
        val padded = pad(input)

        for (blockStart in padded.indices step RATE) {
            for (i in 0 until RATE / 8) {
                val offset = blockStart + i * 8
                if (offset + 8 <= padded.size) {
                    state[i] = state[i] xor littleEndianToLong(padded, offset)
                }
            }
            keccakF1600(state)
        }

        val output = ByteArray(OUTPUT_LENGTH)
        for (i in 0 until OUTPUT_LENGTH / 8) {
            longToLittleEndian(state[i], output, i * 8)
        }
        return output
    }

    private fun pad(input: ByteArray): ByteArray {
        val blockCount = (input.size / RATE) + 1
        val paddedSize = blockCount * RATE
        val padded     = ByteArray(paddedSize)
        input.copyInto(padded)
        padded[input.size]      = 0x06  // SHA3 domain separation
        padded[paddedSize - 1]  = (padded[paddedSize - 1].toInt() or 0x80).toByte()
        return padded
    }

    private fun keccakF1600(state: LongArray) {
        for (round in 0 until 24) {
            // θ
            val c = LongArray(5) { x -> state[x] xor state[x+5] xor state[x+10] xor state[x+15] xor state[x+20] }
            val d = LongArray(5) { x -> c[(x+4)%5] xor rotateLeft(c[(x+1)%5], 1) }
            for (x in 0 until 5) for (y in 0 until 5) state[x+y*5] = state[x+y*5] xor d[x]

            // ρ + π
            val b = LongArray(25)
            for (x in 0 until 5) for (y in 0 until 5) {
                b[y + ((2*x+3*y)%5)*5] = rotateLeft(state[x+y*5], ROTATION_OFFSETS[x+y*5])
            }

            // χ
            for (x in 0 until 5) for (y in 0 until 5) {
                state[x+y*5] = b[x+y*5] xor (b[(x+1)%5+y*5].inv() and b[(x+2)%5+y*5])
            }

            // ι
            state[0] = state[0] xor ROUND_CONSTANTS[round]
        }
    }

    private fun rotateLeft(value: Long, shift: Int): Long {
        val s = shift % 64
        return if (s == 0) value else (value shl s) or (value ushr (64 - s))
    }

    private fun littleEndianToLong(bytes: ByteArray, offset: Int): Long {
        var r = 0L
        for (i in 0 until 8) r = r or ((bytes[offset+i].toLong() and 0xFF) shl (i*8))
        return r
    }

    private fun longToLittleEndian(value: Long, bytes: ByteArray, offset: Int) {
        for (i in 0 until 8) bytes[offset+i] = (value shr (i*8)).toByte()
    }

    private val ROTATION_OFFSETS = intArrayOf(
         0,  1, 62, 28, 27,
        36, 44,  6, 55, 20,
         3, 10, 43, 25, 39,
        41, 45, 15, 21,  8,
        18,  2, 61, 56, 14
    )

    private val ROUND_CONSTANTS = longArrayOf(
        0x0000000000000001L, 0x0000000000008082L, -0x7FFFFFFFFFFF7F76L, -0x7FFFFFFF7FFF8000L,
        0x000000000000808BL, 0x0000000080000001L, -0x7FFFFFFF7FFF7F7FL, -0x7FFFFFFFFFFF7FF7L,
        0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
        0x000000008000808BL, -0x7FFFFFFFFFFFFF75L, -0x7FFFFFFFFFFF7F77L, -0x7FFFFFFFFFFF7FFdL,
        -0x7FFFFFFFFFFF7FFfL, -0x7FFFFFFFFFFF7F6FL, 0x000000000000800AL, -0x7FFFFFFF7FFF7F7EL,
        -0x7FFFFFFF7FFF7FFdL, -0x7FFFFFFFFFFF7F7EL, 0x0000000080000081L, -0x7FFFFFFF7FFF7FF8L,
    )
}
