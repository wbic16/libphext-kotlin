package io.phext.libphext

/**
 * Phext coordinates: a 9-dimensional address of the form
 * `library.shelf.series/collection.volume.book/chapter.section.scroll`
 * (written z3.z2.z1/y3.y2.y1/x3.x2.x1 in libphext-rs).
 *
 * Components are unbounded (the 999 cap was removed in libphext 0.4.0) and
 * are held in [Long]s. Every component is >= 1 in a valid coordinate.
 *
 * This mirrors libphext-rs 0.6.0 `Coordinate` / `ZCoordinate` / `YCoordinate`
 * / `XCoordinate` semantics exactly.
 */

public const val COORDINATE_MINIMUM: Long = 1

/** The large-scale Z arm of a phext coordinate: library, shelf, series. */
public data class ZCoordinate(
    public var library: Long = 1,
    public var shelf: Long = 1,
    public var series: Long = 1,
) : Comparable<ZCoordinate> {
    override fun compareTo(other: ZCoordinate): Int {
        var c = library.compareTo(other.library)
        if (c != 0) return c
        c = shelf.compareTo(other.shelf)
        if (c != 0) return c
        return series.compareTo(other.series)
    }

    override fun toString(): String = "$library.$shelf.$series"
}

/** The large-scale Y arm of a phext coordinate: collection, volume, book. */
public data class YCoordinate(
    public var collection: Long = 1,
    public var volume: Long = 1,
    public var book: Long = 1,
) : Comparable<YCoordinate> {
    override fun compareTo(other: YCoordinate): Int {
        var c = collection.compareTo(other.collection)
        if (c != 0) return c
        c = volume.compareTo(other.volume)
        if (c != 0) return c
        return book.compareTo(other.book)
    }

    override fun toString(): String = "$collection.$volume.$book"
}

/** The large-scale X arm of a phext coordinate: chapter, section, scroll. */
public data class XCoordinate(
    public var chapter: Long = 1,
    public var section: Long = 1,
    public var scroll: Long = 1,
) : Comparable<XCoordinate> {
    override fun compareTo(other: XCoordinate): Int {
        var c = chapter.compareTo(other.chapter)
        if (c != 0) return c
        c = section.compareTo(other.section)
        if (c != 0) return c
        return scroll.compareTo(other.scroll)
    }

    override fun toString(): String = "$chapter.$section.$scroll"
}

/** Thrown (as a parse result) when an address is not of the canonical form. */
public class PhextParseException(message: String = MESSAGE) :
    IllegalArgumentException(message) {
    public companion object {
        public const val MESSAGE: String =
            "Phext addresses are of the form LB.SF.SR/CN.VM.BK/CH.SN.SC"
    }
}

