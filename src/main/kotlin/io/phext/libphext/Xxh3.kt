package io.phext.libphext

import java.math.BigInteger

/**
 * XXH3-128 (the default-seed, default-secret variant), implemented in pure
 * Kotlin from the xxHash reference algorithm (xxhash.h, v0.8.x) so that
 * [checksum] matches libphext-rs `xxh3_128` byte-for-byte at every input
 * length: 0-16, 17-128, 129-240, and the long-input stripe loop.
 *
 * All arithmetic is unsigned 64-bit, carried in [Long] with wrap-around
 * overflow (identical to Rust/C `u64` arithmetic on the JVM).
 */
internal object Xxh3 {

    private const val PRIME32_1: Long = 0x9E3779B1L
    private const val PRIME32_2: Long = 0x85EBCA77L
    private const val PRIME32_3: Long = 0xC2B2AE3DL

    private val PRIME64_1: Long = 0x9E3779B185EBCA87uL.toLong()
    private val PRIME64_2: Long = 0xC2B2AE3D27D4EB4FuL.toLong()
    private val PRIME64_3: Long = 0x165667B19E3779F9uL.toLong()
    private val PRIME64_4: Long = 0x85EBCA77C2B2AE63uL.toLong()
    private val PRIME64_5: Long = 0x27D4EB2F165667C5uL.toLong()

    private val PRIME_MX1: Long = 0x165667919E3779F9uL.toLong()
    private val PRIME_MX2: Long = 0x9FB21C651E98DF25uL.toLong()

    // XXH3_kSecret (192 bytes), from xxhash.h
    private val SECRET: ByteArray = byteArrayOf(
        0xb8.toByte(), 0xfe.toByte(), 0x6c, 0x39, 0x23, 0xa4.toByte(), 0x4a, 0x9b.toByte(),
        0x7c, 0x01, 0x81.toByte(), 0x2c, 0xf7.toByte(), 0x21, 0xad.toByte(), 0x1c,
        0xde.toByte(), 0xd4.toByte(), 0x6d, 0xe9.toByte(), 0x83.toByte(), 0x90.toByte(), 0x97.toByte(), 0xdb.toByte(),
        0x72, 0x40, 0xa4.toByte(), 0xa4.toByte(), 0xb7.toByte(), 0xb3.toByte(), 0x67, 0x1f,
        0xcb.toByte(), 0x79, 0xe6.toByte(), 0x4e, 0xcc.toByte(), 0xc0.toByte(), 0xe5.toByte(), 0x78,
        0x82.toByte(), 0x5a, 0xd0.toByte(), 0x7d, 0xcc.toByte(), 0xff.toByte(), 0x72, 0x21,
        0xb8.toByte(), 0x08, 0x46, 0x74, 0xf7.toByte(), 0x43, 0x24, 0x8e.toByte(),
        0xe0.toByte(), 0x35, 0x90.toByte(), 0xe6.toByte(), 0x81.toByte(), 0x3a, 0x26, 0x4c,
        0x3c, 0x28, 0x52, 0xbb.toByte(), 0x91.toByte(), 0xc3.toByte(), 0x00, 0xcb.toByte(),
        0x88.toByte(), 0xd0.toByte(), 0x65, 0x8b.toByte(), 0x1b, 0x53, 0x2e, 0xa3.toByte(),
        0x71, 0x64, 0x48, 0x97.toByte(), 0xa2.toByte(), 0x0d, 0xf9.toByte(), 0x4e,
        0x38, 0x19, 0xef.toByte(), 0x46, 0xa9.toByte(), 0xde.toByte(), 0xac.toByte(), 0xd8.toByte(),
        0xa8.toByte(), 0xfa.toByte(), 0x76, 0x3f, 0xe3.toByte(), 0x9c.toByte(), 0x34, 0x3f,
        0xf9.toByte(), 0xdc.toByte(), 0xbb.toByte(), 0xc7.toByte(), 0xc7.toByte(), 0x0b, 0x4f, 0x1d,
        0x8a.toByte(), 0x51, 0xe0.toByte(), 0x4b, 0xcd.toByte(), 0xb4.toByte(), 0x59, 0x31,
        0xc8.toByte(), 0x9f.toByte(), 0x7e, 0xc9.toByte(), 0xd9.toByte(), 0x78, 0x73, 0x64,
        0xea.toByte(), 0xc5.toByte(), 0xac.toByte(), 0x83.toByte(), 0x34, 0xd3.toByte(), 0xeb.toByte(), 0xc3.toByte(),
        0xc5.toByte(), 0x81.toByte(), 0xa0.toByte(), 0xff.toByte(), 0xfa.toByte(), 0x13, 0x63, 0xeb.toByte(),
        0x17, 0x0d, 0xdd.toByte(), 0x51, 0xb7.toByte(), 0xf0.toByte(), 0xda.toByte(), 0x49,
        0xd3.toByte(), 0x16, 0x55, 0x26, 0x29, 0xd4.toByte(), 0x68, 0x9e.toByte(),
        0x2b, 0x16, 0xbe.toByte(), 0x58, 0x7d, 0x47, 0xa1.toByte(), 0xfc.toByte(),
        0x8f.toByte(), 0xf8.toByte(), 0xb8.toByte(), 0xd1.toByte(), 0x7a, 0xd0.toByte(), 0x31, 0xce.toByte(),
        0x45, 0xcb.toByte(), 0x3a, 0x8f.toByte(), 0x95.toByte(), 0x16, 0x04, 0x28,
        0xaf.toByte(), 0xd7.toByte(), 0xfb.toByte(), 0xca.toByte(), 0xbb.toByte(), 0x4b, 0x40, 0x7e,
    )

