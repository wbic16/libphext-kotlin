package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Port of libphext-rs `src/test_phokenize.rs` (8 tests). */
class PhokenizeTest {

    /** The earlier phokenize: `next_scroll` in a loop. Quadratic, but the definition of the expected output. */
    private fun reference(phext: String): List<PositionedScroll> {
        val result = ArrayList<PositionedScroll>()
        var coord = defaultCoordinate()
        var temp = phext
        while (true) {
            val r = nextScroll(temp, coord)
            result.add(r.scroll)
            coord = r.next
            temp = r.remaining
            if (temp.isEmpty()) break
        }
        return result
    }

    private fun pairs(tokens: List<PositionedScroll>): List<Pair<String, String>> =
        tokens.map { it.coord.toString() to it.scroll }

    private val breaks = charArrayOf(
        '\u0017', '\u0018', '\u0019', '\u001A', '\u001C', '\u001D', '\u001E', '\u001F', '\u0001',
    )

    private fun edgeCases(): List<String> = listOf(
        "",
        "one scroll\nwith two lines",
        "a\u0017b\u0018c\u0019d\u001Ae\u001Cf\u001Dg\u001Eh\u001Fi\u0001j",
        "\u0017\u0017lead\u0017\u0017\u0017gap\u0001\u0001\u0017deep\u0017",
        "trailing one\u0017",
        "trailing two\u0017\u0017",
        "\u0001\u001F\u001E\u001D\u001C\u001A\u0019\u0018\u0017",
        "ünïcödé ✓\u001Fshelf ☃\u001Aboek ü\u0017🜁",
        "\n\n\u0017\n",
    )

    /** Small deterministic generator, so a failure always reproduces. */
    private class Lcg(var state: Long) {
        fun next(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state ushr 33
        }
    }

    private fun randomDoc(rng: Lcg): String {
        val pieces = listOf("a", "bc", "ü", "☃", "🜁", "\n", " ", "text")
        val doc = StringBuilder()
        val count = rng.next() % 60
        for (i in 0 until count) {
            if (rng.next() % 3 == 0L) {
                val run = 1 + rng.next() % 3
                for (j in 0 until run) {
                    doc.append(breaks[(rng.next() % 9).toInt()])
                }
            } else {
                doc.append(pieces[(rng.next() % pieces.size).toInt()])
            }
        }
        return doc.toString()
    }

    @Test
    fun phokenizeMatchesTheNextScrollLoop() {
        for (doc in edgeCases()) {
            assertEquals(pairs(reference(doc)), pairs(phokenize(doc)), "doc: $doc")
        }
        val rng = Lcg(0x5eed)
        for (i in 0 until 5_000) {
            val doc = randomDoc(rng)
            assertEquals(pairs(reference(doc)), pairs(phokenize(doc)), "doc: $doc")
        }
    }

    private fun subspaceTargets(doc: String, rng: Lcg): List<Coordinate> {
        // every scroll's own coordinate, its neighbours, and some arbitrary ones
        val targets = ArrayList<Coordinate>()
        for (token in phokenize(doc)) {
            targets.add(token.coord)
            val after = token.coord.deepCopy()
            after.scrollBreak()
            targets.add(after)
            val deeper = token.coord.deepCopy()
            deeper.sectionBreak()
            targets.add(deeper)
        }
        for (i in 0 until 4) {
            val address = "%d.%d.%d/%d.%d.%d/%d.%d.%d".format(
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 3,
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 3,
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 5,
            )
            targets.add(toCoordinate(address))
        }
        return targets
    }

    /** The non-empty scrolls of a document, as `explode` sees them. */
    private fun content(doc: String): Map<String, String> =
        explode(doc)
            .filter { it.value.isNotEmpty() }
            .mapKeys { it.key.toString() }
            .toSortedMap()

    private fun documents(seed: Long, count: Int): List<String> {
        val rng = Lcg(seed)
        val docs = ArrayList<String>()
        docs.addAll(edgeCases())
        docs.addAll(
            listOf(
                "a\u0018b\u0019c",
                "a\u0017b\u0017",
                "x\u0017",
                "\u0019\u001Aa",
                "a\u0018b\u0019c\u001Ad\u001Ce\u001Df\u001Eg\u001Fh\u0001i",
                // components are unbounded since 0.4.0: 1001 library breaks land at library 1002,
                // and the shelf run that follows does the same along the shelf dimension
                "\u0001".repeat(1001) + "deep" + "\u001F".repeat(1001) + "deeper",
            ),
        )
        for (i in 0 until count) {
            docs.add(randomDoc(rng))
        }
        val helios = File("tests/regression-1-helios.phext")
        if (helios.exists()) {
            docs.add(helios.readText(Charsets.UTF_8))
        }
        return docs
    }

