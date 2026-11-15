package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Port of libphext-rs `src/test_lib.rs` (54 tests), assertion for assertion
 * wherever the platforms agree. Divergences, all forced by Kotlin/JVM types:
 * - components are Long (signed 64-bit) where Rust uses usize (unsigned):
 *   the "largest component" TryFrom case uses Long.MAX_VALUE, and
 *   toCoordinate saturates at Long.MAX_VALUE.
 * - the fs test uses a temp file instead of the crate-relative
 *   unit-test.phext (Gradle test working directory is the project dir;
 *   content assertions are unchanged).
 */
class PhextTest {

    private fun c(address: String): Coordinate = toCoordinate(address)

    @Test
    fun testCoordinateParsing() {
        val exampleCoordinate = "9.8.7/6.5.4/3.2.1"
        val test = toCoordinate(exampleCoordinate)
        assertEquals(exampleCoordinate, test.toString(), "Coordinate parsing failed")

        val weird = toCoordinate("HOME").toString()
        assertEquals("1.1.1/1.1.1/1.1.1", weird)
    }

    @Test
    fun testToUrlencoded() {
        assertEquals("1.1.1;1.1.1;1.1.1", c("1.1.1/1.1.1/1.1.1").toUrlencoded())
        assertEquals("98.76.54;32.10.1;23.45.67", c("98.76.54/32.10.1/23.45.67").toUrlencoded())
    }

    private fun testHelper(delim: Char, data: List<Pair<String, String>>): Boolean {
        if (data.size < 3) return false
        val sample = data.joinToString(delim.toString()) { it.first }
        for ((expect, address) in data) {
            val text = fetch(sample, c(address))
            assertEquals(expect, text, "Fetching text for $address failed")
        }
        return true
    }

    @Test
    fun testScrolls() {
        val data = listOf(
            "Hello World" to "1.1.1/1.1.1/1.1.1",
            "Scroll #2 -- this text will be selected" to "1.1.1/1.1.1/1.1.2",
            "Scroll #3 - this text will be ignored" to "1.1.1/1.1.1/1.1.3",
        )
        assertTrue(testHelper(SCROLL_BREAK, data))
    }

    @Test
    fun testSections() {
        val data = listOf(
            "Section A" to "1.1.1/1.1.1/1.1.1",
            "Section B" to "1.1.1/1.1.1/1.2.1",
            "Section C" to "1.1.1/1.1.1/1.3.1",
        )
        assertTrue(testHelper(SECTION_BREAK, data))
    }

    @Test
    fun testChapters() {
        val data = listOf(
            "Chapter Alpha" to "1.1.1/1.1.1/1.1.1",
            "Chapter Beta" to "1.1.1/1.1.1/2.1.1",
            "Chapter Gamma" to "1.1.1/1.1.1/3.1.1",
        )
        assertTrue(testHelper(CHAPTER_BREAK, data))
    }

    @Test
    fun testBooks() {
        val data = listOf(
            "Book z1" to "1.1.1/1.1.1/1.1.1",
            "Book Something Else #2" to "1.1.1/1.1.2/1.1.1",
            "Book Part 3" to "1.1.1/1.1.3/1.1.1",
        )
        assertTrue(testHelper(BOOK_BREAK, data))
    }

    @Test
    fun testVolumes() {
        val data = listOf(
            "Volume 1-1-1" to "1.1.1/1.1.1/1.1.1",
            "Volume 1-2-1" to "1.1.1/1.2.1/1.1.1",
            "Volume 1-3-1" to "1.1.1/1.3.1/1.1.1",
        )
        assertTrue(testHelper(VOLUME_BREAK, data))
    }

    @Test
    fun testCollections() {
        val data = listOf(
            "Collection 1-1-1" to "1.1.1/1.1.1/1.1.1",
            "Collection 2-1-1" to "1.1.1/2.1.1/1.1.1",
            "Collection 3-1-1" to "1.1.1/3.1.1/1.1.1",
        )
        assertTrue(testHelper(COLLECTION_BREAK, data))
    }

    @Test
    fun testSeries() {
        val data = listOf(
            "Series 1-1-1" to "1.1.1/1.1.1/1.1.1",
            "Series 1-1-2" to "1.1.2/1.1.1/1.1.1",
            "Series 1-1-3" to "1.1.3/1.1.1/1.1.1",
        )
        assertTrue(testHelper(SERIES_BREAK, data))
    }

    @Test
    fun testShelves() {
        val data = listOf(
            "Shelf 1-1-1" to "1.1.1/1.1.1/1.1.1",
            "Shelf 1-2-1" to "1.2.1/1.1.1/1.1.1",
            "Shelf 1-3-1" to "1.3.1/1.1.1/1.1.1",
        )
        assertTrue(testHelper(SHELF_BREAK, data))
    }

    @Test
    fun testLibraries() {
        val data = listOf(
            "Library 1-1-1" to "1.1.1/1.1.1/1.1.1",
            "Library 2-1-1" to "2.1.1/1.1.1/1.1.1",
            "Library 3-1-1" to "3.1.1/1.1.1/1.1.1",
        )
        assertTrue(testHelper(LIBRARY_BREAK, data))
    }

    @Test
    fun testCoordinatesInvalid() {
        val c1 = toCoordinate("0.0.0/0.0.0/0.0.0") // invalid
        val c2 = Coordinate(
            ZCoordinate(0, 0, 0),
            YCoordinate(0, 0, 0),
            XCoordinate(0, 0, 0),
        )
        assertEquals(c2, c1)
        assertFalse(c1.validateCoordinate())
        assertFalse(c2.validateCoordinate())
    }

    @Test
    fun testCoordinatesValid() {
        val c1 = toCoordinate("1001.254.253/32.4.8/4.2.1") // valid
        val c2 = Coordinate(
            ZCoordinate(1001, 254, 253),
            YCoordinate(32, 4, 8),
            XCoordinate(4, 2, 1),
        )
        assertEquals(c2, c1)
        assertEquals(4L, c1.y.volume)
        // components above 999 are valid since 0.4.0 removed the cap
        assertTrue(c1.validateCoordinate())
        assertTrue(c2.validateCoordinate())
    }

