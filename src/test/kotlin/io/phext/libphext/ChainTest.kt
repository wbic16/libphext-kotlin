package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Port of libphext-rs `src/test_chain.rs` (12 tests). */
class ChainTest {

    /** parse + serialize must reproduce every stream byte-for-byte */
    private fun roundtrip(stream: String) {
        val chains = parseChains(stream)
        assertEquals(stream, serializeChains(chains), "round trip")
    }

    @Test
    fun chainFreeInputIsOneUnclosedChain() {
        // a phext with no chain bytes: the whole stream is the single member
        // of one unclosed chain, and it serializes back unchanged
        val doc = "alpha\u0017beta\u0001gamma"
        val chains = parseChains(doc)
        assertEquals(1, chains.size)
        assertEquals(listOf(doc), chains[0].members)
        assertFalse(chains[0].closed)
        assertEquals(doc, serializeChains(chains))

        // single-phext behaviour on the same bytes is untouched
        assertEquals("beta", fetch(doc, toCoordinate("1.1.1/1.1.1/1.1.2")))
        assertEquals("gamma", fetch(doc, toCoordinate("2.1.1/1.1.1/1.1.1")))
        roundtrip(doc)
    }

    @Test
    fun twoPhextsChainedAndClosedRoundTrip() {
        val stream = "edition one${CHAIN_BREAK}edition two${CHAIN_CLOSE}"
        val chains = parseChains(stream)
        assertEquals(1, chains.size)
        assertEquals(listOf("edition one", "edition two"), chains[0].members)
        assertTrue(chains[0].closed)
        roundtrip(stream)
    }

    @Test
    fun unclosedChainAtEofReportsTruncated() {
        val chains = parseChains("one\u0002two")
        assertEquals(1, chains.size)
        assertEquals(listOf("one", "two"), chains[0].members)
        assertFalse(chains[0].closed, "a chain ending at EOF must report unclosed")
        roundtrip("one\u0002two")
    }

    @Test
    fun twoChainsInOneStreamParseAsTwoUnits() {
        val stream = "a\u0002b\u0003c\u0002d\u0003"
        val chains = parseChains(stream)
        assertEquals(2, chains.size)
        assertEquals(listOf("a", "b"), chains[0].members)
        assertTrue(chains[0].closed)
        assertEquals(listOf("c", "d"), chains[1].members)
        assertTrue(chains[1].closed)
        roundtrip(stream)

        // a closed chain followed by an unclosed one stays two distinct chains
        val mixed = parseChains("a\u0003b")
        assertEquals(2, mixed.size)
        assertEquals(listOf("a"), mixed[0].members)
        assertTrue(mixed[0].closed)
        assertEquals(listOf("b"), mixed[1].members)
        assertFalse(mixed[1].closed)
        roundtrip("a\u0003b")
    }

    @Test
    fun chainedMembersCarryFullPhexts() {
        // members are complete phexts: coordinates resolve inside each member,
        // and each member restarts at 1.1.1/1.1.1/1.1.1
        val first = "root\u0017second scroll"
        val second = insert("", toCoordinate("1.1.1/1.1.1/1.1.3"), "third edition")
        val stream = "$first\u0002$second\u0003"
        val chains = parseChains(stream)
        assertEquals(1, chains.size)
        assertEquals(2, chains[0].members.size)
        assertEquals(
            "second scroll",
            fetch(chains[0].members[0], toCoordinate("1.1.1/1.1.1/1.1.2")),
        )
        assertEquals(
            "third edition",
            fetch(chains[0].members[1], toCoordinate("1.1.1/1.1.1/1.1.3")),
        )
        assertEquals(
            "",
            fetch(chains[0].members[1], toCoordinate("1.1.1/1.1.1/1.1.1")),
        )
        roundtrip(stream)
    }

    @Test
    fun editionsInSuccessionReadInOrder() {
        // the chain is the time axis: members arrive oldest-first and stay ordered
        val stream = "v1\u0002v2\u0002v3\u0003"
        val chains = parseChains(stream)
        assertEquals(1, chains.size)
        assertEquals(listOf("v1", "v2", "v3"), chains[0].members)
        assertTrue(chains[0].closed)
        roundtrip(stream)
    }

