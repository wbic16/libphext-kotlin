package io.phext.libphext

/**
 * Chains: the stream level above the library break (libphext 0.6.0).
 *
 * A chain is an ordered series of complete phexts within one stream,
 * joined by CHAIN_BREAK (0x02) and optionally terminated by CHAIN_CLOSE
 * (0x03). `closed` records how the chain ended: true when a CHAIN_CLOSE
 * terminated it, false when the stream simply ran out (EOF). A stream
 * with no chain bytes at all is a single unclosed chain holding one
 * member phext: every pre-0.6 phext reads this way.
 *
 * Inside a chain, members are *universes* indexed from 1. The multiverse
 * address `$N:<address>` names the scroll at `<address>` in universe N;
 * a bare `<address>` means universe 1.
 */
public data class Chain(
    public var members: MutableList<String> = ArrayList(),
    public var closed: Boolean = false,
) {
    /** Serializes this chain alone: members joined by CHAIN_BREAK, plus a trailing CHAIN_CLOSE when closed. */
    public fun serialize(): String {
        val result = StringBuilder()
        var first = true
        for (member in members) {
            if (!first) result.append(CHAIN_BREAK)
            first = false
            result.append(member)
        }
        if (closed) result.append(CHAIN_CLOSE)
        return result.toString()
    }

    /**
     * Fetches a scroll by multiverse address: "$N:coord", or a bare
     * coordinate (universe 1). Out-of-range universes, malformed universe
     * prefixes, and missing scrolls all yield "", exactly as
     * libphext-rs `Chain::fetch`.
     */
    public fun fetch(address: String): String {
        val (universe, coord) = parseUniverseAddress(address) ?: return ""
        if (universe - 1 < 0 || universe - 1 >= members.size) return ""
        val member = members[(universe - 1).toInt()]
        return fetch(member, coord)
    }

    /**
     * Resolves a point address against this chain: (universe, coordinate,
     * character offset within the scroll), the offset counting Unicode
     * scalar values. A point without "@" resolves to offset 0 of its
     * scroll. Null when the universe or scroll is absent or the position
     * is out of range — never clamps (libphext 0.6.1; see Addressing.kt).
     */
    public fun resolvePoint(point: PointAddress): Triple<Long, Coordinate, Long>? {
        if (point.universe < 1 || point.universe > members.size.toLong()) return null
        val text = addressedScrollText(members[(point.universe - 1).toInt()], point.coordinate)
            ?: return null
        val offset = when (val pos = point.position) {
            null -> 0L
            else -> resolveScrollOffset(text, pos) ?: return null
        }
        return Triple(point.universe, point.coordinate, offset)
    }

    /**
     * Fetches a point address string. A bare point is a caret: it fetches
     * the empty string. The offset form fetches the selected characters:
     * from the point, forward `offset` characters (scalars) within the
     * scroll, terminating at the scroll boundary. Any parse or resolution
     * failure fetches the empty string, as [fetch] does.
     */
    public fun fetchPoint(address: String): String {
        val point = parsePointAddress(address) ?: return ""
        val count = point.offset ?: return ""
        val resolved = resolvePoint(point) ?: return ""
        val text = addressedScrollText(members[(point.universe - 1).toInt()], point.coordinate)
            ?: return ""
        val cps = codePointsOf(text)
        val start = resolved.third.toInt()
        val take = minOf(count, (cps.size - start).toLong())
        return stringOfCodePoints(cps, start, start + take.toInt())
    }

    /**
     * Resolves a range against this chain into per-scroll segments:
     * (universe, coordinate, segment text) in ascending order. Middle
     * scrolls contribute their whole text; the endpoint scrolls
     * contribute from/to their endpoint boundaries (an endpoint without
     * "@" contributes its whole scroll). Null when either endpoint fails
     * to resolve — an out-of-range endpoint is an error, never a clamp.
     */
    public fun resolveRange(range: RangeAddress): List<Triple<Long, Coordinate, String>>? {
        val start = range.start
        val end = range.end
        if (start.universe < 1 || end.universe > members.size.toLong() ||
            start.universe > end.universe
        ) {
            return null
        }
        // both endpoint scrolls must be present, and both endpoint
        // positions must resolve
        resolvePoint(start) ?: return null
        resolvePoint(end) ?: return null
        val segments = ArrayList<Triple<Long, Coordinate, String>>()
        for (universe in start.universe..end.universe) {
            val member = members[(universe - 1).toInt()]
            val scrolls = phokenize(member).sorted()
            for (ps in scrolls) {
                val coord = ps.coord
                if (universe == start.universe && coord < start.coordinate) continue
                if (universe == end.universe && coord > end.coordinate) continue
                val cps = codePointsOf(ps.scroll)
                var lo = 0L
                var hi = cps.size.toLong()
                if (universe == start.universe && coord == start.coordinate) {
                    lo = when (val pos = start.position) {
                        null -> 0L
                        else -> resolveScrollOffset(ps.scroll, pos) ?: return null
                    }
                }
                if (universe == end.universe && coord == end.coordinate) {
                    hi = when (val pos = end.position) {
                        null -> cps.size.toLong()
                        else -> resolveScrollOffset(ps.scroll, pos) ?: return null
                    }
                }
                if (lo > hi) return null
                segments.add(
                    Triple(universe, coord, stringOfCodePoints(cps, lo.toInt(), hi.toInt())),
                )
            }
        }
        return segments
    }

    /**
     * Fetches a range address string: the spanned text with phext
     * structure bytes preserved — the covered segments re-assembled at
     * their coordinates into a phext (chain) fragment (scroll breaks
     * between covered scrolls inside a universe, CHAIN_BREAK between
     * universes, never a trailing CHAIN_CLOSE: a fragment is an excerpt,
     * not a terminated chain), so the result round-trips as a phext
     * fragment. Empty segments contribute nothing. Any parse or
     * resolution failure fetches the empty string, as [fetch] does.
     */
    public fun fetchRange(address: String): String {
        val range = parseRangeAddress(address) ?: return ""
        val segments = resolveRange(range) ?: return ""
        val fragmentMembers = ArrayList<String>()
        for (universe in range.start.universe..range.end.universe) {
            var member = ""
            for ((segUniverse, coord, text) in segments) {
                if (segUniverse != universe) continue
                if (text.isNotEmpty()) {
                    member = insert(member, coord, text)
                }
            }
            fragmentMembers.add(member)
        }
        return Chain(fragmentMembers, false).serialize()
    }

    public companion object {
        @JvmStatic
        public fun newInstance(): Chain = Chain()
    }
}