public data class Coordinate(
    public var z: ZCoordinate = ZCoordinate(),
    public var y: YCoordinate = YCoordinate(),
    public var x: XCoordinate = XCoordinate(),
) : Comparable<Coordinate> {

    override fun compareTo(other: Coordinate): Int {
        var c = z.compareTo(other.z)
        if (c != 0) return c
        c = y.compareTo(other.y)
        if (c != 0) return c
        return x.compareTo(other.x)
    }

    /**
     * A deep copy: the z/y/x arms are copied too. (The data-class `copy()`
     * is shallow and would share the arms — Rust's `Coordinate` is a value
     * type, so every snapshot in the reference is deep. Use this wherever
     * the reference copies a coordinate.)
     */
    public fun deepCopy(): Coordinate = Coordinate(z.copy(), y.copy(), x.copy())

    /** True when every component is >= [COORDINATE_MINIMUM]. */
    public fun validateCoordinate(): Boolean =
        z.library >= COORDINATE_MINIMUM && z.shelf >= COORDINATE_MINIMUM &&
            z.series >= COORDINATE_MINIMUM &&
            y.collection >= COORDINATE_MINIMUM && y.volume >= COORDINATE_MINIMUM &&
            y.book >= COORDINATE_MINIMUM &&
            x.chapter >= COORDINATE_MINIMUM && x.section >= COORDINATE_MINIMUM &&
            x.scroll >= COORDINATE_MINIMUM

    /**
     * Canonical string form (`z/y/x`), or "" when the coordinate is invalid —
     * exactly as libphext-rs `Coordinate::to_string`.
     */
    override fun toString(): String {
        if (!validateCoordinate()) return ""
        return "$z/$y/$x"
    }

    /** URL-encoded form: macro arms separated by ';' instead of '/'. */
    public fun toUrlencoded(): String {
        if (!validateCoordinate()) return ""
        return "$z;$y;$x"
    }

    public fun scrollBreak() {
        x.scroll = advance(x.scroll)
    }

    public fun sectionBreak() {
        x.section = advance(x.section)
        x.scroll = 1
    }

    public fun chapterBreak() {
        x.chapter = advance(x.chapter)
        x.section = 1
        x.scroll = 1
    }

    public fun bookBreak() {
        y.book = advance(y.book)
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public fun volumeBreak() {
        y.volume = advance(y.volume)
        y.book = 1
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public fun collectionBreak() {
        y.collection = advance(y.collection)
        y.volume = 1
        y.book = 1
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public fun seriesBreak() {
        z.series = advance(z.series)
        y.collection = 1
        y.volume = 1
        y.book = 1
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public fun shelfBreak() {
        z.shelf = advance(z.shelf)
        z.series = 1
        y.collection = 1
        y.volume = 1
        y.book = 1
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public fun libraryBreak() {
        z.library = advance(z.library)
        z.shelf = 1
        z.series = 1
        y.collection = 1
        y.volume = 1
        y.book = 1
        x.chapter = 1
        x.section = 1
        x.scroll = 1
    }

    public companion object {
        /** The default coordinate, 1.1.1/1.1.1/1.1.1. */
        @JvmStatic
        public fun newInstance(): Coordinate = Coordinate()

        /**
         * The strict parser (libphext-rs `Coordinate::try_from`): returns the
         * coordinate, or null when the address does not have exactly three
         * macro parts of three numeric components each. A component that is
         * non-numeric, empty, negative, or beyond [Long] range is a parse
         * failure (null), never an exception escaping this function.
         *
         * Note: Rust parses components as `usize` (unsigned 64-bit); Kotlin
         * components are signed [Long], so values above [Long.MAX_VALUE]
         * fail here where Rust would accept up to 2^64-1.
         */
        @JvmStatic
        public fun tryParse(value: String): Coordinate? {
            val parts = splitOn(value, '/')
            if (parts.size != 3) return null
            val zp = splitOn(parts[0], '.')
            val yp = splitOn(parts[1], '.')
            val xp = splitOn(parts[2], '.')
            if (zp.size != 3 || yp.size != 3 || xp.size != 3) return null
            val result = Coordinate()
            result.z.library = parseComponent(zp[0]) ?: return null
            result.z.shelf = parseComponent(zp[1]) ?: return null
            result.z.series = parseComponent(zp[2]) ?: return null
            result.y.collection = parseComponent(yp[0]) ?: return null
            result.y.volume = parseComponent(yp[1]) ?: return null
            result.y.book = parseComponent(yp[2]) ?: return null
            result.x.chapter = parseComponent(xp[0]) ?: return null
            result.x.section = parseComponent(xp[1]) ?: return null
            result.x.scroll = parseComponent(xp[2]) ?: return null
            return result
        }

        /**
         * Like [tryParse] but throws [PhextParseException] on malformed input
         * (the Kotlin face of libphext-rs `TryFrom` returning `Err`).
         */
        @JvmStatic
        public fun parse(value: String): Coordinate =
            tryParse(value) ?: throw PhextParseException()

        private fun parseComponent(text: String): Long? {
            // Mirrors Rust's `str::parse::<usize>()`: an optional leading '+'
            // followed by digits only; no whitespace, no '-', no empty string.
            if (text.isEmpty()) return null
            var start = 0
            if (text[0] == '+') {
                if (text.length == 1) return null
                start = 1
            }
            for (i in start until text.length) {
                if (text[i] !in '0'..'9') return null
            }
            return text.substring(start).toLongOrNull()
        }

        private fun advance(index: Long): Long = index + 1 // unbounded: never saturates
    }
}

/** A scroll of text together with the coordinate it lives at. */
public data class PositionedScroll(
    public var coord: Coordinate = Coordinate(),
    public var scroll: String = "",
) : Comparable<PositionedScroll> {
    override fun compareTo(other: PositionedScroll): Int = coord.compareTo(other.coord)

    public companion object {
        @JvmStatic
        public fun newInstance(): PositionedScroll =
            PositionedScroll(toCoordinate("1.1.1/1.1.1/1.1.1"), "")
    }
}

/** A pair of coordinates delimiting a range of scrolls. */
public data class Range(
    public var start: Coordinate = Coordinate(),
    public var end: Coordinate = Coordinate(),
) {
    override fun toString(): String = "$start-$end"

    public companion object {
        @JvmStatic
        public fun newInstance(): Range =
            Range(toCoordinate("1.1.1/1.1.1/1.1.1"), toCoordinate("1.1.1/1.1.1/1.1.1"))
    }
}

/** split-on-delimiter with Rust `str::split` semantics (empty segments kept). */
internal fun splitOn(s: String, delim: Char): List<String> {
    val out = ArrayList<String>()
    var start = 0
    for (i in s.indices) {
        if (s[i] == delim) {
            out.add(s.substring(start, i))
            start = i + 1
        }
    }
    out.add(s.substring(start))
    return out
}

/** The default coordinate, 1.1.1/1.1.1/1.1.1 (libphext-rs `default_coordinate`). */
public fun defaultCoordinate(): Coordinate = Coordinate()

/**
 * The lenient address parser (libphext-rs `to_coordinate`): scans digits and
 * the separators '.', '/', ';', assigning each digit run to the next component
 * in order. Non-digit, non-separator characters are ignored; digit runs
 * saturate at [Long.MAX_VALUE] instead of overflowing.
 */
public fun toCoordinate(address: String): Coordinate {
    val result = defaultCoordinate()
    var index = 0
    var value = 0L
    for (byte in address.toByteArray(Charsets.UTF_8)) {
        val b = byte.toInt() and 0xFF
        if (b == ADDRESS_MICRO_BREAK || b == ADDRESS_MACRO_BREAK || b == ADDRESS_MACRO_ALT) {
            when (index) {
                1 -> { result.z.library = value; index += 1 }
                2 -> { result.z.shelf = value; index += 1 }
                3 -> { result.z.series = value; index += 1 }
                4 -> { result.y.collection = value; index += 1 }
                5 -> { result.y.volume = value; index += 1 }
                6 -> { result.y.book = value; index += 1 }
                7 -> { result.x.chapter = value; index += 1 }
                8 -> { result.x.section = value; index += 1 }
                else -> {}
            }
            value = 0
        }
        if (b in 0x30..0x39) {
            value = saturatingMul10Add(value, (b - 0x30).toLong())
            if (index == 0) index = 1
        }
    }
    if (index > 0) {
        result.x.scroll = value
    }
    return result
}

private fun saturatingMul10Add(value: Long, digit: Long): Long {
    // value * 10 + digit, saturating at Long.MAX_VALUE (Rust: saturating ops on u64)
    return if (value > (Long.MAX_VALUE - digit) / 10) Long.MAX_VALUE else value * 10 + digit
}
