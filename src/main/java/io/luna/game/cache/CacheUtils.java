package io.luna.game.cache;

import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Cache-specific buffer utilities and common decoding primitives used by #317/#377 data formats.
 * <p>
 * This includes:
 * <ul>
 *   <li>“Smart” integer decoding used in many cache streams</li>
 *   <li>24-bit MEDIUM reads used in index/archive headers</li>
 *   <li>RS2 newline-terminated string decoding</li>
 *   <li>GZIP decompression for certain cache containers</li>
 *   <li>Jagex file-name hashing used by named archives</li>
 * </ul>
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class CacheUtils {

    /**
     * Region (map) size in tiles (typically 64).
     */
    public static final int MAP_SIZE = Region.SIZE;

    /**
     * Maximum plane count (exclusive).
     */
    public static final int MAP_PLANES = Position.HEIGHT_LEVELS.upperEndpoint();

    /**
     * Reads a “smart” value.
     * <p>
     * Smart encoding uses either 1 or 2 bytes:
     * <ul>
     *   <li>If the next unsigned byte is {@code <= 127}, the value is that byte.</li>
     *   <li>Otherwise the value is an unsigned short with {@code 32768} subtracted.</li>
     * </ul>
     *
     * @param buf The buffer to read from.
     * @return The decoded smart value.
     */
    public static int readSmart(ByteBuf buf) {
        int peek = buf.getUnsignedByte(buf.readerIndex());
        if (peek <= Byte.MAX_VALUE) {
            return buf.readUnsignedByte();
        }
        return buf.readUnsignedShort() + Short.MIN_VALUE;
    }

    /**
     * Reads a 24-bit unsigned MEDIUM value (3 bytes, big-endian).
     *
     * @param buf The buffer to read from.
     * @return The decoded integer in the range {@code 0..16777215}.
     */
    public static int readMedium(ByteBuf buf) {
        return (buf.readUnsignedByte() << 16) | (buf.readUnsignedByte() << 8) | buf.readUnsignedByte();
    }

    /**
     * Reads an RS2 string (newline-terminated, byte value {@code 10}).
     * <p>
     * This is commonly used in cache config streams and interface data.
     *
     * @param buf The buffer to read from.
     * @return The decoded string (excluding the terminator).
     */
    public static String readString(ByteBuf buf) {
        StringBuilder bldr = new StringBuilder();
        char c;
        while ((c = (char) buf.readByte()) != 10) {
            bldr.append(c);
        }
        return bldr.toString();
    }

    /**
     * GZIP-decompresses a cache file buffer.
     * <p>
     * <strong>Ownership note:</strong> This method does not release {@code fileBuf}. The caller should manage the
     * lifecycle of the input buffer (especially if it is reference-counted).
     *
     * @param fileBuf The gzip-compressed buffer.
     * @return A new buffer containing the uncompressed bytes.
     * @throws IOException If decompression fails.
     */
    public static ByteBuf unzip(ByteBuf fileBuf) throws IOException {
        byte[] data = new byte[fileBuf.readableBytes()];
        fileBuf.readBytes(data);

        try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            while (true) {
                byte[] buf = new byte[1024];
                int read = is.read(buf, 0, buf.length);
                if (read == -1) {
                    break;
                }
                os.write(buf, 0, read);
            }

            return Unpooled.wrappedBuffer(os.toByteArray());
        }
    }

    /**
     * Computes the Jagex-style name hash used by named archives.
     * <p>
     * This matches the classic cache hash algorithm:
     * <pre>
     * hash = (hash * 61 + char) - 32
     * </pre>
     * using {@code name.toUpperCase()}.
     *
     * @param name The file name.
     * @return The hash key used for archive lookups.
     */
    public static int hash(String name) {
        int hash = 0;
        name = name.toUpperCase();
        for (int j = 0; j < name.length(); j++) {
            hash = (hash * 61 + name.charAt(j)) - 32;
        }
        return hash;
    }
}