/**
 * Splits a stream into its chains. Splitting follows split semantics, so
 * empty members and empty chains are preserved exactly: [parseChains] and
 * [serializeChains] are byte-exact inverses for every stream.
 */
public fun parseChains(input: String): List<Chain> {
    val endsClosed = input.endsWith(CHAIN_CLOSE)
    val segments = splitOn(input, CHAIN_CLOSE).toMutableList()
    if (endsClosed) {
        // the empty tail after a trailing delimiter is the end of the
        // stream, not the start of another chain
        segments.removeAt(segments.size - 1)
    }
    val count = segments.size
    val chains = ArrayList<Chain>()
    for ((i, segment) in segments.withIndex()) {
        val closed = endsClosed || (i + 1 < count)
        chains.add(Chain(splitOn(segment, CHAIN_BREAK).toMutableList(), closed))
    }
    return chains
}

/** Serializes a stream of chains back into stream bytes: the inverse of [parseChains]. */
public fun serializeChains(chains: List<Chain>): String {
    val result = StringBuilder()
    for (chain in chains) {
        result.append(chain.serialize())
    }
    return result.toString()
}

/**
 * Parses a multiverse address: "$N:<address>" or a bare "<address>"
 * (universe 1). Returns (universe, coordinate), or null when a `$` prefix
 * is malformed or names universe 0 — mirroring libphext-rs
 * `parse_universe_address` (including its use of the lenient
 * [toCoordinate] for the coordinate part).
 */
public fun parseUniverseAddress(address: String): Pair<Long, Coordinate>? {
    val universe: Long
    val coordPart: String
    if (address.startsWith('$')) {
        val rest = address.substring(1)
        val colon = rest.indexOf(':')
        if (colon < 0) return null
        val num = rest.substring(0, colon)
        val n = parseUniverseNumber(num) ?: return null
        if (n < 1) return null
        universe = n
        coordPart = rest.substring(colon + 1)
    } else {
        universe = 1
        coordPart = address
    }
    return universe to toCoordinate(coordPart)
}

private fun parseUniverseNumber(text: String): Long? {
    // Mirrors Rust's `str::parse::<usize>()`: optional leading '+', digits
    // only, overflow is a parse failure.
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
