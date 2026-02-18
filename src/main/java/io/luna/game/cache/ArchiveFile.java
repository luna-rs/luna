package io.luna.game.cache;

/**
 * Metadata for a single file entry within a cache {@link Archive}.
 *
 * @author Graham Edgecombe
 */
public final class ArchiveFile {

    /**
     * Hash of the file name (see {@link CacheUtils#hash(String)}).
     */
    private final int hash;

    /**
     * File size after decompression, in bytes.
     */
    private final int uncompressedSize;

    /**
     * File size as stored in the archive payload, in bytes.
     */
    private final int compressedSize;

    /**
     * Byte offset into the archive payload where the file data begins.
     */
    private final int offset;

    /**
     * Creates a new {@link ArchiveFile} entry.
     *
     * @param hash The name hash.
     * @param uncompressedSize The size after decompression.
     * @param compressedSize The stored size inside the archive payload.
     * @param offset The byte offset within the archive payload.
     */
    public ArchiveFile(int hash, int uncompressedSize, int compressedSize, int offset) {
        this.hash = hash;
        this.uncompressedSize = uncompressedSize;
        this.compressedSize = compressedSize;
        this.offset = offset;
    }

    /**
     * @return The hash of the file name.
     */
    public int getHash() {
        return hash;
    }

    /**
     * @return The size after decompression, in bytes.
     */
    public int getUncompressedSize() {
        return uncompressedSize;
    }

    /**
     * @return The stored size, in bytes.
     */
    public int getCompressedSize() {
        return compressedSize;
    }

    /**
     * @return The byte offset within the archive payload where the file begins.
     */
    public int getOffset() {
        return offset;
    }
}
