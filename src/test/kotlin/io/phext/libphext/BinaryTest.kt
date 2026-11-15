package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of libphext-rs `src/test_binary.rs` (10 tests). */
class BinaryTest {

    private fun coord(address: String): Coordinate = toCoordinate(address)

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    @Test
    fun testEncodeDecodeRoundTrip() {
        // empty payload: header line alone
        val empty = ByteArray(0)
        val emptyScroll = Binary.encodeScroll(empty)
        assertEquals("phext-binary:1:base64\n", emptyScroll)
        assertArrayEquals(empty, Binary.decodeScroll(emptyScroll))

        // every byte value
        val allBytes = ByteArray(256) { it.toByte() }
        val scroll = Binary.encodeScroll(allBytes)
        assertTrue(Binary.isBinaryScroll(scroll))
        assertArrayEquals(allBytes, Binary.decodeScroll(scroll))

        // a plainly non-UTF-8 sequence
        val foreign = bytes(0xFF, 0xFE, 0x80, 0x00, 0x61, 0xC3, 0x28)
        val foreignScroll = Binary.encodeScroll(foreign)
        assertArrayEquals(foreign, Binary.decodeScroll(foreignScroll))

        // >= 1 MiB payload, deterministic pattern covering all byte values
        val big = ByteArray(1024 * 1024 + 13) { (it % 256).toByte() }
        val bigScroll = Binary.encodeScroll(big)
        assertArrayEquals(big, Binary.decodeScroll(bigScroll))
    }

    @Test
    fun testDecodeScrollRejectsNonBinary() {
        assertNull(Binary.decodeScroll("just some text"))
        assertNull(Binary.decodeScroll(""))
        // header must be a complete line: no line break, no binary scroll
        assertNull(Binary.decodeScroll("phext-binary:1:base64"))
        assertFalse(Binary.isBinaryScroll("phext-binary:1:base64"))
        // malformed base64 body: recognisably binary, undecodable
        val malformed = "phext-binary:1:base64\n!!! not base64 !!!"
        assertTrue(Binary.isBinaryScroll(malformed))
        assertNull(Binary.decodeScroll(malformed))
    }

    @Test
    fun testInsertAndFetchBinary() {
        val target = coord("1.1.1/1.1.1/1.1.2")
        val payload = bytes(0x00, 0x01, 0x02, 0xFE, 0xFF, 0x7F, 0x80)

        var doc = insert("", coord("1.1.1/1.1.1/1.1.1"), "plain text scroll")
        doc = Binary.insertBinary(doc, target, payload)

        // the text scroll next door is untouched
        assertEquals("plain text scroll", fetch(doc, coord("1.1.1/1.1.1/1.1.1")))
        // the binary scroll fetches back byte-identical
        assertArrayEquals(payload, Binary.fetchBinary(doc, target))
        // and its stored form is the canonical binary scroll
        val stored = fetch(doc, target)
        assertTrue(Binary.isBinaryScroll(stored))
        assertArrayEquals(payload, Binary.decodeScroll(stored))
    }

    @Test
    fun testFetchBinaryFallbacks() {
        val doc = insert("", coord("1.1.1/1.1.1/1.1.1"), "hello")
        // a plain text scroll yields its own UTF-8 bytes
        assertArrayEquals("hello".toByteArray(Charsets.UTF_8), Binary.fetchBinary(doc, coord("1.1.1/1.1.1/1.1.1")))
        // an unoccupied coordinate yields no bytes (mirrors fetch returning "")
        assertArrayEquals(ByteArray(0), Binary.fetchBinary(doc, coord("9.9.9/9.9.9/9.9.9")))
        // a malformed binary scroll yields its own bytes verbatim, not a panic
        val doc2 = insert("", coord("1.1.1/1.1.1/1.1.1"), "phext-binary:1:base64\n!!!")
        assertArrayEquals(
            "phext-binary:1:base64\n!!!".toByteArray(Charsets.UTF_8),
            Binary.fetchBinary(doc2, coord("1.1.1/1.1.1/1.1.1")),
        )
    }

