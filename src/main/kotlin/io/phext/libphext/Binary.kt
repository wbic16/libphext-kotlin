package io.phext.libphext

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Binary scrolls: the canonical way to store arbitrary bytes inside phext,
 * mirroring libphext-rs 0.6.0 `src/binary.rs`.
 *
 * A binary scroll is text of the form:
 * ```
 * phext-binary:1:base64
 * <RFC 4648 base64 body, standard alphabet, padding required, no wrapping>
 * ```
 *
 * The ingestion rule: when non-UTF-8 data is observed in an incoming scroll
 * update, it is encoded with base64 verbatim. Byte-oriented entry points
 * ([insertBytes], [replaceBytes], [ingest]) never reject and never fail on
 * non-UTF-8 scroll data.
 *
 * Base64 is `java.util.Base64` (basic encoder/decoder), with an explicit
 * strictness check in [Binary.decodeScroll] so that bodies Rust's
 * `base64` STANDARD engine would reject (missing padding, whitespace,
 * foreign characters) are rejected here too.
 */

/** A scroll's raw bytes together with its coordinate (the byte-level phoken). */
public data class PositionedByteScroll(
    public val coord: Coordinate,
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PositionedByteScroll) return false
        return coord == other.coord && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * coord.hashCode() + bytes.contentHashCode()
}

public object Binary {

    /** The first (and only header) line of a binary scroll, without its line break. */
    public const val BINARY_SCROLL_HEADER: String = "phext-binary:1:base64"

    private const val BINARY_SCROLL_PREFIX: String = "phext-binary:1:base64\n"

    /** True when `scroll` carries the binary scroll header line. */
    @JvmStatic
    public fun isBinaryScroll(scroll: String): Boolean =
        scroll.startsWith(BINARY_SCROLL_PREFIX)

    /** Encodes raw bytes as a binary scroll (header line + base64 body). */
    @JvmStatic
    public fun encodeScroll(payload: ByteArray): String =
        BINARY_SCROLL_PREFIX + java.util.Base64.getEncoder().encodeToString(payload)

