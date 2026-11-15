package io.phext.libphext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * XXH3-128 cross-checks for [checksum]: the libphext-rs manifest vectors
 * (also asserted in PhextTest.testScrollManifest) plus deterministic
 * pseudo-random inputs at every length class boundary (0-16, 17-128,
 * 129-240, and the long-input stripe loop), generated with the reference
 * xxhash implementation (Python `xxhash`, the same library the Python
 * port uses — its hexdigest matches the rs vectors above).
 */
class Xxh3Test {

    private fun gen(n: Int): ByteArray {
        var state = 0x0123456789abcdefL
        val out = ByteArray(n)
        for (i in 0 until n) {
            state = state * 6364136223846793005L + 1442695040888963407L
            out[i] = ((state ushr 33) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun testXxh3AcrossLengthClasses() {
        val vectors = mapOf(
            0 to "99aa06d3014798d86001c324468d497f",
            1 to "f1f63042c4d8f4690005b2e934ceef7d",
            2 to "ca71550a85ed0609cd7debfd355ddef9",
            3 to "89b8d893321b65ab4a54a0c5c6c2037e",
            4 to "b162323e73232d47e1ef5c60d432ec8c",
            5 to "affafbd05a1bde4f7f785191733a20c4",
            8 to "e093eb66733d412e71ca7ab886075041",
            9 to "4ee07916ca42cf49c99ab8d2fd2fc347",
            16 to "26a0d36038ef92e4c47e88d97f5f543e",
            17 to "c9f8330d488d0f49b04e2dd0c31a3ca9",
            32 to "c66776a7cf11eef527c9edea0aefd464",
            64 to "6ad27ac3db94ad385bdd99ea9081c91b",
            100 to "f817c8d67872cd92b3f31c6ac00339c9",
            128 to "7f95eaacdf6a70ac69b98620893329e6",
            129 to "36e12b2421c4e92beaa1ccad9b45662a",
            200 to "6f4c60bd7949ba99b823257e5651bebb",
            240 to "2a299c950573829de6a986953ac2a12d",
            241 to "54cfc21f2fbb3cd212e6c4065f4043d1",
            300 to "ecb69d659bf406233e6bda6a1efc29bd",
            1024 to "5ab1af5be7c4805a5eaae4c39c5bbc0b",
            4097 to "2ff3bfa71802d01188948ac4a55560ad",
        )
        for ((len, expected) in vectors) {
            val input = gen(len)
            val actual = Xxh3.checksumHex(input)
            assertEquals(expected, actual, "xxh3-128 mismatch at length $len")
        }
    }
}
