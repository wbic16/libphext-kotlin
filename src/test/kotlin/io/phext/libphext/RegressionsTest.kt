package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Port of libphext-rs `src/regressions.rs` (1 test). Fixture files live in tests/, as in the other ports. */
class RegressionsTest {

    @Test
    fun heliosStalledParse() {
        val helios = File("tests/regression-1-helios.phext").readText(Charsets.UTF_8)
        val coord = toCoordinate("1.1.1/1.1.1/1.1.1")
        val result = fetch(helios, coord)
        val actual = result.toByteArray(Charsets.UTF_8).size
        println("Helios: $actual")
        // The fixture's byte length depends on line endings: an LF checkout
        // gives 358, a CRLF (Windows) checkout gives 370. Both are correct.
        assertTrue(
            actual == 358 || actual == 370,
            "unexpected first-scroll length: $actual",
        )

        val msg = File("tests/regression-1-msg.txt").readText(Charsets.UTF_8)
        val pushCoord = toCoordinate("2.1.100/1.1.1/1.1.1")
        val updated = replace(helios, pushCoord, msg)
        println("Update: ${msg.toByteArray(Charsets.UTF_8).size}")
        val actualUpdated = updated.toByteArray(Charsets.UTF_8).size
        // LF gives 15150, CRLF gives 15317.
        assertTrue(
            actualUpdated == 15150 || actualUpdated == 15317,
            "unexpected replaced length: $actualUpdated",
        )
    }
}
