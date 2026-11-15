package io.phext.libphext

/**
 * Line/column addressing (libphext 0.6.1): point, offset, and range
 * addresses over chains, mirroring the addressing section of
 * libphext-rs `src/phext.rs` (v0.6.1) and the ratified range spec
 * (RANGE-SYNTAX-PROPOSAL.md).
 *
 * A point address is "[$N:]<9D coordinate>@<line>,<column>": the
 * multiverse prefix and the coordinate exactly as in
 * [parseUniverseAddress], plus a within-scroll caret position. Line and
 * column are 1-based; a position names a caret boundary (column 1 is the
 * boundary before the line's first character; a line of n characters has
 * valid columns 1..n+1), and columns count Unicode scalar values. A point
 * may carry "+<offsetChars>": select forward that many characters
 * (scalars) from the point, counted within the scroll, terminating at
 * the scroll boundary.
 *
 * A range address is two point addresses joined by one hyphen:
 * "<point>-<point>". The endpoints may span scrolls and universes; an
 * endpoint without "@" names its whole scroll (as start: its first
 * boundary; as end: its last). Canonical form: endpoints in ascending
 * (universe, coordinate, line, column) order — direction is discarded —
 * "$1:" never written, integers in minimal decimal form. Out-of-range
 * endpoints fail at resolution, never clamp.
 *
 * Kotlin/JVM strings are UTF-16: every line/column/offset computation
 * here iterates Unicode code points, never Char (UTF-16 code unit)
 * indices and never bytes. Lines split on 0x0A.
 *
 * Integers are [Long]s, consistent with this port's [Coordinate]:
 * libphext-rs parses address integers as `usize` (unsigned 64-bit), so
 * values above [Long.MAX_VALUE] fail to parse here where the reference
 * accepts up to 2^64-1 — the same known divergence as coordinate
 * components (see logs/kotlin.md).
 */

/** A caret boundary inside one scroll: 1-based line and column. */
public data class ScrollPosition(
    public var line: Long = 1,
    public var column: Long = 1,
) : Comparable<ScrollPosition> {
    override fun compareTo(other: ScrollPosition): Int {
        val c = line.compareTo(other.line)
        if (c != 0) return c
        return column.compareTo(other.column)
    }
}

/**
 * One parsed point address: universe (1-based; 1 when the "$N:" prefix
 * is absent), the 9D coordinate, an optional caret position, and an
 * optional forward offset in characters (scalars).
 */
public data class PointAddress(
    public var universe: Long = 1,
    public var coordinate: Coordinate = Coordinate(),
    public var position: ScrollPosition? = null,
    public var offset: Long? = null,
) {
    /** Canonical form: "$1:" never written; minimal decimal integers. */
    override fun toString(): String {
        val result = StringBuilder()
        if (universe > 1) result.append('$').append(universe).append(':')
        result.append(coordinate)
        val pos = position
        if (pos != null) {
            result.append('@').append(pos.line).append(',').append(pos.column)
            val off = offset
            if (off != null) result.append('+').append(off)
        }
        return result.toString()
    }
}

/** A parsed range: two point endpoints, normalized to ascending order at parse time. */
public data class RangeAddress(
    public var start: PointAddress,
    public var end: PointAddress,
) {
    override fun toString(): String = "$start-$end"
}

private fun parseAddressUint(text: String): Long? {
    if (text.isEmpty()) return null
    for (c in text) {
        if (c !in '0'..'9') return null
    }
    return text.toLongOrNull()
}

private fun parseAddressCoordinate(text: String): Coordinate? {
    val arms = splitOn(text, '/')
    if (arms.size != 3) return null
    val values = LongArray(9)
    var index = 0
    for (arm in arms) {
        val parts = splitOn(arm, '.')
        if (parts.size != 3) return null
        for (part in parts) {
            val value = parseAddressUint(part) ?: return null
            if (value < 1) return null
            values[index] = value
            index += 1
        }
    }
    return Coordinate(
        ZCoordinate(values[0], values[1], values[2]),
        YCoordinate(values[3], values[4], values[5]),
        XCoordinate(values[6], values[7], values[8]),
    )
}

/**
 * Parses a v0.6.1 point address:
 * "[$N:]<coordinate>[@<line>,<column>[+<offsetChars>]]". Returns null on
 * any malformed input: universe 0, a coordinate component below 1, a
 * line or column below 1, a sign anywhere, or trailing structure. "$1:"
 * is accepted on input; the canonical [PointAddress.toString] form never
 * emits it.
 */