    @Test
    fun emptyAndBreakOnlyStreamsRoundTrip() {
        // the empty stream is one unclosed chain holding one empty phext
        val chains = parseChains("")
        assertEquals(1, chains.size)
        assertEquals(listOf(""), chains[0].members)
        assertFalse(chains[0].closed)
        roundtrip("")

        // a lone close terminates a chain holding one empty phext
        val chains2 = parseChains("\u0003")
        assertEquals(1, chains2.size)
        assertEquals(listOf(""), chains2[0].members)
        assertTrue(chains2[0].closed)
        roundtrip("\u0003")

        // leading and trailing breaks preserve their empty members
        assertEquals(listOf("a", ""), parseChains("a\u0002")[0].members)
        assertEquals(listOf("", "b"), parseChains("\u0002b")[0].members)
        assertEquals(3, parseChains("\u0002\u0002")[0].members.size)
        roundtrip("a\u0002")
        roundtrip("\u0002b")
        roundtrip("\u0002\u0002")
        roundtrip("\u0002")

        // back-to-back closes are two chains, the second holding an empty phext
        val chains3 = parseChains("a\u0003\u0003")
        assertEquals(2, chains3.size)
        assertTrue(chains3[0].closed && chains3[1].closed)
        assertEquals(listOf(""), chains3[1].members)
        roundtrip("a\u0003\u0003")
    }

    @Test
    fun serializeFromConstructedChains() {
        val chain = Chain.newInstance()
        assertFalse(chain.closed)
        assertEquals("", chain.serialize())
        chain.members.add("v1")
        chain.members.add("v2")
        assertEquals("v1\u0002v2", chain.serialize())
        chain.closed = true
        assertEquals("v1\u0002v2\u0003", chain.serialize())
        assertEquals("v1\u0002v2\u0003", serializeChains(listOf(chain)))
    }

    @Test
    fun canonShapedDocumentsParseUnchanged() {
        // the regression fixture stands in for the canon phexts (CYOA,
        // incipit.phext, emise.phext): verified to contain no 0x02 or 0x03
        // bytes, so each reads as one unclosed chain holding one member,
        // byte-identical in and out
        val doc = File("tests/regression-1-helios.phext").readText(Charsets.UTF_8)
        val docBytes = doc.toByteArray(Charsets.UTF_8)
        assertFalse(docBytes.contains(CHAIN_BREAK_U8.toByte()))
        assertFalse(docBytes.contains(CHAIN_CLOSE_U8.toByte()))
        val chains = parseChains(doc)
        assertEquals(1, chains.size)
        assertEquals(listOf(doc), chains[0].members)
        assertFalse(chains[0].closed)
        assertEquals(doc, serializeChains(chains))
    }

    @Test
    fun chainLayerDoesNotTouchMemberStructure() {
        // splitting happens at the stream level only: a member's own scrolls
        // and breaks are exactly what the single-phext functions see
        val chains = parseChains("x\u0017y\u0002z\u0017w\u0003")
        assertEquals(listOf("x\u0017y", "z\u0017w"), chains[0].members)
        assertEquals(2, phokenize(chains[0].members[1]).size)
        roundtrip("x\u0017y\u0002z\u0017w\u0003")
    }

    @Test
    fun multiverseAddressing() {
        val chain = Chain(mutableListOf("U1 home", "U2 home"), true)
        assertEquals("U1 home", chain.fetch("1.1.1/1.1.1/1.1.1"))
        assertEquals("U1 home", chain.fetch("\$1:1.1.1/1.1.1/1.1.1"))
        assertEquals("U2 home", chain.fetch("\$2:1.1.1/1.1.1/1.1.1"))
        assertEquals("", chain.fetch("\$3:1.1.1/1.1.1/1.1.1"))
        assertNull(parseUniverseAddress("\$0:1.1.1/1.1.1/1.1.1"))
        val parsed = parseUniverseAddress("\$12:2.1.1/1.1.1/1.1.1")
        assertEquals(12L, parsed!!.first)
    }

    @Test
    fun adjacentClosesAreTwoEmptyChains() {
        val chains = parseChains("\u0003\u0003")
        assertEquals(2, chains.size)
        assertTrue(chains.all { it.closed })
        assertEquals("\u0003\u0003", serializeChains(chains))
    }
}
