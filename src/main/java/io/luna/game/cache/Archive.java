package io.luna.game.cache;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A single “named archive” inside the #317/#377 cache.
 * <p>
 * An archive is a container for one or more {@link ArchiveFile}s, addressed by a hashed name. Archives can be stored
 * in one of two formats:
 * <ul>
 *   <li><b>Whole-archive compressed</b>: the entire archive payload is BZIP2-compressed.</li>
 *   <li><b>Individually compressed (“packed”)</b>: the archive payload is not compressed as a whole,
 *       but each file inside the archive is BZIP2-compressed.</li>
 * </ul>
 * <p>
 * This class decodes the archive header, indexes each contained file, and provides {@link #getFileData(String)} for
 * extracting file bytes (decompressing when required).
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class Archive {

    /**
     * Decodes an {@link Archive} from {@code buf}.
     * <p>
     * The first two 24-bit MEDIUM values are the uncompressed and compressed sizes for the archive payload. When
     * these differ, the archive payload is compressed as a whole and must be BZIP2-decompressed before reading the
     * internal file table.
     * <p>
     * When they are equal, the archive payload is already “raw”, and each file entry inside the archive is typically
     * individually compressed.
     *
     * @param buf The buffer containing the archive data positioned at the archive header.
     * @return The decoded archive.
     * @throws IOException If decompression fails or the archive data is malformed.
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
     * BZIP2-decompresses {@code data}.
     * <p>
     * RuneScape cache BZIP2 streams are commonly stored without the normal {@code "BZh1"} header. This method
     * reconstructs that noteheader so Apache Commons Compress can decode the stream.
     *
     * @param data The compressed bytes (reference-counted {@link ByteBuf}).
     * @return A new buffer containing the uncompressed bytes.
     * @throws IOException If decompression fails.
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
     * Backing archive payload data (either raw or decompressed).
     */
    private final ByteBuf data;

    /**
     * Whether file payloads are individually compressed.
     * <p>
     * If {@code true}, each extracted file must be BZIP2-unpacked. If {@code false}, the archive payload was
     * decompressed as a whole and file payloads are already raw.
     */
    private final boolean packed;

    /**
     * Files contained in this archive keyed by {@code CacheUtils.hash(name)}.
     */
    private final ImmutableMap<Integer, ArchiveFile> files;

    /**
     * Creates an {@link Archive}.
     *
     * @param data Backing archive payload.
     * @param packed Whether file payloads are individually compressed.
     * @param files File table keyed by hash.
     */
    private Archive(ByteBuf data, boolean packed, ImmutableMap<Integer, ArchiveFile> files) {
        this.data = data;
        this.packed = packed;
        this.files = files;
    }

    /**
     * Extracts a file payload from this archive.
     * <p>
     * If the archive uses per-file compression ({@link #packed} is {@code true}), the returned buffer will be the
     * decompressed bytes. If not packed, the returned buffer is the raw bytes for that file.
     * <p>
     * If the file does not exist, {@link Unpooled#EMPTY_BUFFER} is returned.
     *
     * @param name The file name inside the archive.
     * @return A buffer containing the file bytes (possibly decompressed).
     * @throws IOException If decompression fails.
     */
    public ByteBuf getFileData(String name) throws IOException {
        ArchiveFile file = files.get(CacheUtils.hash(name));
        if (file == null) {
            return Unpooled.EMPTY_BUFFER;
        }

        data.readerIndex(file.getOffset());
        ByteBuf fileBytes = data.readBytes(file.getCompressedSize());

        if (!packed) {
            return fileBytes;
        }

        return unpack(fileBytes);
    }
}