public fun parsePointAddress(address: String): PointAddress? {
    var universe = 1L
    var rest = address
    if (address.startsWith('$')) {
        val stripped = address.substring(1)
        val colon = stripped.indexOf(':')
        if (colon < 0) return null
        val n = parseAddressUint(stripped.substring(0, colon)) ?: return null
        if (n < 1) return null
        universe = n
        rest = stripped.substring(colon + 1)
    }
    var coordPart = rest
    var posPart: String? = null
    val at = rest.indexOf('@')
    if (at >= 0) {
        coordPart = rest.substring(0, at)
        val pos = rest.substring(at + 1)
        if (pos.contains('@')) return null
        posPart = pos
    }
    val coordinate = parseAddressCoordinate(coordPart) ?: return null
    var position: ScrollPosition? = null
    var offset: Long? = null
    if (posPart != null) {
        val comma = posPart.indexOf(',')
        if (comma < 0) return null
        val line = parseAddressUint(posPart.substring(0, comma)) ?: return null
        val colPart = posPart.substring(comma + 1)
        var colText = colPart
        var offsetValue: Long? = null
        val plus = colPart.indexOf('+')
        if (plus >= 0) {
            colText = colPart.substring(0, plus)
            offsetValue = parseAddressUint(colPart.substring(plus + 1)) ?: return null
        }
        val column = parseAddressUint(colText) ?: return null
        if (line < 1 || column < 1) return null
        position = ScrollPosition(line, column)
        offset = offsetValue
    }
    return PointAddress(universe, coordinate, position, offset)
}

private fun endpointPositionKey(point: PointAddress, isStart: Boolean): Pair<Long, Long> {
    val pos = point.position
    return if (pos != null) {
        pos.line to pos.column
    } else if (isStart) {
        1L to 1L
    } else {
        Long.MAX_VALUE to Long.MAX_VALUE
    }
}

private fun endpointCmp(a: PointAddress, aIsStart: Boolean, b: PointAddress, bIsStart: Boolean): Int {
    if (a.universe != b.universe) return a.universe.compareTo(b.universe)
    val coord = a.coordinate.compareTo(b.coordinate)
    if (coord != 0) return coord
    val ka = endpointPositionKey(a, aIsStart)
    val kb = endpointPositionKey(b, bIsStart)
    if (ka.first != kb.first) return ka.first.compareTo(kb.first)
    return ka.second.compareTo(kb.second)
}

/**
 * Parses a v0.6.1 range address: exactly two point addresses joined by
 * one hyphen. The endpoints are normalized to ascending order (direction
 * is discarded, per the ratified spec). Range endpoints may not carry a
 * "+offset" — the offset form never takes a second endpoint. Returns
 * null on malformed input.
 */
public fun parseRangeAddress(address: String): RangeAddress? {
    val hyphen = address.indexOf('-')
    if (hyphen < 0) return null
    val first = address.substring(0, hyphen)
    val second = address.substring(hyphen + 1)
    if (second.contains('-')) return null
    val a = parsePointAddress(first) ?: return null
    val b = parsePointAddress(second) ?: return null
    if (a.offset != null || b.offset != null) return null
    return if (endpointCmp(a, true, b, false) > 0) {
        RangeAddress(b, a)
    } else {
        RangeAddress(a, b)
    }
}

/** The Unicode scalar values (code points) of [text], in order. */
internal fun codePointsOf(text: String): IntArray {
    val result = IntArray(text.codePointCount(0, text.length))
    var index = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        result[index] = cp
        index += 1
        i += Character.charCount(cp)
    }
    return result
}

/** Builds a string from the code points in `cps[from] until cps[until]`. */
internal fun stringOfCodePoints(cps: IntArray, from: Int, until: Int): String {
    val result = StringBuilder()
    for (i in from until until) {
        result.appendCodePoint(cps[i])
    }
    return result.toString()
}

/**
 * Resolves a caret position inside one scroll to a character (scalar)
 * offset. Null when the line or column is past the scroll's extent —
 * never clamps.
 */
internal fun resolveScrollOffset(text: String, pos: ScrollPosition): Long? {
    if (pos.line < 1 || pos.column < 1) return null
    var line = 1L
    var column = 1L
    var offset = 0L
    var i = 0
    while (i < text.length) {
        if (line == pos.line && column == pos.column) return offset
        val cp = text.codePointAt(i)
        if (cp == LINE_BREAK_U8) {
            line += 1
            column = 1
        } else {
            column += 1
        }
        offset += 1
        i += Character.charCount(cp)
    }
    return if (line == pos.line && column == pos.column) offset else null
}

/**
 * The scroll text at a coordinate in one member phext, in the phokenize
 * sense of presence: a coordinate is present exactly when [phokenize]
 * enumerates it.
 */
internal fun addressedScrollText(member: String, target: Coordinate): String? =
    phokenize(member).firstOrNull { it.coord == target }?.scroll