    @Test
    fun testUrlEncoding() {
        val c1 = toCoordinate("142.143.144;145.146.147;148.149.150") // valid
        val c2 = Coordinate(
            ZCoordinate(142, 143, 144),
            YCoordinate(145, 146, 147),
            XCoordinate(148, 149, 150),
        )
        assertEquals(c2, c1)
        assertTrue(c1.validateCoordinate())
        assertTrue(c2.validateCoordinate())

        val c3 = toCoordinate("1001.1002.1003;1004.1005.1006;1007.1008.1009")
        val c4 = Coordinate(
            ZCoordinate(1001, 1002, 1003),
            YCoordinate(1004, 1005, 1006),
            XCoordinate(1007, 1008, 1009),
        )
        assertEquals(c4, c3)
        assertTrue(c3.validateCoordinate())
        assertTrue(c4.validateCoordinate())
    }

    @Test
    fun testRealisticParse() {
        val coord = c("6.13.4/2.11.4/2.20.3")
        // the rs literal, reconstructed from its exact break counts
        // (verified against the rs source bytes): the breaks that walk
        // 1.1.1/1.1.1/1.1.1 -> 6.13.4/2.11.4/2.20.3
        val subspace =
            "\u0001".repeat(5) + "\u001F".repeat(12) + "\u001E".repeat(3) +
                "\u001D" + "\u001C".repeat(10) + "\u001A".repeat(3) + "\u0019" +
                "\u0018".repeat(19) + "\u0017".repeat(2) +
                "here's some text at 6.13.4/2.11.4/2.20.3" +
                "\u0017this is the next scroll and won't be picked"
        val result = fetch(subspace, coord)
        assertEquals("here's some text at 6.13.4/2.11.4/2.20.3", result)
    }

    @Test
    fun testDeadReckoning() {
        val test = StringBuilder()
        test.append("random text in 1.1.1/1.1.1/1.1.1 that we can skip past")
        test.append(LIBRARY_BREAK)
        test.append("everything in here is at 2.1.1/1.1.1/1.1.1")
        test.append(SCROLL_BREAK)
        test.append("and now we're at 2.1.1/1.1.1/1.1.2")
        test.append(SCROLL_BREAK)
        test.append("moving on up to 2.1.1/1.1.1/1.1.3")
        test.append(BOOK_BREAK)
        test.append("and now over to 2.1.1/1.1.2/1.1.1")
        test.append(SHELF_BREAK)
        test.append("woot, up to 2.2.1/1.1.1/1.1.1")
        test.append(LIBRARY_BREAK)
        test.append("here we are at 3.1.1/1.1.1.1.1")
        test.append(LIBRARY_BREAK) // 4.1.1/1.1.1/1.1.1
        test.append(LIBRARY_BREAK) // 5.1.1/1.1.1/1.1.1
        test.append("getting closer to our target now 5.1.1/1.1.1/1.1.1")
        repeat(4) { test.append(SHELF_BREAK) } // 5.2.1 .. 5.5.1
        repeat(4) { test.append(SERIES_BREAK) } // 5.5.2 .. 5.5.5
        test.append("here we go! 5.5.5/1.1.1/1.1.1")
        repeat(3) { test.append(COLLECTION_BREAK) } // 5.5.5/2.1.1 .. /4.1.1
        repeat(3) { test.append(BOOK_BREAK) } // 5.5.5/4.1.2 .. /4.1.4
        test.append("this test appears at 5.5.5/4.1.4/1.1.1")
        repeat(5) { test.append(VOLUME_BREAK) } // 5.5.5/4.2.1 .. /4.6.1
        repeat(4) { test.append(CHAPTER_BREAK) } // .. 5.5.5/4.6.1/5.1.1
        repeat(6) { test.append(BOOK_BREAK) } // .. 5.5.5/4.6.7/1.1.1
        repeat(4) { test.append(CHAPTER_BREAK) } // .. 5.5.5/4.6.7/5.1.1
        repeat(5) { test.append(SCROLL_BREAK) } // .. 5.5.5/4.6.7/5.1.6
        test.append("here's a test at 5.5.5/4.6.7/5.1.6")
        test.append(SCROLL_BREAK) // 5.5.5/4.6.7/5.1.7
        repeat(4) { test.append(CHAPTER_BREAK) } // .. 5.5.5/4.6.7/9.1.1
        repeat(4) { test.append(SECTION_BREAK) } // .. 5.5.5/4.6.7/9.5.1
        repeat(8) { test.append(SCROLL_BREAK) } // .. 5.5.5/4.6.7/9.5.9
        test.append("Expected Test Pattern Alpha Whisky Tango Foxtrot")
        val doc = test.toString()

        assertEquals(
            "Expected Test Pattern Alpha Whisky Tango Foxtrot",
            fetch(doc, c("5.5.5/4.6.7/9.5.9")),
        )
        assertEquals(
            "here's a test at 5.5.5/4.6.7/5.1.6",
            fetch(doc, c("5.5.5/4.6.7/5.1.6")),
        )
    }

    @Test
    fun testLineBreak() {
        assertEquals('\n', LINE_BREAK)
    }

    @Test
    fun testMoreCowbell() {
        assertEquals('\u0007', MORE_COWBELL)
        assertTrue(checkForCowbell("Hello\u0007"))
        assertFalse(checkForCowbell("nope\u0017just more scrolls"))
    }

