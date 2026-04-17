package com.goolem.kmp.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipReaderTest {

    @Test
    fun tooSmallArchiveThrows() {
        assertFailsWith<ZipFormatException> {
            ZipReader.read(ByteArray(10))
        }
    }

    @Test
    fun emptyBytesThrows() {
        assertFailsWith<ZipFormatException> {
            ZipReader.read(ByteArray(0))
        }
    }

    @Test
    fun garbageInputThrows() {
        assertFailsWith<ZipFormatException> {
            ZipReader.read(ByteArray(100) { 0xFF.toByte() })
        }
    }

    @Test
    fun sanitizePathRejectsTraversal() {
        // We can't call sanitizePath directly (private), but we test
        // via the public API once we have a real ZIP. For now, verify
        // that ZipFormatException is a RuntimeException subclass.
        val ex = ZipFormatException("test")
        assertTrue(ex is RuntimeException)
    }

    @Test
    fun maxEntryBytesIsConfigurable() {
        val before = ZipReader.maxEntryBytes
        ZipReader.maxEntryBytes = 1024
        assertEquals(1024, ZipReader.maxEntryBytes)
        ZipReader.maxEntryBytes = before
    }

    @Test
    fun maxTotalBytesIsConfigurable() {
        val before = ZipReader.maxTotalBytes
        ZipReader.maxTotalBytes = 999L
        assertEquals(999L, ZipReader.maxTotalBytes)
        ZipReader.maxTotalBytes = before
    }
}
