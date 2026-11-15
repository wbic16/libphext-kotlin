package io.phext.libphext

/**
 * Core phext operations, mirroring libphext-rs 0.6.0 `src/phext.rs`.
 *
 * Phext is composable, relational text: scrolls of plain text addressed in a
 * 9-dimensional hierarchy by ASCII control-code dimension breaks. All
 * functions here operate on the phext's UTF-8 bytes internally, exactly as
 * the reference does, so offsets and segmentation are byte-exact.
 */

// ---------------------------------------------------------------------------
// phext constants (char forms)
// ---------------------------------------------------------------------------
public const val LIBRARY_BREAK: Char = '\u0001'    // 11th dimension
public const val CHAIN_BREAK: Char = '\u0002'      // stream level: separates whole phexts in a chain
public const val CHAIN_CLOSE: Char = '\u0003'      // stream level: terminates a chain
public const val MORE_COWBELL: Char = '\u0007'
public const val LINE_BREAK: Char = '\u000A'
public const val SCROLL_BREAK: Char = '\u0017'     // 3D break
public const val SECTION_BREAK: Char = '\u0018'    // 4D break
public const val CHAPTER_BREAK: Char = '\u0019'    // 5D break
public const val BOOK_BREAK: Char = '\u001A'       // 6D break
public const val VOLUME_BREAK: Char = '\u001C'     // 7D break
public const val COLLECTION_BREAK: Char = '\u001D' // 8D break
public const val SERIES_BREAK: Char = '\u001E'     // 9D break
public const val SHELF_BREAK: Char = '\u001F'      // 10D break

// byte forms of the char constants above
public const val LIBRARY_BREAK_U8: Int = 0x01
public const val CHAIN_BREAK_U8: Int = 0x02
public const val CHAIN_CLOSE_U8: Int = 0x03
public const val MORE_COWBELL_U8: Int = 0x07
public const val LINE_BREAK_U8: Int = 0x0A
public const val SCROLL_BREAK_U8: Int = 0x17
public const val SECTION_BREAK_U8: Int = 0x18
public const val CHAPTER_BREAK_U8: Int = 0x19
public const val BOOK_BREAK_U8: Int = 0x1A
public const val VOLUME_BREAK_U8: Int = 0x1C
public const val COLLECTION_BREAK_U8: Int = 0x1D
public const val SERIES_BREAK_U8: Int = 0x1E
public const val SHELF_BREAK_U8: Int = 0x1F

public const val ADDRESS_MICRO_BREAK: Int = 0x2E // '.'
public const val ADDRESS_MACRO_BREAK: Int = 0x2F // '/'
public const val ADDRESS_MACRO_ALT: Int = 0x3B   // ';' (url encoding)

private fun ByteArray.utf8String(offset: Int, length: Int): String =
    String(this, offset, length, Charsets.UTF_8)

private fun isDimensionBreak(byte: Int): Boolean =
    byte == SCROLL_BREAK_U8 || byte == SECTION_BREAK_U8 || byte == CHAPTER_BREAK_U8 ||
        byte == BOOK_BREAK_U8 || byte == VOLUME_BREAK_U8 || byte == COLLECTION_BREAK_U8 ||
        byte == SERIES_BREAK_U8 || byte == SHELF_BREAK_U8 || byte == LIBRARY_BREAK_U8

