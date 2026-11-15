package io.phext.libphext

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZ
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The xz seal: canonical compression for a finished phext, mirroring
 * libphext-rs 0.6.0 `src/seal.rs`.
 *
 * The seal is a standard xz stream over the phext's exact bytes. This
 * implementation encodes with XZ for Java (org.tukaani:xz — a pure-Java
 * port of XZ Utils) at preset 6 with a CRC64 check, the same encoder
 * choices as the reference's xz2/liblzma encoder. Decoders must accept
 * any valid xz stream; the preset is an encoder choice, not a format
 * requirement.
 */
public object Seal {

    /** The xz preset this implementation seals with. */
    public const val SEAL_PRESET: Int = 6

    /** Seals a phext: xz-compresses its full UTF-8 bytes. */
    @JvmStatic
    public fun seal(input: String): ByteArray = sealBytes(input.toByteArray(Charsets.UTF_8))

    /** Seals raw phext bytes (see [seal]). */
    @JvmStatic
    public fun sealBytes(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val options = LZMA2Options(SEAL_PRESET)
        XZOutputStream(out, options, XZ.CHECK_CRC64).use { xz ->
            xz.write(input)
        }
        return out.toByteArray()
    }

    /**
     * Unseals a sealed phext back to its exact original bytes.
     * @throws IOException when the data is not a valid xz stream.
     */
    @JvmStatic
    public fun unseal(data: ByteArray): ByteArray {
        XZInputStream(ByteArrayInputStream(data)).use { xz ->
            return xz.readBytes()
        }
    }

    /**
     * Unseals directly to text.
     * @throws IOException when the data is not a valid xz stream, or when
     * the unsealed bytes are not valid UTF-8. (For foreign content that
     * may carry non-UTF-8 scrolls, [unseal] to bytes and pass them
     * through [Binary.ingest] instead.)
     */
    @JvmStatic
    public fun unsealString(data: ByteArray): String {
        val bytes = unseal(data)
        return decodeUtf8Strict(bytes)
            ?: throw IOException("unsealed bytes are not valid UTF-8")
    }
}