    @Test
    fun testCoordinateBasedInsert() {
        var test = ""
        test += "aaa" // 1.1.1/1.1.1/1.1.1
        test += LIBRARY_BREAK // 2.1.1/1.1.1/1.1.1
        test += "bbb"
        test += SCROLL_BREAK // 2.1.1/1.1.1/1.1.2
        test += "ccc"

        val root = c("1.1.1/1.1.1/1.1.1")
        val coord1 = c("2.1.1/1.1.1/1.1.3")
        val expected1 = getSubspaceCoordinates(test.toByteArray(Charsets.UTF_8), coord1)
        assertEquals(2L, expected1.best.z.library)
        assertEquals(1L, expected1.best.z.shelf)
        assertEquals(1L, expected1.best.z.series)
        assertEquals(1L, expected1.best.y.collection)
        assertEquals(1L, expected1.best.y.volume)
        assertEquals(1L, expected1.best.y.book)
        assertEquals(1L, expected1.best.x.chapter)
        assertEquals(1L, expected1.best.x.section)
        assertEquals(2L, expected1.best.x.scroll)
        assertEquals(11, expected1.start)
        assertEquals(11, expected1.end)

        val expectedCoord = defaultCoordinate()
        expectedCoord.z.library = 2
        expectedCoord.x.scroll = 3
        assertEquals(expectedCoord, coord1)

        val update1 = insert(test, coord1, "ddd")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd", update1)

        val update2 = insert(update1, c("2.1.1/1.1.1/1.1.4"), "eee")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee", update2)

        val update3 = insert(update2, c("2.1.1/1.1.1/1.2.1"), "fff")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff", update3)

        val update4 = insert(update3, c("2.1.1/1.1.1/1.2.2"), "ggg")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff\u0017ggg", update4)

        val update5 = insert(update4, c("2.1.1/1.1.1/2.1.1"), "hhh")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff\u0017ggg\u0019hhh", update5)

        val update6 = insert(update5, c("2.1.1/1.1.1/1.1.5"), "iii")
        assertEquals("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", update6)

        val update7 = insert(update6, root, "---AAA")
        assertEquals("aaa---AAA\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", update7)

        val update8 = insert(update7, c("2.1.1/1.1.1/1.1.1"), "---BBB")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", update8)

        val update9 = insert(update8, c("2.1.1/1.1.1/1.1.2"), "---CCC")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", update9)

        val update10 = insert(update9, c("2.1.1/1.1.1/1.1.3"), "---DDD")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", update10)

        val update11 = insert(update10, c("2.1.1/1.1.1/1.1.4"), "---EEE")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii\u0018fff\u0017ggg\u0019hhh", update11)

        val update12 = insert(update11, c("2.1.1/1.1.1/1.1.5"), "---III")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff\u0017ggg\u0019hhh", update12)

        val update13 = insert(update12, c("2.1.1/1.1.1/1.2.1"), "---FFF")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg\u0019hhh", update13)

        val update14 = insert(update13, c("2.1.1/1.1.1/1.2.2"), "---GGG")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh", update14)

        val update15 = insert(update14, c("2.1.1/1.1.1/2.1.1"), "---HHH")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH", update15)

        val update16 = insert(update15, c("2.1.1/1.1.2/1.1.1"), "jjj")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj", update16)

        val update17 = insert(update16, c("2.1.1/1.2.1/1.1.1"), "kkk")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk", update17)

        val update18 = insert(update17, c("2.1.1/2.1.1/1.1.1"), "lll")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll", update18)

        val update19 = insert(update18, c("2.1.2/1.1.1/1.1.1"), "mmm")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm", update19)

        val update20 = insert(update19, c("2.2.1/1.1.1/1.1.1"), "nnn")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm\u001Fnnn", update20)

        val update21 = insert(update20, c("3.1.1/1.1.1/1.1.1"), "ooo")
        assertEquals("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm\u001Fnnn\u0001ooo", update21)
    }

    @Test
    fun testCoordinateBasedReplace() {
        val update0 = replace(
            "AAA\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj",
            c("1.1.1/1.1.1/1.1.1"), "aaa",
        )
        assertEquals("aaa\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update0)

        val update1 = replace(update0, c("1.1.1/1.1.1/1.1.2"), "222")
        assertEquals("aaa\u0017222\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update1)

        val update2 = replace(update1, c("1.1.1/1.1.1/1.2.1"), "3-")
        assertEquals("aaa\u0017222\u00183-\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update2)

        val update3 = replace(update2, c("1.1.1/1.1.1/2.1.1"), "delta")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update3)

        val update4 = replace(update3, c("1.1.1/1.1.2/1.1.1"), "a bridge just close enough")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update4)

        val update5 = replace(update4, c("1.1.1/1.2.1/1.1.1"), "nifty")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update5)

        val update6 = replace(update5, c("1.1.1/2.1.1/1.1.1"), "G8")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001Ehhh\u001Fiii\u0001jjj", update6)

        val update7 = replace(update6, c("1.1.2/1.1.1/1.1.1"), "Hello World")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001Fiii\u0001jjj", update7)

        val update8 = replace(update7, c("1.2.1/1.1.1/1.1.1"), "_o_")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001F_o_\u0001jjj", update8)

        val update9 = replace(update8, c("2.1.1/1.1.1/1.1.1"), "/win")
        assertEquals("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001F_o_\u0001/win", update9)

        // the api editor has trouble with this input...
        val updateR0a = replace("hello world\u0017scroll two", c("2.1.1/1.1.1/1.1.5"), "2.1.1-1.1.1-1.1.5")
        assertEquals("hello world\u0017scroll two\u0001\u0017\u0017\u0017\u00172.1.1-1.1.1-1.1.5", updateR0a)

        // regression from api testing
        val updateR1a = replace("", c("1.1.1/1.1.1/1.1.1"), "aaa")
        assertEquals("aaa", updateR1a)

        val updateR1b = replace(updateR1a, c("1.1.1/1.1.1/1.1.2"), "bbb")
        assertEquals("aaa\u0017bbb", updateR1b)

        val updateR1c = replace(updateR1b, c("1.2.3/4.5.6/7.8.9"), "ccc")
        assertEquals("aaa\u0017bbb\u001F\u001E\u001E\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ccc", updateR1c)

        val updateR1d = replace(updateR1c, c("1.4.4/2.8.8/4.16.16"), "ddd")
        assertEquals("aaa\u0017bbb\u001F\u001E\u001E\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ccc\u001F\u001F\u001E\u001E\u001E\u001D\u001C\u001C\u001C\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ddd", updateR1d)

        val updateRegression1 = replace(updateR1d, c("11.12.13/14.15.16/17.18.19"), "eee")
        assertEquals(
            "aaa\u0017bbb\u001F\u001E\u001E\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ccc\u001F\u001F\u001E\u001E\u001E\u001D\u001C\u001C\u001C\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ddd" +
                "\u0001".repeat(10) +
                "\u001F".repeat(11) +
                "\u001E".repeat(12) +
                "\u001D".repeat(13) +
                "\u001C".repeat(14) +
                "\u001A".repeat(15) +
                "\u0019".repeat(16) +
                "\u0018".repeat(17) +
                "\u0017".repeat(18) +
                "eee",
            updateRegression1,
        )

        // finally found the bugger!
        val regression2Baseline =
            "1.1.1\u00171.1.2\u00171.1.3\u00171.1.4\u00181.2.1\u00171.2.2\u00171.2.3\u00171.2.4\u0019" +
                "2.1.1\u00193.1.1\u00194.1.1\u001A2/1.1.1\u0017\u00172/1.1.3\u001C2.1/1.1.1\u001D2.1.1/1.1.1\u001E2/1.1.1/1.1.1\u001F2.1/1.1.1/1.1.1\u00012.1.1/1.1.1/1.1.1"
        val updateRegression2 = replace(regression2Baseline, c("1.1.1/1.1.2/1.1.2"), "new content")
        assertEquals(
            "1.1.1\u00171.1.2\u00171.1.3\u00171.1.4\u00181.2.1\u00171.2.2\u00171.2.3\u00171.2.4\u00192.1.1\u00193.1.1\u00194.1.1\u001A2/1.1.1\u0017new content\u00172/1.1.3\u001C2.1/1.1.1\u001D2.1.1/1.1.1\u001E2/1.1.1/1.1.1\u001F2.1/1.1.1/1.1.1\u00012.1.1/1.1.1/1.1.1",
            updateRegression2,
        )
    }