private fun applyBreak(location: Coordinate, byte: Int) {
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

/** True when the input contains a MORE_COWBELL (0x07) byte. */
public fun checkForCowbell(input: String): Boolean {
    for (b in input.toByteArray(Charsets.UTF_8)) {
        if ((b.toInt() and 0xFF) == MORE_COWBELL_U8) return true
    }
    return false
}

/** Result of [getSubspaceCoordinates]: byte range plus the coordinate in effect there. */
public data class SubspaceCoordinates(
    public val start: Int,
    public val end: Int,
    public val best: Coordinate,
)

/**
 * Finds the start and end byte offsets for the given coordinate.
 *
 * When the document reaches `target`, `start..end` is that scroll's text
 * (empty if it has none) and `best` is `target`; otherwise `start == end` is
 * where the scroll would go and `best` is the coordinate in effect there.
 * (libphext-rs `get_subspace_coordinates`, ported line for line.)
 */
public fun getSubspaceCoordinates(subspace: ByteArray, target: Coordinate): SubspaceCoordinates {
    val walker = defaultCoordinate()
    var best = defaultCoordinate()
    var found: Int? = null
    val max = subspace.size
    var position = 0
    var moved = true

    while (true) {
        if (moved && found == null) {
            if (walker == target) {
                found = position
                best = walker.deepCopy()
            } else if (walker < target) {
                best = walker.deepCopy()
            } else {
                return SubspaceCoordinates(position, position, best)
            }
            moved = false
        }
        if (position == max) break

        val next = subspace[position].toInt() and 0xFF
        if (isDimensionBreak(next)) {
            applyBreak(walker, next)
            moved = true
            if (walker > target) {
                val start = found
                return if (start != null) {
                    SubspaceCoordinates(start, position, best)
                } else {
                    SubspaceCoordinates(position, position, best)
                }
            }
            if (found == position) {
                found = position + 1
            }
        }
        position += 1
    }

    val start = found
    return if (start != null) {
        SubspaceCoordinates(start, max, best)
    } else {
        SubspaceCoordinates(max, max, best)
    }
}

private fun fixupBreaks(from: Coordinate, location: Coordinate): ByteArray {
    val fixup = ArrayList<Byte>()
    val subspaceCoordinate = from.deepCopy()
    while (subspaceCoordinate.z.library < location.z.library) {
        fixup.add(LIBRARY_BREAK_U8.toByte()); subspaceCoordinate.libraryBreak()
    }
    while (subspaceCoordinate.z.shelf < location.z.shelf) {
        fixup.add(SHELF_BREAK_U8.toByte()); subspaceCoordinate.shelfBreak()
    }
    while (subspaceCoordinate.z.series < location.z.series) {
        fixup.add(SERIES_BREAK_U8.toByte()); subspaceCoordinate.seriesBreak()
    }
    while (subspaceCoordinate.y.collection < location.y.collection) {
        fixup.add(COLLECTION_BREAK_U8.toByte()); subspaceCoordinate.collectionBreak()
    }
    while (subspaceCoordinate.y.volume < location.y.volume) {
        fixup.add(VOLUME_BREAK_U8.toByte()); subspaceCoordinate.volumeBreak()
    }
    while (subspaceCoordinate.y.book < location.y.book) {
        fixup.add(BOOK_BREAK_U8.toByte()); subspaceCoordinate.bookBreak()
    }
    while (subspaceCoordinate.x.chapter < location.x.chapter) {
        fixup.add(CHAPTER_BREAK_U8.toByte()); subspaceCoordinate.chapterBreak()
    }
    while (subspaceCoordinate.x.section < location.x.section) {
        fixup.add(SECTION_BREAK_U8.toByte()); subspaceCoordinate.sectionBreak()
    }
    while (subspaceCoordinate.x.scroll < location.x.scroll) {
        fixup.add(SCROLL_BREAK_U8.toByte()); subspaceCoordinate.scrollBreak()
    }
    return fixup.toByteArray()
}

private fun ArrayList<Byte>.toByteArray(): ByteArray {
    val out = ByteArray(size)
    for (i in indices) out[i] = this[i]
    return out
}

/** Removes the scroll at `location` (replace with "" + normalize). */
public fun remove(input: String, location: Coordinate): String {
    val phase1 = replace(input, location, "")
    return normalize(phase1)
}

/** The first scroll's text up to the first phext break, plus "..." if truncated. */
public fun createSummary(input: String): String {
    val bytes = input.toByteArray(Charsets.UTF_8)
    var end = 0
    while (end < bytes.size) {
        if (isPhextBreak(bytes[end].toInt() and 0xFF)) break
        end += 1
    }
    var summary = bytes.utf8String(0, end)
    if (end < bytes.size) {
        summary += "..."
    }
    return summary
}

/** HTML navigation map (`<ul>` of links) over the document's scrolls. */
public fun navmap(urlbase: String, input: String): String {
    val phokens = phokenize(input)
    val result = StringBuilder()
    if (phokens.isNotEmpty()) {
        result.append("<ul>\n")
    }
    for (phoken in phokens) {
        result.append(
            "<li><a href=\"" + urlbase + phoken.coord.toUrlencoded() + "\">" +
                phoken.coord.toString() + " " + createSummary(phoken.scroll) + "</a></li>\n"
        )
    }
    if (phokens.isNotEmpty()) {
        result.append("</ul>\n")
    }
    return result.toString()
}

/** Text navigation map: one `* coord: summary` line per scroll. */
public fun textmap(input: String): String {
    val phokens = phokenize(input)
    val result = StringBuilder()
    for (phoken in phokens) {
        result.append("* " + phoken.coord.toString() + ": " + createSummary(phoken.scroll) + "\n")
    }
    return result.toString()
}

/** 128-bit content checksum (XXH3-128), as 32 lowercase hex digits. */
public fun checksum(input: String): String =
    Xxh3.checksumHex(input.toByteArray(Charsets.UTF_8))

/** A phext whose scrolls are replaced by their checksums. */
public fun manifest(input: String): String {
    val phokens = phokenize(input).toMutableList()
    for (i in phokens.indices) {
        phokens[i] = phokens[i].copy(scroll = checksum(phokens[i].scroll))
    }
    return dephokenize(phokens)
}

private fun soundexInternal(byte: String): String {
    val letter1 = "bpfv"
    val letter2 = "cskgjqxz"
    val letter3 = "dt"
    val letter4 = "l"
    val letter5 = "mn"
    val letter6 = "r"
    var value = 1 // 1-100
    for (c in byte.toByteArray(Charsets.UTF_8)) {
        val ch = (c.toInt() and 0xFF).toChar()
        if (letter1.contains(ch)) { value += 1; continue }
        if (letter2.contains(ch)) { value += 2; continue }
        if (letter3.contains(ch)) { value += 3; continue }
        if (letter4.contains(ch)) { value += 4; continue }
        if (letter5.contains(ch)) { value += 5; continue }
        if (letter6.contains(ch)) { value += 6; continue }
    }
    return (value % 99).toString()
}

/** A phext whose scrolls are replaced by their soundex-v1 values. */
public fun soundexV1(input: String): String {
    val phokens = phokenize(input).toMutableList()
    for (i in phokens.indices) {
        phokens[i] = phokens[i].copy(scroll = soundexInternal(phokens[i].scroll))
    }
    return dephokenize(phokens)
}

private fun indexPhokens(input: String): List<PositionedScroll> {
    val phokens = phokenize(input)
    var offset = 0
    val coord = defaultCoordinate()
    val output = ArrayList<PositionedScroll>()
    for (phoken in phokens) {
        val reference = phoken.coord
        while (coord.z.library < reference.z.library) { coord.libraryBreak(); offset += 1 }
        while (coord.z.shelf < reference.z.shelf) { coord.shelfBreak(); offset += 1 }
        while (coord.z.series < reference.z.series) { coord.seriesBreak(); offset += 1 }
        while (coord.y.collection < reference.y.collection) { coord.collectionBreak(); offset += 1 }
        while (coord.y.volume < reference.y.volume) { coord.volumeBreak(); offset += 1 }
        while (coord.y.book < reference.y.book) { coord.bookBreak(); offset += 1 }
        while (coord.x.chapter < reference.x.chapter) { coord.chapterBreak(); offset += 1 }
        while (coord.x.section < reference.x.section) { coord.sectionBreak(); offset += 1 }
        while (coord.x.scroll < reference.x.scroll) { coord.scrollBreak(); offset += 1 }

        output.add(PositionedScroll(coord.deepCopy(), offset.toString()))
        offset += phoken.scroll.toByteArray(Charsets.UTF_8).size
    }
    return output
}

/** A phext whose scrolls hold the byte offset of each scroll in the input. */
public fun index(input: String): String {
    val output = indexPhokens(input).toMutableList()
    return dephokenize(output)
}

/** The offset of the scroll at `coord`, or of the last scroll before it (0 if none). */
public fun offset(input: String, coord: Coordinate): Int {
    val output = indexPhokens(input)
    var result = 0
    for (phoken in output) {
        if (phoken.coord > coord) break
        result = phoken.scroll.toIntOrNull() ?: result
    }
    return result
}

/** Replaces the scroll at `location` with `scroll`, creating breaks as needed. */
public fun replace(input: String, location: Coordinate, scroll: String): String {
    val bytes = input.toByteArray(Charsets.UTF_8)
    val parts = getSubspaceCoordinates(bytes, location)
    val start = parts.start
    var end = parts.end
    val fixup = fixupBreaks(parts.best, location)

    val text = scroll.toByteArray(Charsets.UTF_8)
    val max = bytes.size
    if (end > max) end = max
    val temp = ByteArray(start + fixup.size + text.size + (max - end))
    bytes.copyInto(temp, 0, 0, start)
    fixup.copyInto(temp, start)
    text.copyInto(temp, start + fixup.size)
    bytes.copyInto(temp, start + fixup.size + text.size, end, max)
    return temp.toString(Charsets.UTF_8)
}

/** Replaces every scroll from `location.start` through `location.end` with `scroll`. */
public fun rangeReplace(input: String, location: Range, scroll: String): String {
    val bytes = input.toByteArray(Charsets.UTF_8)
    val partsStart = getSubspaceCoordinates(bytes, location.start)
    val partsEnd = getSubspaceCoordinates(bytes, location.end)
    val start = partsStart.start
    var end = partsEnd.end

    val text = scroll.toByteArray(Charsets.UTF_8)
    val max = bytes.size
    if (end > max) end = max
    val temp = ByteArray(start + text.size + (max - end))
    bytes.copyInto(temp, 0, 0, start)
    text.copyInto(temp, start)
    bytes.copyInto(temp, start + text.size, end, max)
    return temp.toString(Charsets.UTF_8)
}

/**
 * Inserts `scroll` at `location`: existing content at that coordinate is
 * extended (the new text is appended to the scroll there), and breaks are
 * created to reach a coordinate past the current end.
 */
public fun insert(input: String, location: Coordinate, scroll: String): String {
    val bytes = input.toByteArray(Charsets.UTF_8)
    val parts = getSubspaceCoordinates(bytes, location)
    val end = parts.end
    val fixup = fixupBreaks(parts.best, location)

    val text = scroll.toByteArray(Charsets.UTF_8)
    val temp = ByteArray(end + fixup.size + text.size + (bytes.size - end))
    bytes.copyInto(temp, 0, 0, end)
    fixup.copyInto(temp, end)
    text.copyInto(temp, end + fixup.size)
    bytes.copyInto(temp, end + fixup.size + text.size, end, bytes.size)
    return temp.toString(Charsets.UTF_8)
}

/** Result of [nextScroll]: the scroll found, the coordinate after it, and the rest. */
public data class NextScrollResult(
    public val scroll: PositionedScroll,
    public val next: Coordinate,
    public val remaining: String,
)

/**
 * Retrieves the next scroll from the given string, assuming an arbitrary
 * starting point (libphext-rs `next_scroll`).
 */
public fun nextScroll(input: String, start: Coordinate): NextScrollResult {
    val location = start.deepCopy()
    val p = input.toByteArray(Charsets.UTF_8)
    val output = ArrayList<Byte>()
    var pi = 0
    var begin = start.deepCopy()
    val pmax = p.size
    while (pi < pmax) {
        var dimensionBreak = false
        val b = p[pi].toInt() and 0xFF
        if (b == SCROLL_BREAK_U8) { location.scrollBreak(); dimensionBreak = true }
        if (b == SECTION_BREAK_U8) { location.sectionBreak(); dimensionBreak = true }
        if (b == CHAPTER_BREAK_U8) { location.chapterBreak(); dimensionBreak = true }
        if (b == BOOK_BREAK_U8) { location.bookBreak(); dimensionBreak = true }
        if (b == VOLUME_BREAK_U8) { location.volumeBreak(); dimensionBreak = true }
        if (b == COLLECTION_BREAK_U8) { location.collectionBreak(); dimensionBreak = true }
        if (b == SERIES_BREAK_U8) { location.seriesBreak(); dimensionBreak = true }
        if (b == SHELF_BREAK_U8) { location.shelfBreak(); dimensionBreak = true }
        if (b == LIBRARY_BREAK_U8) { location.libraryBreak(); dimensionBreak = true }

        if (dimensionBreak) {
            if (output.isNotEmpty()) {
                pi += 1
                break
            }
        } else {
            begin = location.deepCopy()
            output.add(p[pi])
        }
        pi += 1
    }

    val remaining = ByteArray(p.size - pi)
    for (i in remaining.indices) remaining[i] = p[pi + i]

    val outScroll = PositionedScroll(begin, output.toByteArray().toString(Charsets.UTF_8))
    return NextScrollResult(outScroll, location, remaining.toString(Charsets.UTF_8))
}

/**
 * Transforms a packed phext buffer into a phext token (phoken) stream:
 * one [PositionedScroll] per scroll position, in coordinate order,
 * including empty scrolls reached by breaks (libphext-rs `phokenize`).
 */
public fun phokenize(input: String): List<PositionedScroll> {
    val result = ArrayList<PositionedScroll>()
    val bytes = input.toByteArray(Charsets.UTF_8)
    val max = bytes.size
    val coord = defaultCoordinate()
    var pi = 0
    while (true) {
        val start = coord.deepCopy()
        var begin = start
        var textStart: Int? = null
        var textEnd = max
        while (pi < max) {
            val byte = bytes[pi].toInt() and 0xFF
            if (isDimensionBreak(byte)) {
                applyBreak(coord, byte)
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
            bytes.utf8String(first, textEnd - first)
        } else {
            ""
        }
        result.add(PositionedScroll(begin, scroll))
        if (pi >= max) break
    }
    return result
}

/** Explodes an input phext into a coordinate -> scroll map. */
public fun explode(input: String): Map<Coordinate, String> {
    val parts = phokenize(input)
    val hash = HashMap<Coordinate, String>()
    for (row in parts) {
        hash[row.coord] = row.scroll
    }
    return hash
}

/** Serializes a coordinate -> scroll map into a monolithic phext document. */
public fun implode(map: Map<Coordinate, String>): String {
    val vec = ArrayList<PositionedScroll>()
    for ((key, value) in map) {
        vec.add(PositionedScroll(key, value))
    }
    vec.sort()
    return dephokenize(vec)
}

/** Combines `left` and `right`, merging content on a per-scroll basis. */
public fun merge(left: String, right: String): String {
    val tl = phokenize(left)
    val tr = phokenize(right)
    var tli = 0
    var tri = 0
    val maxtl = tl.size
    val maxtr = tr.size
    val result = StringBuilder()
    var coord = defaultCoordinate()

    while (true) {
        val haveLeft = tli < maxtl
        val haveRight = tri < maxtr

        val pickLeft = haveLeft && (!haveRight || tl[tli].coord <= tr[tri].coord)
        val pickRight = haveRight && (!haveLeft || tr[tri].coord <= tl[tli].coord)

        if (pickLeft) {
            appendScroll(result, tl[tli], coord)
            coord = tl[tli].coord
            tli += 1
        }
        if (pickRight) {
            appendScroll(result, tr[tri], coord)
            coord = tr[tri].coord
            tri += 1
        }

        if (!pickLeft && !pickRight) {
            break
        }
    }
    return result.toString()
}

/** Retrieves the scroll located at `target` ("" when there is none). */
public fun fetch(input: String, target: Coordinate): String {
    val bytes = input.toByteArray(Charsets.UTF_8)
    val parts = getSubspaceCoordinates(bytes, target)
    val start = parts.start
    val end = parts.end
    if (end > start) {
        return bytes.utf8String(start, end - start)
    }
    return ""
}

/** Shifts every break in the document one dimension up (line -> scroll -> ... ). */
public fun expand(input: String): String {
    val buffer = input.toByteArray(Charsets.UTF_8)
    for (p in buffer.indices) {
        val b = buffer[p].toInt() and 0xFF
        buffer[p] = when (b) {
            LINE_BREAK_U8 -> SCROLL_BREAK_U8.toByte()
            SCROLL_BREAK_U8 -> SECTION_BREAK_U8.toByte()
            SECTION_BREAK_U8 -> CHAPTER_BREAK_U8.toByte()
            CHAPTER_BREAK_U8 -> BOOK_BREAK_U8.toByte()
            BOOK_BREAK_U8 -> VOLUME_BREAK_U8.toByte()
            VOLUME_BREAK_U8 -> COLLECTION_BREAK_U8.toByte()
            COLLECTION_BREAK_U8 -> SERIES_BREAK_U8.toByte()
            SERIES_BREAK_U8 -> SHELF_BREAK_U8.toByte()
            SHELF_BREAK_U8 -> LIBRARY_BREAK_U8.toByte()
            else -> buffer[p]
        }
    }
    return buffer.toString(Charsets.UTF_8)
}

/** Shifts every break in the document one dimension down (library -> shelf -> ... -> line). */
public fun contract(input: String): String {
    val buffer = input.toByteArray(Charsets.UTF_8)
    for (p in buffer.indices) {
        val b = buffer[p].toInt() and 0xFF
        buffer[p] = when (b) {
            LIBRARY_BREAK_U8 -> SHELF_BREAK_U8.toByte()
            SHELF_BREAK_U8 -> SERIES_BREAK_U8.toByte()
            SERIES_BREAK_U8 -> COLLECTION_BREAK_U8.toByte()
            COLLECTION_BREAK_U8 -> VOLUME_BREAK_U8.toByte()
            VOLUME_BREAK_U8 -> BOOK_BREAK_U8.toByte()
            BOOK_BREAK_U8 -> CHAPTER_BREAK_U8.toByte()
            CHAPTER_BREAK_U8 -> SECTION_BREAK_U8.toByte()
            SECTION_BREAK_U8 -> SCROLL_BREAK_U8.toByte()
            SCROLL_BREAK_U8 -> LINE_BREAK_U8.toByte()
            else -> buffer[p]
        }
    }
    return buffer.toString(Charsets.UTF_8)
}

internal fun dephokenize(tokens: MutableList<PositionedScroll>): String {
    val result = StringBuilder()
    var coord = defaultCoordinate()
    for (ps in tokens) {
        if (ps.scroll.isNotEmpty()) {
            appendScroll(result, ps, coord)
        }
        coord = ps.coord
    }
    return result.toString()
}

private fun appendScroll(output: StringBuilder, token: PositionedScroll, coordIn: Coordinate) {
    val coord = coordIn.deepCopy()
    while (coord < token.coord) {
        if (coord.z.library < token.coord.z.library) { output.append(LIBRARY_BREAK); coord.libraryBreak(); continue }
        if (coord.z.shelf < token.coord.z.shelf) { output.append(SHELF_BREAK); coord.shelfBreak(); continue }
        if (coord.z.series < token.coord.z.series) { output.append(SERIES_BREAK); coord.seriesBreak(); continue }
        if (coord.y.collection < token.coord.y.collection) { output.append(COLLECTION_BREAK); coord.collectionBreak(); continue }
        if (coord.y.volume < token.coord.y.volume) { output.append(VOLUME_BREAK); coord.volumeBreak(); continue }
        if (coord.y.book < token.coord.y.book) { output.append(BOOK_BREAK); coord.bookBreak(); continue }
        if (coord.x.chapter < token.coord.x.chapter) { output.append(CHAPTER_BREAK); coord.chapterBreak(); continue }
        if (coord.x.section < token.coord.x.section) { output.append(SECTION_BREAK); coord.sectionBreak(); continue }
        if (coord.x.scroll < token.coord.x.scroll) { output.append(SCROLL_BREAK); coord.scrollBreak(); continue }
    }
    output.append(token.scroll)
}

/**
 * Subtracts the scrolls from `left` where there is also content at those
 * coordinates in `right`.
 */
public fun subtract(left: String, right: String): String {
    val pl = phokenize(left)
    val pr = phokenize(right)
    val result = StringBuilder()
    var pri = 0
    val max = pr.size
    var coord = defaultCoordinate()
    for (token in pl) {
        if (token.scroll.isEmpty()) {
            continue
        }
        while (pri < max && pr[pri].coord < token.coord) {
            pri += 1
        }
        val masked = pri < max && pr[pri].coord == token.coord && pr[pri].scroll.isNotEmpty()
        if (!masked) {
            appendScroll(result, token, coord)
            coord = token.coord
        }
    }
    return result.toString()
}

/** True when `byte` is one of the phext delimiters (line breaks included). */
public fun isPhextBreak(byte: Int): Boolean =
    byte == LINE_BREAK_U8 || byte == SCROLL_BREAK_U8 || byte == SECTION_BREAK_U8 ||
        byte == CHAPTER_BREAK_U8 || byte == BOOK_BREAK_U8 || byte == VOLUME_BREAK_U8 ||
        byte == COLLECTION_BREAK_U8 || byte == SERIES_BREAK_U8 || byte == SHELF_BREAK_U8 ||
        byte == LIBRARY_BREAK_U8

/** The canonical serialization of the document (implode of explode, via phokens). */
public fun normalize(input: String): String {
    val arr = phokenize(input).toMutableList()
    return dephokenize(arr)
}