    private const val STRIPE_LEN = 64
    private const val SECRET_CONSUME_RATE = 8
    private const val SECRET_MERGEACCS_START = 11
    private const val SECRET_LASTACC_START = 7
    private const val MIDSIZE_STARTOFFSET = 3
    private const val MIDSIZE_LASTOFFSET = 17
    private const val SECRET_SIZE_MIN = 136

    /** The 128-bit hash as (low64, high64), matching XXH128_hash_t field order. */
    fun hash128(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        return when {
            len <= 16 -> len0to16(input)
            len <= 128 -> len17to128(input)
            len <= 240 -> len129to240(input)
            else -> hashLong(input)
        }
    }

    /** libphext-rs `checksum`: the u128 (high<<64 | low) as 32 lowercase hex digits. */
    fun checksumHex(input: ByteArray): String {
        val (low, high) = hash128(input)
        fun hex16(v: Long): String {
            val s = java.lang.Long.toUnsignedString(v, 16)
            return "0".repeat(16 - s.length) + s
        }
        return hex16(high) + hex16(low)
    }

    private val MASK64: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)

    // -- primitives ---------------------------------------------------------

    private fun readLE64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun readLE32(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun readLE64Secret(off: Int): Long = readLE64(SECRET, off)
    private fun readLE32Secret(off: Int): Long = readLE32(SECRET, off)

    private fun swap64(v: Long): Long = java.lang.Long.reverseBytes(v)
    private fun swap32(v: Long): Long =
        (java.lang.Integer.reverseBytes((v and 0xFFFFFFFFL).toInt()).toLong() and 0xFFFFFFFFL)

    private fun rotl64(v: Long, r: Int): Long = (v shl r) or (v ushr (64 - r))
    private fun rotl32(v: Long, r: Int): Long {
        val x = v and 0xFFFFFFFFL
        return ((x shl r) or (x ushr (32 - r))) and 0xFFFFFFFFL
    }

    private fun xorshift64(v: Long, shift: Int): Long = v xor (v ushr shift)

    /** 64x64 -> 128 multiply, returned as (low, high). */
    private fun mult64to128(lhs: Long, rhs: Long): Pair<Long, Long> {
        val product = BigInteger.valueOf(lhs).and(MASK64)
            .multiply(BigInteger.valueOf(rhs).and(MASK64))
        val low = product.and(MASK64).toLong()
        val high = product.shiftRight(64).toLong()
        return low to high
    }

    private fun mul128Fold64(lhs: Long, rhs: Long): Long {
        val (low, high) = mult64to128(lhs, rhs)
        return low xor high
    }

    private fun xxh64Avalanche(h: Long): Long {
        var v = h
        v = v xor (v ushr 33)
        v *= PRIME64_2
        v = v xor (v ushr 29)
        v *= PRIME64_3
        v = v xor (v ushr 32)
        return v
    }

    private fun xxh3Avalanche(h: Long): Long {
        var v = xorshift64(h, 37)
        v *= PRIME_MX1
        v = xorshift64(v, 32)
        return v
    }

    private fun xxh3Rrmxmx(h: Long, len: Long): Long {
        var v = h
        v = v xor rotl64(v, 49) xor rotl64(v, 24)
        v *= PRIME_MX2
        v = v xor ((v ushr 35) + len)
        v *= PRIME_MX2
        return xorshift64(v, 28)
    }

    private fun mix16B(input: ByteArray, inOff: Int, secretOff: Int, seed: Long): Long {
        val inputLo = readLE64(input, inOff)
        val inputHi = readLE64(input, inOff + 8)
        return mul128Fold64(
            inputLo xor (readLE64Secret(secretOff) + seed),
            inputHi xor (readLE64Secret(secretOff + 8) - seed),
        )
    }

    // -- short inputs -------------------------------------------------------

    private fun len0to16(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        if (len > 8) return len9to16(input)
        if (len >= 4) return len4to8(input)
        if (len > 0) return len1to3(input)
        val bitflipL = readLE64Secret(64) xor readLE64Secret(72)
        val bitflipH = readLE64Secret(80) xor readLE64Secret(88)
        return xxh64Avalanche(bitflipL) to xxh64Avalanche(bitflipH)
    }

    private fun len1to3(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        val c1 = input[0].toLong() and 0xFF
        val c2 = input[len shr 1].toLong() and 0xFF
        val c3 = input[len - 1].toLong() and 0xFF
        val combinedL = ((c1 shl 16) or (c2 shl 24) or c3 or (len.toLong() shl 8)) and 0xFFFFFFFFL
        val combinedH = rotl32(swap32(combinedL), 13)
        val bitflipL = (readLE32Secret(0) xor readLE32Secret(4))
        val bitflipH = (readLE32Secret(8) xor readLE32Secret(12))
        val keyedLo = combinedL xor bitflipL
        val keyedHi = combinedH xor bitflipH
        return xxh64Avalanche(keyedLo) to xxh64Avalanche(keyedHi)
    }

    private fun len4to8(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        var seed = 0L
        seed = seed xor (swap32(seed) shl 32)
        val inputLo = readLE32(input, 0)
        val inputHi = readLE32(input, len - 4)
        val input64 = inputLo + (inputHi shl 32)
        val bitflip = (readLE64Secret(16) xor readLE64Secret(24)) + seed
        val keyed = input64 xor bitflip
        var (low, high) = mult64to128(keyed, PRIME64_1 + (len.toLong() shl 2))
        high += (low shl 1)
        low = low xor (high ushr 3)
        low = xorshift64(low, 35)
        low *= PRIME_MX2
        low = xorshift64(low, 28)
        high = xxh3Avalanche(high)
        return low to high
    }

    private fun len9to16(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        val bitflipL = (readLE64Secret(32) xor readLE64Secret(40))
        val bitflipH = (readLE64Secret(48) xor readLE64Secret(56))
        val inputLo = readLE64(input, 0)
        var inputHi = readLE64(input, len - 8)
        var (low, high) = mult64to128(inputLo xor inputHi xor bitflipL, PRIME64_1)
        low += (len - 1).toLong() shl 54
        inputHi = inputHi xor bitflipH
        high += inputHi + ((inputHi and 0xFFFFFFFFL) * (PRIME32_2 - 1))
        low = low xor swap64(high)
        val h = mult64to128(low, PRIME64_2)
        val hLow = h.first
        val hHigh = h.second + high * PRIME64_2
        return xxh3Avalanche(hLow) to xxh3Avalanche(hHigh)
    }

    // -- mid inputs ---------------------------------------------------------

    private fun mix32B(
        accLow: Long, accHigh: Long,
        input: ByteArray, in1: Int, in2: Int,
        secretOff: Int, seed: Long,
    ): Pair<Long, Long> {
        var low = accLow
        var high = accHigh
        low += mix16B(input, in1, secretOff + 0, seed)
        low = low xor (readLE64(input, in2) + readLE64(input, in2 + 8))
        high += mix16B(input, in2, secretOff + 16, seed)
        high = high xor (readLE64(input, in1) + readLE64(input, in1 + 8))
        return low to high
    }

    private fun finalizeMid(accLow: Long, accHigh: Long, len: Int, seed: Long): Pair<Long, Long> {
        var low = accLow + accHigh
        var high = (accLow * PRIME64_1) + (accHigh * PRIME64_4) +
            ((len - seed) * PRIME64_2)
        low = xxh3Avalanche(low)
        high = -xxh3Avalanche(high)
        return low to high
    }

    private fun len17to128(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        var accLow = len.toLong() * PRIME64_1
        var accHigh = 0L
        if (len > 32) {
            if (len > 64) {
                if (len > 96) {
                    val r = mix32B(accLow, accHigh, input, 48, len - 64, 96, 0)
                    accLow = r.first; accHigh = r.second
                }
                val r = mix32B(accLow, accHigh, input, 32, len - 48, 64, 0)
                accLow = r.first; accHigh = r.second
            }
            val r = mix32B(accLow, accHigh, input, 16, len - 32, 32, 0)
            accLow = r.first; accHigh = r.second
        }
        val r = mix32B(accLow, accHigh, input, 0, len - 16, 0, 0)
        accLow = r.first; accHigh = r.second
        return finalizeMid(accLow, accHigh, len, 0)
    }

    private fun len129to240(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        var accLow = len.toLong() * PRIME64_1
        var accHigh = 0L
        var i = 32
        while (i < 160) {
            val r = mix32B(accLow, accHigh, input, i - 32, i - 16, i - 32, 0)
            accLow = r.first; accHigh = r.second
            i += 32
        }
        accLow = xxh3Avalanche(accLow)
        accHigh = xxh3Avalanche(accHigh)
        while (i <= len) {
            val r = mix32B(
                accLow, accHigh, input, i - 32, i - 16,
                MIDSIZE_STARTOFFSET + i - 160, 0,
            )
            accLow = r.first; accHigh = r.second
            i += 32
        }
        val r = mix32B(
            accLow, accHigh, input, len - 16, len - 32,
            SECRET_SIZE_MIN - MIDSIZE_LASTOFFSET - 16, 0, // seed negated below via mix with -seed: seed is 0
        )
        // NB: the reference passes seed = -seed64 = 0 here, so the call above is exact.
        accLow = r.first; accHigh = r.second
        return finalizeMid(accLow, accHigh, len, 0)
    }

    // -- long inputs --------------------------------------------------------

    private fun accumulate512(acc: LongArray, input: ByteArray, inOff: Int, secretOff: Int) {
        for (lane in 0 until 8) {
            val dataVal = readLE64(input, inOff + lane * 8)
            val dataKey = dataVal xor readLE64Secret(secretOff + lane * 8)
            acc[lane xor 1] += dataVal
            acc[lane] += (dataKey and 0xFFFFFFFFL) * (dataKey ushr 32)
        }
    }

    private fun scrambleAcc(acc: LongArray, secretOff: Int) {
        for (lane in 0 until 8) {
            val key64 = readLE64Secret(secretOff + lane * 8)
            var acc64 = acc[lane]
            acc64 = acc64 xor (acc64 ushr 47)
            acc64 = acc64 xor key64
            acc64 *= PRIME32_2
            acc[lane] = acc64
        }
    }

    private fun mergeAccs(acc: LongArray, secretOff: Int, start: Long): Long {
        var result = start
        for (i in 0 until 4) {
            result += mul128Fold64(
                acc[2 * i] xor readLE64Secret(secretOff + 16 * i),
                acc[2 * i + 1] xor readLE64Secret(secretOff + 16 * i + 8),
            )
        }
        return xxh3Avalanche(result)
    }

    private fun hashLong(input: ByteArray): Pair<Long, Long> {
        val len = input.size
        val acc = longArrayOf(
            PRIME32_3, PRIME64_1, PRIME64_2, PRIME64_3,
            PRIME64_4, PRIME32_2, PRIME64_5, PRIME32_1,
        )
        val secretSize = SECRET.size
        val nbStripesPerBlock = (secretSize - STRIPE_LEN) / SECRET_CONSUME_RATE
        val blockLen = STRIPE_LEN * nbStripesPerBlock
        val nbBlocks = (len - 1) / blockLen

        for (n in 0 until nbBlocks) {
            // accumulate block
            var s = 0
            while (s < nbStripesPerBlock) {
                accumulate512(acc, input, n * blockLen + s * STRIPE_LEN, s * SECRET_CONSUME_RATE)
                s++
            }
            scrambleAcc(acc, secretSize - STRIPE_LEN)
        }

        val nbStripes = ((len - 1) - (blockLen * nbBlocks)) / STRIPE_LEN
        var s = 0
        while (s < nbStripes) {
            accumulate512(acc, input, nbBlocks * blockLen + s * STRIPE_LEN, s * SECRET_CONSUME_RATE)
            s++
        }
        // last stripe
        accumulate512(
            acc, input, len - STRIPE_LEN,
            secretSize - STRIPE_LEN - SECRET_LASTACC_START,
        )

        val low = mergeAccs(acc, SECRET_MERGEACCS_START, len.toLong() * PRIME64_1)
        val high = mergeAccs(
            acc,
            secretSize - STRIPE_LEN - SECRET_MERGEACCS_START,
            (len.toLong() * PRIME64_2).inv(),
        )
        return low to high
    }
}