    /**
     * Decodes a binary scroll back to its raw bytes. Returns null when the
     * scroll is not a binary scroll, or when its body is not strict base64.
     */
    @JvmStatic
    public fun decodeScroll(scroll: String): ByteArray? {
        if (!isBinaryScroll(scroll)) return null
        val body = scroll.substring(BINARY_SCROLL_PREFIX.length)
        if (!isStrictBase64(body)) return null
        return try {
            java.util.Base64.getDecoder().decode(body)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun isStrictBase64(body: String): Boolean {
        // RFC 4648 with padding required and no whitespace — the acceptance
        // set of Rust base64 0.22's STANDARD engine. (java.util.Base64 alone
        // tolerates a missing final padding, so it is checked explicitly.)
        if (body.length % 4 != 0) return false
        var padding = 0
        for (i in body.indices) {
            val c = body[i]
            if (c == '=') {
                padding += 1
                if (padding > 2) return false
            } else {
                if (padding > 0) return false
                val ok = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '+' || c == '/'
                if (!ok) return false
            }
        }
        return true
    }

    /**
     * Stores `payload` at `location` as a binary scroll, unconditionally:
     * the caller has declared this scroll binary, even if the bytes happen
     * to be valid UTF-8.
     */
    @JvmStatic
    public fun insertBinary(input: String, location: Coordinate, payload: ByteArray): String =
        insert(input, location, encodeScroll(payload))

    /** Replaces the scroll at `location` with `payload`, stored as a binary scroll. */
    @JvmStatic
    public fun replaceBinary(input: String, location: Coordinate, payload: ByteArray): String =
        replace(input, location, encodeScroll(payload))

    /**
     * Retrieves the bytes held at `target`. A well-formed binary scroll
     * decodes to its payload; any other scroll yields its own UTF-8 bytes
     * (empty when the coordinate is unoccupied). Total: never fails.
     */
    @JvmStatic
    public fun fetchBinary(input: String, target: Coordinate): ByteArray {
        val scroll = fetch(input, target)
        return decodeScroll(scroll) ?: scroll.toByteArray(Charsets.UTF_8)
    }

    private fun storedForm(payload: ByteArray): String =
        decodeUtf8Strict(payload) ?: encodeScroll(payload)

    /** Byte-oriented insert under the ingestion rule (see class docs). */
    @JvmStatic
    public fun insertBytes(input: String, location: Coordinate, payload: ByteArray): String =
        insert(input, location, storedForm(payload))

    /** Byte-oriented replace under the ingestion rule (see class docs). */
    @JvmStatic
    public fun replaceBytes(input: String, location: Coordinate, payload: ByteArray): String =
        replace(input, location, storedForm(payload))

    /**
     * The byte-level twin of [phokenize]: walks a raw byte stream and
     * returns each scroll's coordinate and raw bytes, with identical
     * segmentation. Scroll bodies are returned uninterpreted.
     */
    @JvmStatic
    public fun phokenizeBytes(input: ByteArray): List<PositionedByteScroll> {
        val result = ArrayList<PositionedByteScroll>()
        val max = input.size
        val coord = defaultCoordinate()
        var pi = 0
        while (true) {
            val start = coord.deepCopy()
            var begin = start
            var textStart: Int? = null
            var textEnd = max
            while (pi < max) {
                val byte = input[pi].toInt() and 0xFF
                if (isStructuralBreak(byte)) {
                    applyBreakBytes(coord, byte)
                    if (textStart != null) {
                        textEnd = pi
                        pi += 1
                        break
                    }
                } else if (textStart == null) {
                    textStart = pi
                    begin = coord.deepCopy()
                }
                pi += 1
            }
            val first = textStart
            val scroll = if (first != null) {
                input.copyOfRange(first, textEnd)
            } else {
                ByteArray(0)
            }
            result.add(PositionedByteScroll(begin, scroll))
            if (pi >= max) break
        }
        return result
    }

    /**
     * Ingests an externally supplied phext byte stream. Valid UTF-8 scrolls
     * pass through verbatim; any scroll whose bytes are not valid UTF-8 is
     * stored as a binary scroll holding the base64 of the exact observed
     * bytes. Never rejects. The result is the canonical serialization.
     */
    @JvmStatic
    public fun ingest(bytes: ByteArray): String {
        val parts = phokenizeBytes(bytes)
        val map = HashMap<Coordinate, String>()
        for (part in parts) {
            map[part.coord] = decodeUtf8Strict(part.bytes) ?: encodeScroll(part.bytes)
        }
        return implode(map)
    }

    private fun isStructuralBreak(byte: Int): Boolean =
        byte == SCROLL_BREAK_U8 || byte == SECTION_BREAK_U8 || byte == CHAPTER_BREAK_U8 ||
            byte == BOOK_BREAK_U8 || byte == VOLUME_BREAK_U8 || byte == COLLECTION_BREAK_U8 ||
            byte == SERIES_BREAK_U8 || byte == SHELF_BREAK_U8 || byte == LIBRARY_BREAK_U8

    private fun applyBreakBytes(location: Coordinate, byte: Int) {
        when (byte) {
            SCROLL_BREAK_U8 -> location.scrollBreak()
            SECTION_BREAK_U8 -> location.sectionBreak()
            CHAPTER_BREAK_U8 -> location.chapterBreak()
            BOOK_BREAK_U8 -> location.bookBreak()
            VOLUME_BREAK_U8 -> location.volumeBreak()
            COLLECTION_BREAK_U8 -> location.collectionBreak()
            SERIES_BREAK_U8 -> location.seriesBreak()
            SHELF_BREAK_U8 -> location.shelfBreak()
            LIBRARY_BREAK_U8 -> location.libraryBreak()
        }
    }
}

/** Strict UTF-8 decode: the string, or null when the bytes are not valid UTF-8. */
internal fun decodeUtf8Strict(bytes: ByteArray): String? {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        null
    }
}