    /** fetch, replace, insert, and remove each act on exactly the scroll at the target coordinate. */
    @Test
    fun editsActOnExactlyTheTargetScroll() {
        val rng = Lcg(0xc0de)
        for (doc in documents(0xc0de, 1_500)) {
            val before = content(doc)
            for (target in subspaceTargets(doc, rng)) {
                val key = target.toString()
                val current = before[key] ?: ""

                assertEquals(current, fetch(doc, target), "fetch at $key")

                val wantReplace = before.toMutableMap()
                wantReplace[key] = "X"
                assertEquals(wantReplace, content(replace(doc, target, "X")), "replace at $key")

                val wantInsert = before.toMutableMap()
                wantInsert[key] = current + "Y"
                assertEquals(wantInsert, content(insert(doc, target, "Y")), "insert at $key")

                val wantRemove = before.toMutableMap()
                wantRemove.remove(key)
                assertEquals(wantRemove, content(remove(doc, target)), "remove at $key")

                offset(doc, target)
            }
        }
    }

    @Test
    fun missingCoordinatesBetweenScrollsAreLeftAlone() {
        val doc = "a\u0018b\u0019c"
        val between = toCoordinate("1.1.1/1.1.1/1.1.2")
        assertEquals("", fetch(doc, between))
        assertEquals("a\u0017X\u0018b\u0019c", replace(doc, between, "X"))
        assertEquals("a\u0017Y\u0018b\u0019c", insert(doc, between, "Y"))
        assertEquals(doc, remove(doc, between))
        val after = toCoordinate("1.1.1/1.1.1/1.1.2")
        assertEquals("x\u0017Y", insert("x\u0017", after, "Y"))
        assertEquals("b", fetch("a\u0017b\u0017", after))
    }

    @Test
    fun subtractRemovesOnlyScrollsWithContentOnTheRight() {
        val docs = documents(0x5b7, 1_000)
        for ((index, left) in docs.withIndex()) {
            val right = docs[(index + 1) % docs.size]
            val mask = content(right)
            val want = content(left).filterKeys { it !in mask }
            assertEquals(want, content(subtract(left, right)), "subtract")
        }
        assertEquals("\u0017\u0017lead", subtract("\u0017\u0017lead", "a\u0018b"))
    }

    @Test
    fun offsetDoesNotPanicBeforeTheFirstScroll() {
        assertEquals(0, offset("\u0019\u001Aa", toCoordinate("1.1.1/1.1.1/1.1.1")))
        assertEquals(0, offset("", toCoordinate("2.2.2/2.2.2/2.2.2")))
        assertEquals(3, offset("ab\u0017cd", toCoordinate("1.1.1/1.1.1/1.1.2")))
        assertEquals(3, offset("ab\u0017cd", toCoordinate("1.1.1/1.1.1/1.1.9")))
    }

    @Test
    fun summariesKeepMultibyteCharactersWhole() {
        // U+010A's low byte is 0x0A, a line break (in its two-byte UTF-8 form it is not)
        assertEquals("\u010Abc", createSummary("\u010Abc"))
        assertEquals("日本...", createSummary("日本\u0017rest"))
        assertEquals("one...", createSummary("one\ntwo"))
    }

    @Test
    fun explodeAndImplodeRoundtripTheSameScrolls() {
        val rng = Lcg(0xfab1e)
        fun filled(m: Map<Coordinate, String>): List<Pair<String, String>> =
            m.filter { it.value.isNotEmpty() }
                .map { it.key.toString() to it.value }
                .sortedBy { it.first }
        for (i in 0 until 2_000) {
            val doc = randomDoc(rng)
            val map = explode(doc)
            val rebuilt = implode(map)
            val again = explode(rebuilt)
            assertEquals(filled(map), filled(again), "doc: $doc")
        }
    }

    @Test
    fun aLargePhextExplodesInLinearTime() {
        val doc = StringBuilder()
        for (index in 0 until 50_000) {
            if (index > 0) {
                // 40 scrolls a book, 40 books a volume: every coordinate stays in range
                doc.append(
                    when {
                        index % 1600 == 0 -> '\u001C'
                        index % 40 == 0 -> '\u001A'
                        else -> '\u0017'
                    },
                )
            }
            doc.append("kind: message\nauthor: bench\n---\nsome words about the topic here")
        }
        val text = doc.toString()
        val start = System.nanoTime()
        val map = explode(text)
        val rebuilt = implode(map)
        val toc = textmap(text)
        val spentMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(50_000, map.size)
        assertEquals(text, rebuilt)
        assertEquals(50_000, toc.count { it == '\n' })
        assertTrue(spentMs < 2000, "took $spentMs ms")
    }
}
