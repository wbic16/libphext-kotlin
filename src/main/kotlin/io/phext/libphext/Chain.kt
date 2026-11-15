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
