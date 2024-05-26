package io.luna.game.cache;

/**
 * Holds information about a single file in the archived cache.
 *
 * @author Graham Edgecombe
 */
public final class ArchiveFile {
	
	/**
	 * The name hash.
	 */
	private final int hash;
	
	/**
	 * The uncompressed size.
	 */
	private final int uncompressedSize;
	
	/**
	 * The compressed size.
	 */
	private final int compressedSize;
	
	/**
	 * The offset in the named cache.
	 */
	private final int offset;
	
	/**
	 * Creates an archived file.
	 * @param hash The hash.
	 * @param uncompressedSize The uncompressed size.
	 * @param compressedSize The compressed size.
	 * @param offset The offset.
	 */
	public ArchiveFile(int hash, int uncompressedSize, int compressedSize, int offset) {
		this.hash = hash;
		this.uncompressedSize = uncompressedSize;
		this.compressedSize = compressedSize;
		this.offset = offset;
	}
	
	/**
	 * Gets the hash of the name of this file.
	 * @return The hash of this file's name.
	 */
	public int getHash() {
		return hash;
	}
	
	/**
	 * Gets the uncompressed size.
	 * @return The uncompressed size, in bytes.
	 */
	public int getUncompressedSize() {
		return uncompressedSize;
	}
	
	/**
	 * Gets the compressed size.
	 * @return The compressed size, in bytes.
	 */
	public int getCompressedSize() {
		return compressedSize;
	}
	
	/**
	 * Gets the offset.
	 * @return The offset.
	 */
	public int getOffset() {
		return offset;
	}
	
}