    @Test
    fun testUpdateRuleNonUtf8IsBase64Verbatim() {
        val target = coord("1.1.1/1.1.1/1.1.1")
        // payload deliberately includes dimension-break bytes (0x17, 0x1C):
        // armoured as base64, they round-trip as content, not structure
        val payload = bytes(0xFF, 0x17, 0x1C, 0x80, 0x00)
        val doc = Binary.replaceBytes("", target, payload)

        val stored = fetch(doc, target)
        assertTrue(Binary.isBinaryScroll(stored), "non-UTF-8 update must be stored as a binary scroll")
        assertArrayEquals(payload, Binary.fetchBinary(doc, target))

        // same rule through the insert entry point
        val doc2 = Binary.insertBytes("", target, payload)
        assertArrayEquals(payload, Binary.fetchBinary(doc2, target))
    }

    @Test
    fun testUpdateRuleUtf8StaysText() {
        val target = coord("1.1.1/1.1.1/1.1.1")
        val doc = Binary.replaceBytes("", target, "ordinary text ✓".toByteArray(Charsets.UTF_8))
        assertEquals("ordinary text ✓", fetch(doc, target))
        assertFalse(Binary.isBinaryScroll(fetch(doc, target)))
        assertArrayEquals("ordinary text ✓".toByteArray(Charsets.UTF_8), Binary.fetchBinary(doc, target))
    }

    @Test
    fun testIngestForeignByteStream() {
        // a phext byte stream whose second scroll is not valid UTF-8
        val stream = "hello".toByteArray(Charsets.UTF_8) +
            byteArrayOf(SCROLL_BREAK_U8.toByte()) +
            bytes(0xFF, 0xFE, 0x41, 0x80)

        val doc = Binary.ingest(stream)

        // text scroll passes through verbatim
        assertEquals("hello", fetch(doc, coord("1.1.1/1.1.1/1.1.1")))
        // foreign scroll: stored as a binary scroll, reads back as the original bytes
        val foreignAt = coord("1.1.1/1.1.1/1.1.2")
        assertTrue(Binary.isBinaryScroll(fetch(doc, foreignAt)))
        assertArrayEquals(bytes(0xFF, 0xFE, 0x41, 0x80), Binary.fetchBinary(doc, foreignAt))
    }

    @Test
    fun testIngestCanonicalPhextIsIdentity() {
        var doc = insert("", coord("1.1.1/1.1.1/1.1.1"), "first")
        doc = insert(doc, coord("1.1.1/1.1.1/1.1.2"), "second ✓ scroll")
        doc = insert(doc, coord("2.1.1/1.3.1/1.1.1"), "far away")
        assertEquals(doc, Binary.ingest(doc.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun testIngestEmptyAndBreakOnlyStreams() {
        // no panic, no content
        val doc = Binary.ingest(ByteArray(0))
        assertEquals("", fetch(doc, coord("1.1.1/1.1.1/1.1.1")))
        val doc2 = Binary.ingest(byteArrayOf(SCROLL_BREAK_U8.toByte(), SECTION_BREAK_U8.toByte()))
        assertEquals("", fetch(doc2, coord("1.1.1/1.1.1/1.1.1")))
    }

    @Test
    fun testPhokenizeBytesMatchesPhokenize() {
        val doc = "alpha\u0017beta\u0018gamma"
        val textParts = phokenize(doc)
        val byteParts = Binary.phokenizeBytes(doc.toByteArray(Charsets.UTF_8))
        assertEquals(textParts.size, byteParts.size)
        for ((textPart, bytePart) in textParts.zip(byteParts)) {
            assertEquals(textPart.coord, bytePart.coord)
            assertArrayEquals(textPart.scroll.toByteArray(Charsets.UTF_8), bytePart.bytes)
        }
    }
}