    @Test
    fun testCoordinateBasedRemove() {
        var update = "aaa\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj"
        update = remove(update, c("1.1.1/1.1.1/1.1.1"))
        assertEquals("\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/1.1.1/1.1.2"))
        assertEquals("\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/1.1.1/1.2.1"))
        assertEquals("\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/1.1.1/2.1.1"))
        assertEquals("\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/1.1.2/1.1.1"))
        assertEquals("\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/1.2.1/1.1.1"))
        assertEquals("\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.1/2.1.1/1.1.1"))
        assertEquals("\u001Ehhh\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.1.2/1.1.1/1.1.1"))
        assertEquals("\u001Fiii\u0001jjj", update)
        update = remove(update, c("1.2.1/1.1.1/1.1.1"))
        assertEquals("\u0001jjj", update)
        update = remove(update, c("2.1.1/1.1.1/1.1.1"))
        assertEquals("", update)
    }

    @Test
    fun testRangeBasedReplace() {
        val doc1 = "Before\u0019text to be replaced\u001Calso this\u001Dand this\u0017After"
        val range1 = Range(c("1.1.1/1.1.1/2.1.1"), c("1.1.1/2.1.1/1.1.1"))
        assertEquals("Before\u0019\u0017After", rangeReplace(doc1, range1, ""))

        val doc2 = "Before\u0001Library two\u0001Library three\u0001Library four"
        val range2 = Range(c("2.1.1/1.1.1/1.1.1"), c("3.1.1/1.1.1/1.1.1"))
        assertEquals("Before\u0001\u0001Library four", rangeReplace(doc2, range2, ""))
    }

    @Test
    fun testNextScroll() {
        val doc1 = "3A\u0017B2\u0018C1"
        val (update1, nextStart, remaining) = nextScroll(doc1, c("1.1.1/1.1.1/1.1.1"))
        assertEquals("1.1.1/1.1.1/1.1.1", update1.coord.toString())
        assertEquals("3A", update1.scroll)
        assertEquals("1.1.1/1.1.1/1.1.2", nextStart.toString())
        assertEquals("B2\u0018C1", remaining)
    }

    @Test
    fun testPhokenize() {
        val doc1 = "one\u0017two\u0017three\u0017four"
        val expected1 = listOf(
            PositionedScroll(c("1.1.1/1.1.1/1.1.1"), "one"),
            PositionedScroll(c("1.1.1/1.1.1/1.1.2"), "two"),
            PositionedScroll(c("1.1.1/1.1.1/1.1.3"), "three"),
            PositionedScroll(c("1.1.1/1.1.1/1.1.4"), "four"),
        )
        assertEquals(expected1, phokenize(doc1))

        val doc2 = "one\u0001two\u001Fthree\u001Efour\u001Dfive\u001Csix\u001Aseven\u0019eight\u0018nine\u0017ten"
        val expected2 = listOf(
            PositionedScroll(c("1.1.1/1.1.1/1.1.1"), "one"),
            PositionedScroll(c("2.1.1/1.1.1/1.1.1"), "two"),
            PositionedScroll(c("2.2.1/1.1.1/1.1.1"), "three"),
            PositionedScroll(c("2.2.2/1.1.1/1.1.1"), "four"),
            PositionedScroll(c("2.2.2/2.1.1/1.1.1"), "five"),
            PositionedScroll(c("2.2.2/2.2.1/1.1.1"), "six"),
            PositionedScroll(c("2.2.2/2.2.2/1.1.1"), "seven"),
            PositionedScroll(c("2.2.2/2.2.2/2.1.1"), "eight"),
            PositionedScroll(c("2.2.2/2.2.2/2.2.1"), "nine"),
            PositionedScroll(c("2.2.2/2.2.2/2.2.2"), "ten"),
        )
        assertEquals(expected2, phokenize(doc2))

        val doc3 = "one\u0017two\u0018three\u0019four\u001Afive\u001Csix\u001Dseven\u001Eeight\u001Fnine\u0001ten"
        val expected3 = listOf(
            PositionedScroll(c("1.1.1/1.1.1/1.1.1"), "one"),
            PositionedScroll(c("1.1.1/1.1.1/1.1.2"), "two"),
            PositionedScroll(c("1.1.1/1.1.1/1.2.1"), "three"),
            PositionedScroll(c("1.1.1/1.1.1/2.1.1"), "four"),
            PositionedScroll(c("1.1.1/1.1.2/1.1.1"), "five"),
            PositionedScroll(c("1.1.1/1.2.1/1.1.1"), "six"),
            PositionedScroll(c("1.1.1/2.1.1/1.1.1"), "seven"),
            PositionedScroll(c("1.1.2/1.1.1/1.1.1"), "eight"),
            PositionedScroll(c("1.2.1/1.1.1/1.1.1"), "nine"),
            PositionedScroll(c("2.1.1/1.1.1/1.1.1"), "ten"),
        )
        assertEquals(expected3, phokenize(doc3))

        val doc4 = "\u001A\u001C\u001D\u001E\u001F\u0001stuff here"
        val expected4 = listOf(
            PositionedScroll(c("2.1.1/1.1.1/1.1.1"), "stuff here"),
        )
        assertEquals(expected4, phokenize(doc4))
    }

    @Test
    fun testLastEmptyScroll() {
        // a regression discovered from SQ
        val doc1 = "hello\u0017world\u0017"
        val target1 = c("1.1.1/1.1.1/1.1.2")
        val parts1 = getSubspaceCoordinates(doc1.toByteArray(Charsets.UTF_8), target1)
        assertEquals(6, parts1.start)
        assertEquals(11, parts1.end)
        assertEquals(target1, parts1.best)
        assertEquals("world", fetch(doc1, target1))
    }

    @Test
    fun testMerge() {
        assertEquals("3A4C\u0017B2D1", merge("3A\u0017B2", "4C\u0017D1"))
        assertEquals(
            "Hello Darkness, my old friend.\u0017I've come to talk with you again.",
            merge("Hello \u0017I've come to talk", "Darkness, my old friend.\u0017 with you again."),
        )
        assertEquals(
            "One1\u0017Two2\u0018Three3\u0019Four4",
            merge("One\u0017Two\u0018Three\u0019Four", "1\u00172\u00183\u00194"),
        )
        assertEquals(
            "\u001Eprecursor here\u0001stuff hereand more",
            merge(
                "\u001A\u001C\u001D\u001E\u001F\u0001stuff here",
                "\u001A\u001C\u001D\u001Eprecursor here\u001F\u0001and more",
            ),
        )
        assertEquals(
            "\u0001\u0001 Library at 3.1.1/1.1.1/1.1.1 \u001F Shelf at 3.2.1/1.1.1/1.1.1\u0001 Library 4.1.1/1.1.1/1.1.1 \u001E Series at 4.1.2/1.1.1/1.1.1",
            merge(
                "\u0001\u0001 Library at 3.1.1/1.1.1/1.1.1 \u001F Shelf at 3.2.1/1.1.1/1.1.1",
                "\u0001\u0001\u0001 Library 4.1.1/1.1.1/1.1.1 \u001E Series at 4.1.2/1.1.1/1.1.1",
            ),
        )
        assertEquals(
            "\u001D Collection at 1.1.1/2.1.1/1.1.1\u001C Volume at 1.1.1/2.2.1/1.1.1\u001D Collection at 1.1.1/3.1.1/1.1.1\u001C Volume at 1.1.1/3.2.1/1.1.1",
            merge(
                "\u001D Collection at 1.1.1/2.1.1/1.1.1\u001C Volume at 1.1.1/2.2.1/1.1.1",
                "\u001D\u001D Collection at 1.1.1/3.1.1/1.1.1\u001C Volume at 1.1.1/3.2.1/1.1.1",
            ),
        )
        assertEquals(
            "\u001ABook #2 Part 1 + Part II\u001ABook #3 Part 1 + Part Deux",
            merge("\u001ABook #2 Part 1\u001ABook #3 Part 1", "\u001A + Part II\u001A + Part Deux"),
        )
        assertEquals("AA__\u0001BB__\u0001CC__", merge("AA\u0001BB\u0001CC", "__\u0001__\u0001__"))
    }

    @Test
    fun testSubtract() {
        assertEquals(
            "\u0017Scroll two.",
            subtract("Here's scroll one.\u0017Scroll two.", "Just content at the first scroll"),
        )
    }

    @Test
    fun testNormalize() {
        assertEquals("\u0017Scroll two", normalize("\u0017Scroll two\u0018\u0018\u0018\u0018"))
    }

    @Test
    fun testExpand() {
        var update = expand("nothing but line breaks\nto test expansion to scrolls\nline 3")
        assertEquals("nothing but line breaks\u0017to test expansion to scrolls\u0017line 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u0018to test expansion to scrolls\u0018line 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u0019to test expansion to scrolls\u0019line 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u001Ato test expansion to scrolls\u001Aline 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u001Cto test expansion to scrolls\u001Cline 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u001Dto test expansion to scrolls\u001Dline 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u001Eto test expansion to scrolls\u001Eline 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u001Fto test expansion to scrolls\u001Fline 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u0001to test expansion to scrolls\u0001line 3", update)
        update = expand(update)
        assertEquals("nothing but line breaks\u0001to test expansion to scrolls\u0001line 3", update)
    }

    @Test
    fun testContract() {
        val doc1 = "A more complex example than expand\u0001----\u001F++++\u001E____\u001Doooo\u001C====\u001Azzzz\u0019gggg\u0018....\u0017qqqq"
        val update1 = contract(doc1)
        assertEquals("A more complex example than expand\u001F----\u001E++++\u001D____\u001Coooo\u001A====\u0019zzzz\u0018gggg\u0017....\nqqqq", update1)
        val update2 = contract(update1)
        assertEquals("A more complex example than expand\u001E----\u001D++++\u001C____\u001Aoooo\u0019====\u0018zzzz\u0017gggg\n....\nqqqq", update2)
    }

    @Test
    fun testFsReadWrite() {
        val file = File.createTempFile("unit-test", ".phext")
        try {
            val initial = "a simple phext doc with three scrolls\u0017we just want to verify\u0017that all of our breaks are making it through rust's fs layer.\u0018section 2\u0019chapter 2\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2"
            file.writeText(initial, Charsets.UTF_8)
            val prior = file.readText(Charsets.UTF_8)
            assertEquals(initial, prior)
            val message = replace(prior, c("2.1.1/1.1.1/1.1.1"), "still lib 2")
            assertEquals("a simple phext doc with three scrolls\u0017we just want to verify\u0017that all of our breaks are making it through rust's fs layer.\u0018section 2\u0019chapter 2\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001still lib 2", message)
        } finally {
            file.delete()
        }
    }

    @Test
    fun testReplaceCreate() {
        val initial = "A\u0017B\u0017C\u0018D\u0019E\u001AF\u001CG\u001DH\u001EI\u001FJ\u0001K"
        val message = replace(initial, c("3.1.1/1.1.1/1.1.1"), "L")
        assertEquals("A\u0017B\u0017C\u0018D\u0019E\u001AF\u001CG\u001DH\u001EI\u001FJ\u0001K\u0001L", message)
    }

    @Test
    fun testSummary() {
        assertEquals("A short phext...", createSummary("A short phext\nSecond line\u0017second scroll............................."))
        assertEquals("very terse", createSummary("very terse"))
    }

    @Test
    fun testNavmap() {
        val example = "Just a couple of scrolls.\u0017Second scroll\u0017Third scroll"
        val result = navmap("http://127.0.0.1/api/v1/index/", example)
        assertEquals(
            "<ul>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.1\">1.1.1/1.1.1/1.1.1 Just a couple of scrolls.</a></li>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.2\">1.1.1/1.1.1/1.1.2 Second scroll</a></li>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.3\">1.1.1/1.1.1/1.1.3 Third scroll</a></li>\n</ul>\n",
            result,
        )
    }

    @Test
    fun testTextmap() {
        val example = "Just a couple of scrolls.\u0017Second scroll\u0017Third scroll"
        assertEquals(
            "* 1.1.1/1.1.1/1.1.1: Just a couple of scrolls.\n* 1.1.1/1.1.1/1.1.2: Second scroll\n* 1.1.1/1.1.1/1.1.3: Third scroll\n",
            textmap(example),
        )
    }

    @Test
    fun testLargerCoordinates() {
        val coord = c("111.222.333/444.555.666/777.888.999")
        val result = insert("", coord, "Hello World")
        val map = textmap(result)
        assertEquals(4997, result.toByteArray(Charsets.UTF_8).size)
        assertEquals("* 111.222.333/444.555.666/777.888.999: Hello World\n", map)
    }

    @Test
    fun testPhextIndex() {
        val example = "first scroll\u0017second scroll\u0018second section\u0019second chapter\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2"
        assertEquals("0\u001713\u001827\u001942\u001A57\u001C64\u001D73\u001E86\u001F95\u0001103", index(example))

        assertEquals(0, offset(example, c("1.1.1/1.1.1/1.1.1")))
        assertEquals(13, offset(example, c("1.1.1/1.1.1/1.1.2")))
        assertEquals(27, offset(example, c("1.1.1/1.1.1/1.2.1")))
        assertEquals(42, offset(example, c("1.1.1/1.1.1/2.1.1")))
        assertEquals(57, offset(example, c("1.1.1/1.1.2/1.1.1")))
        assertEquals(64, offset(example, c("1.1.1/1.2.1/1.1.1")))
        assertEquals(73, offset(example, c("1.1.1/2.1.1/1.1.1")))
        assertEquals(86, offset(example, c("1.1.2/1.1.1/1.1.1")))
        assertEquals(95, offset(example, c("1.2.1/1.1.1/1.1.1")))
        assertEquals(103, offset(example, c("2.1.1/1.1.1/1.1.1")))
        assertEquals(103, offset(example, c("2.1.1/1.1.1/1.2.1")))
        assertEquals(112, example.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun testScrollManifest() {
        val example = "first scroll\u0017second scroll\u0018second section\u0019second chapter\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2"
        val result = manifest(example)

        assertEquals("7e79edd92a62a048e1cd24ffab542e34", checksum("00000000000000000000"))
        assertEquals("ba9d944e4967e29d48bae69ac2999699", checksum("first scroll"))
        assertEquals("2fe1b2040314ac66f132dd3b4926157c", checksum("second scroll"))
        assertEquals("fddb6916753b6f4e0b5281469134778b", checksum("second section"))
        assertEquals("16ab5b1a0a997db95ec215a3bf2c57b3", checksum("second chapter"))
        assertEquals("0f20f79bf36f63e8fba25cc6765e2d0d", checksum("book 2"))
        assertEquals("7ead0c6fef43adb446fe3bda6fb0adc7", checksum("volume 2"))
        assertEquals("78c12298931c6edede92962137a9280a", checksum("collection 2"))
        assertEquals("0f35100c84df601a490b7b63d7e8c0a8", checksum("series 2"))
        assertEquals("3bbf7e67cb33d613a906bc5a3cbefd95", checksum("shelf 2"))
        assertEquals("2e7fdd387196a8a2706ccb9ad6792bc3", checksum("library 2"))

        // the expected phext: the hashes above joined by the rs separators, in order
        val seps = listOf("\u0017", "\u0018", "\u0019", "\u001A", "\u001C", "\u001D", "\u001E", "\u001F", "\u0001")
        val sb = StringBuilder()
        val hashes = listOf(
            "ba9d944e4967e29d48bae69ac2999699", "2fe1b2040314ac66f132dd3b4926157c",
            "fddb6916753b6f4e0b5281469134778b", "16ab5b1a0a997db95ec215a3bf2c57b3",
            "0f20f79bf36f63e8fba25cc6765e2d0d", "7ead0c6fef43adb446fe3bda6fb0adc7",
            "78c12298931c6edede92962137a9280a", "0f35100c84df601a490b7b63d7e8c0a8",
            "3bbf7e67cb33d613a906bc5a3cbefd95", "2e7fdd387196a8a2706ccb9ad6792bc3",
        )
        for (i in hashes.indices) {
            if (i > 0) sb.append(seps[i - 1])
            sb.append(hashes[i])
        }
        assertEquals(sb.toString(), result)
    }

    @Test
    fun testPhextSoundexV1() {
        val sample = "it was the best of scrolls\u0017it was the worst of scrolls\u0017aaa\u0017bbb\u0017ccc\u0017ddd\u0017eee\u0017fff\u0017ggg\u0017hhh\u0017iii\u0017jjj\u0017kkk\u0017lll\u0018mmm\u0018nnn\u0018ooo\u0018ppp\u0019qqq\u0019rrr\u0019sss\u0019ttt\u001Auuu\u001Avvv\u001Awww\u001Axxx\u001Ayyy\u001Azzz"
        val result = soundexV1(sample)
        assertEquals("36\u001741\u00171\u00174\u00177\u001710\u00171\u00174\u00177\u00171\u00171\u00177\u00177\u001713\u001816\u001816\u00181\u00184\u00197\u001919\u00197\u001910\u001A1\u001A4\u001A1\u001A7\u001A1\u001A7", result)
    }

    @Test
    fun testInsertPerformance2kScrolls() {
        val doc1 = "the quick brown fox jumped over the lazy dog"
        var x = 0
        val next = c("1.1.1/1.1.1/1.1.1")
        val result = mutableListOf("")

        val start = System.nanoTime()
        while (true) {
            x += 1
            if (x > 2000) break
            if (next.x.scroll > 32) next.sectionBreak()
            if (next.x.section > 32) next.chapterBreak()
            if (next.x.chapter > 32) next.bookBreak()
            result.add(insert(result[x - 1], next, doc1))
            next.scrollBreak()
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("Performance test took: $elapsedMs ms")
        assertTrue(elapsedMs < 5000, "took $elapsedMs ms")

        assertEquals(c("1.1.1/1.1.1/2.31.17"), next)
        assertEquals(44, doc1.length)

        var phextTokens = 0
        var scrollBreaks = 0
        var sectionBreaks = 0
        var chapterBreaks = 0
        val check = result.last().toByteArray(Charsets.UTF_8)
        for (byte in check) {
            val b = byte.toInt() and 0xFF
            if (isPhextBreak(b)) phextTokens += 1
            if (b == SCROLL_BREAK_U8) scrollBreaks += 1
            if (b == SECTION_BREAK_U8) sectionBreaks += 1
            if (b == CHAPTER_BREAK_U8) chapterBreaks += 1
        }
        assertEquals(1999, phextTokens)
        assertEquals(1937, scrollBreaks)
        assertEquals(61, sectionBreaks)
        assertEquals(1, chapterBreaks)
        assertEquals(2000 * 44 + 1999, check.size)
    }

    @Test
    fun testInsertPerformanceMediumScrolls() {
        val docTemplate = "the quick brown fox jumped over the lazy dog\n"
        val doc1 = docTemplate.repeat(1000)
        var x = 0
        val next = c("1.1.1/1.1.1/1.1.1")
        val result = mutableListOf("")

        val start = System.nanoTime()
        while (true) {
            x += 1
            if (x > 25) break
            if (next.x.scroll > 5) next.sectionBreak()
            if (next.x.section > 5) next.chapterBreak()
            if (next.x.chapter > 5) next.bookBreak()
            result.add(insert(result[x - 1], next, doc1))
            next.scrollBreak()
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("Performance test took: $elapsedMs ms")
        assertTrue(elapsedMs < 1000, "took $elapsedMs ms")

        assertEquals(c("1.1.1/1.1.1/1.5.6"), next)
        assertEquals(45000, doc1.toByteArray(Charsets.UTF_8).size)

        var phextTokens = 0
        var lineBreaks = 0
        var scrollBreaks = 0
        var sectionBreaks = 0
        var chapterBreaks = 0
        val check = result.last().toByteArray(Charsets.UTF_8)
        for (byte in check) {
            val b = byte.toInt() and 0xFF
            if (isPhextBreak(b)) phextTokens += 1
            if (b == LINE_BREAK_U8) lineBreaks += 1
            if (b == SCROLL_BREAK_U8) scrollBreaks += 1
            if (b == SECTION_BREAK_U8) sectionBreaks += 1
            if (b == CHAPTER_BREAK_U8) chapterBreaks += 1
        }
        assertEquals(25024, phextTokens)
        assertEquals(25000, lineBreaks)
        assertEquals(20, scrollBreaks)
        assertEquals(4, sectionBreaks)
        assertEquals(0, chapterBreaks)
        assertEquals(25 * (45000 - 1000) + 25024, check.size)
    }

    @Test
    fun testNewOperators() {
        val ps = PositionedScroll.newInstance()
        assertEquals("", ps.scroll)
        assertEquals(c("1.1.1/1.1.1/1.1.1"), ps.coord)

        val coord = Coordinate.newInstance()
        assertEquals(ZCoordinate(), coord.z)
        assertEquals(YCoordinate(), coord.y)
        assertEquals(XCoordinate(), coord.x)

        val range = Range.newInstance()
        assertEquals(Coordinate.newInstance(), range.start)
        assertEquals(Coordinate.newInstance(), range.end)

        val zc = ZCoordinate()
        assertEquals(1L, zc.library)
        assertEquals(1L, zc.shelf)
        assertEquals(1L, zc.series)

        val yc = YCoordinate()
        assertEquals(1L, yc.collection)
        assertEquals(1L, yc.volume)
        assertEquals(1L, yc.book)

        val xc = XCoordinate()
        assertEquals(1L, xc.chapter)
        assertEquals(1L, xc.section)
        assertEquals(1L, xc.scroll)
    }

    @Test
    fun testHashSupport() {
        val stuff = explode("hello world\u0017\u0017\u0017scroll 4\u0001Library 2").toMutableMap()
        val scroll1Address = c("1.1.1/1.1.1/1.1.1")
        val scroll2Address = c("1.1.1/1.1.1/1.1.4")
        val scroll3Address = c("2.1.1/1.1.1/1.1.1")
        assertEquals("hello world", stuff[scroll1Address])
        assertEquals("scroll 4", stuff[scroll2Address])
        assertEquals("Library 2", stuff[scroll3Address])

        val scroll4Address = c("2.3.4/5.6.7/8.9.1")
        stuff[scroll4Address] = "random insertion"

        val serialized = implode(stuff)
        assertEquals(
            "hello world\u0017\u0017\u0017scroll 4\u0001Library 2\u001F\u001F\u001E\u001E\u001E\u001D\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018random insertion",
            serialized,
        )
    }

    @Test
    fun testSubspaceFilter() {
        // the rs test is a design note only (TODO: create a suite of test
        // filters and verify they select properly) — kept for parity.
    }

    @Test
    fun testEmojiInScroll() {
        val coord = c("1.1.1/1.1.1/1.1.1")
        val content = "Lux 🔆 test"
        val update = insert("", coord, content)
        assertEquals(content, fetch(update, coord))
    }

    @Test
    fun testCoordinateAbove999ParsePrintRoundTrip() {
        val address = "1000.1001.1002/1003.1004.1005/1006.1007.1008"
        val coord = toCoordinate(address)
        assertEquals(1000L, coord.z.library)
        assertEquals(1001L, coord.z.shelf)
        assertEquals(1002L, coord.z.series)
        assertEquals(1003L, coord.y.collection)
        assertEquals(1004L, coord.y.volume)
        assertEquals(1005L, coord.y.book)
        assertEquals(1006L, coord.x.chapter)
        assertEquals(1007L, coord.x.section)
        assertEquals(1008L, coord.x.scroll)
        assertTrue(coord.validateCoordinate())
        assertEquals(address, coord.toString())
        assertEquals("1000.1001.1002;1003.1004.1005;1006.1007.1008", coord.toUrlencoded())

        val parsed = Coordinate.tryParse(address)
        assertNotNull(parsed)
        assertEquals(coord, parsed)
        assertEquals(address, parsed!!.toString())

        // 0 components are still invalid, exactly as before the cap was removed
        val zero = toCoordinate("0.1.1/1.1.1/1.1.1")
        assertFalse(zero.validateCoordinate())
        assertEquals("", zero.toString())
    }

    @Test
    fun testScrollBreaksAdvancePast999() {
        // 1001 scrolls in a row: breaks advance 998 -> 999 -> 1000 -> 1001 without saturating
        val doc = StringBuilder()
        for (i in 1..1001) {
            if (i > 1) doc.append(SCROLL_BREAK)
            doc.append("s$i")
        }
        val text = doc.toString()
        for (scroll in listOf(998L, 999L, 1000L, 1001L)) {
            val coord = c("1.1.1/1.1.1/1.1.$scroll")
            assertEquals("s$scroll", fetch(text, coord), "fetch at scroll $scroll")
        }
        val parts = phokenize(text)
        assertEquals(1001, parts.size)
        assertEquals(998L, parts[997].coord.x.scroll)
        assertEquals(999L, parts[998].coord.x.scroll)
        assertEquals(1000L, parts[999].coord.x.scroll)
        assertEquals(1001L, parts[1000].coord.x.scroll)
    }

    @Test
    fun testFetchAndExplodeAbove999() {
        val coord = c("1.1.1/1.1.1/1000.1.1")
        val doc = replace("", coord, "beyond the cap")
        assertEquals("beyond the cap", fetch(doc, coord))

        val map = explode(doc)
        assertEquals("beyond the cap", map[coord])

        val first = c("1.1.1/1.1.1/1.1.1")
        assertEquals("", fetch(doc, first))
    }

    @Test
    fun testEmojiBoundaryCrash() {
        val padding = "a".repeat(29) // bytes 0-28
        val text = padding + "🔆" // emoji at bytes 29-32
        val summary = createSummary(text)
        assertTrue(summary.isNotEmpty())
    }

    @Test
    fun testTryFromValidAddressesRoundTrip() {
        val root = Coordinate.tryParse("1.1.1/1.1.1/1.1.1")
        assertNotNull(root)
        assertEquals("1.1.1/1.1.1/1.1.1", root!!.toString())

        val mixed = Coordinate.tryParse("98.76.54/32.10.1/23.45.67")
        assertNotNull(mixed)
        assertEquals("98.76.54/32.10.1/23.45.67", mixed!!.toString())

        // the largest component a Long can hold still parses
        // (Rust uses usize/unsigned 64-bit; Kotlin components are Long)
        val biggest = "${Long.MAX_VALUE}.1.1/1.1.1/1.1.1"
        val parsed = Coordinate.tryParse(biggest)
        assertNotNull(parsed)
        assertEquals(Long.MAX_VALUE, parsed!!.z.library)
    }

    @Test
    fun testTryFromMalformedComponentsReturnErr() {
        // every case below used to panic inside TryFrom in old rs versions;
        // each must now fail the parse (null here). The nine cases cover
        // all nine component positions in turn.
        val cases = listOf(
            "a.1.1/1.1.1/1.1.1", // library: alphabetic
            "1..1/1.1.1/1.1.1", // shelf: empty
            "1.1.-1/1.1.1/1.1.1", // series: negative
            "1.1.1/999999999999999999999999999999.1.1/1.1.1", // collection: overflow (30 digits)
            "1.1.1/1.x.1/1.1.1", // volume: alphabetic
            "1.1.1/1.1./1.1.1", // book: empty
            "1.1.1/1.1.1/-2.1.1", // chapter: negative
            "1.1.1/1.1.1/1.999999999999999999999999999999.1", // section: overflow (30 digits)
            "1.1.1/1.1.1/1.1.z", // scroll: alphabetic
        )
        for (address in cases) {
            assertNull(Coordinate.tryParse(address), "expected parse failure for $address")
        }
    }

    @Test
    fun testTryFromComponentSpellings() {
        // Rust's integer parser accepts a leading '+': value as usual
        val plus = Coordinate.tryParse("+1.1.1/1.1.1/1.1.1")
        assertNotNull(plus)
        assertEquals("1.1.1/1.1.1/1.1.1", plus!!.toString())

        // whitespace is not numeric: failure, not a panic
        assertNull(Coordinate.tryParse(" 1.1.1/1.1.1/1.1.1"))
        assertNull(Coordinate.tryParse("1.1.1/1.1.1/1.1.1 "))
        assertNull(Coordinate.tryParse("1.1.1/1. 1.1/1.1.1"))
    }

    @Test
    fun testTryFromBadShapesReturnErr() {
        assertNull(Coordinate.tryParse("1.1.1/1.1.1"))
        assertNull(Coordinate.tryParse("1.1/1.1.1/1.1.1"))
        assertNull(Coordinate.tryParse("1.1.1.1/1.1.1/1.1.1"))
        assertNull(Coordinate.tryParse(""))
    }

    @Test
    fun testToCoordinateLongDigitRunSaturates() {
        // a digit run far beyond u64 saturates (Long.MAX_VALUE here;
        // usize::MAX in Rust) instead of overflowing
        val coord = toCoordinate("999999999999999999999999999999999.1.1/1.1.1/1.1.1")
        assertEquals(Long.MAX_VALUE, coord.z.library)
    }
}
