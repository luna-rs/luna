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
 * A utility class for byte buffers and various cache functions.
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class CacheUtils {

    /**
     * The map size.
     */
    public static final int MAP_SIZE = Region.SIZE;

    /**
     * The maximum height level, exclusive.
     */
    public static final int MAP_PLANES = Position.HEIGHT_LEVELS.upperEndpoint();

    /**
     * Gets a smart.
     *
     * @param buf The buffer.
     * @return The smart.
     */
    public static int readSmart(ByteBuf buf) {
        int peek = buf.getUnsignedByte(buf.readerIndex());
        if (peek <= Byte.MAX_VALUE) {
            return buf.readUnsignedByte();
        } else {
            return buf.readUnsignedShort() + Short.MIN_VALUE;
        }
    }

    /**
     * Reads 3 bytes (24-bit MEDIUM) from {@code msg}.
     *
     * @param buf The buffer to read from.
     * @return The value.
     */
    public static int readMedium(ByteBuf buf) {
        return (buf.readUnsignedByte() << 16) | (buf.readUnsignedByte() << 8) | buf.readUnsignedByte();
    }

    /**
     * Gets an RS2 string from the buffer.
     *
     * @param buf The buffer.
     * @return The RS2 string.
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
     * Unzips a cache file.
     *
     * @param fileBuf The cache file.
     * @return The unzipped byte buffer.
     * @throws IOException if an I/O error occurs.
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
     * Hashes a file name.
     *
     * @param name The file name.
     * @return The hash.
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
