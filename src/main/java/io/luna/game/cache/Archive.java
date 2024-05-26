package io.luna.game.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Represents an archive within the cache.
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class Archive {

    /**
     * Decodes the data from {@code buf} into an {@link Archive}.
     *
     * @param buf The data to decode.
     * @return The archive.
     */
    public static Archive decode(ByteBuf buf) throws IOException {
        boolean packed = true;
        ImmutableMap.Builder<Integer, ArchiveFile> files = ImmutableMap.builder();
        int uncompressed = CacheUtils.readMedium(buf);
        int compressed = CacheUtils.readMedium(buf);
        if (uncompressed != compressed) {
            ByteBuf data = buf.readBytes(compressed);
            ByteBuf decompressed = unpack(data);
            buf = Unpooled.wrappedBuffer(decompressed);
            packed = false;
        }
        int size = buf.readUnsignedShort();
        int offset = buf.readerIndex() + size * 10;
        for (int i = 0; i < size; i++) {
            int nameHash = buf.readInt();
            int uncompressedSize = CacheUtils.readMedium(buf);
            int compressedSize = CacheUtils.readMedium(buf);
            ArchiveFile nf = new ArchiveFile(nameHash, uncompressedSize, compressedSize, offset);
            files.put(nf.getHash(), nf);
            offset += nf.getCompressedSize();
        }
        return new Archive(buf, packed, files.build());
    }

    /**
     * Unpacks data using BZIP2.
     *
     * @param data The compressed bytes.
     * @return The uncompressed bytes.
     * @throws IOException if an I/O error occurs.
     */
    private static ByteBuf unpack(ByteBuf data) throws IOException {
        try {
            byte[] newData = new byte[data.readableBytes() + 4];
            data.readerIndex(0);
            data.readBytes(newData, 4, data.readableBytes());
            newData[0] = 'B';
            newData[1] = 'Z';
            newData[2] = 'h';
            newData[3] = '1';
            try (InputStream in = new BZip2CompressorInputStream(new ByteArrayInputStream(newData));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                while (true) {
                    byte[] buf = new byte[512];
                    int read = in.read(buf, 0, buf.length);
                    if (read == -1) {
                        break;
                    }
                    out.write(buf, 0, read);
                }
                return Unpooled.copiedBuffer(out.toByteArray());
            }
        } finally {
            data.release();
        }
    }

    /**
     * Holds the data within this archive.
     */
    private final ByteBuf data;

    /**
     * If the data is packed.
     */
    private final boolean packed;

    /**
     * A map of files within this archive.
     */
    private final ImmutableMap<Integer, ArchiveFile> files;

    /**
     * Creates an {@link Archive}.
     *
     * @param data Holds the data within this archive.
     * @param packed If the data is packed.
     * @param files A map of files within this archive.
     */
    private Archive(ByteBuf data, boolean packed, ImmutableMap<Integer, ArchiveFile> files) {
        this.data = data;
        this.packed = packed;
        this.files = files;
    }

    /**
     * Gets the data of a file within this archive.
     *
     * @param name The name of the file.
     * @return A buffer containing the data.
     * @throws IOException if an I/O error occurs.
     */
    public ByteBuf getFileData(String name) throws IOException {
        ArchiveFile file = files.get(CacheUtils.hash(name));
        if (file == null) {
            return Unpooled.EMPTY_BUFFER;
        } else {
            data.readerIndex(file.getOffset());
            ByteBuf fileBytes = data.readBytes(file.getCompressedSize());
            if (!packed) {
                return fileBytes;
            } else {
                return unpack(fileBytes);
            }
        }
    }
}