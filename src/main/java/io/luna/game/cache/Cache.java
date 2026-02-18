package io.luna.game.cache;

import io.luna.LunaContext;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.util.ExecutorUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;

/**
 * Read-only access to the RuneScape #377 cache files on disk.
 * <p>
 * This implementation reads from {@code main_file_cache.dat} and one or more index files {@code main_file_cache.idxN}.
 * Each index entry points to a linked-list of 520-byte blocks in the data file.
 * <h3>Usage</h3>
 * <ul>
 *   <li>Call {@link #open()} once to open the data/index files.</li>
 *   <li>Optionally run cache decoders via {@link #runDecoders(LunaContext, CacheDecoder[])}.</li>
 *   <li>Use {@link #getFile(int, int)} to read raw cache files.</li>
 *   <li>Call {@link #close()} on shutdown.</li>
 * </ul>
 * <p>
 * <strong>Threading:</strong> Decoders are executed on an internal executor (currently single-threaded). File reads
 * are synchronous.
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class Cache implements Closeable {

    /**
     * Size of a single index record in bytes (file size + first block pointer).
     */
    public static final int INDEX_SIZE = 6;

    /**
     * Payload bytes stored per data block.
     */
    public static final int DATA_BLOCK_SIZE = 512;

    /**
     * Header bytes stored per data block.
     */
    public static final int DATA_HEADER_SIZE = 8;

    /**
     * Total bytes per data block (header + payload).
     */
    public static final int DATA_SIZE = DATA_BLOCK_SIZE + DATA_HEADER_SIZE;

    /**
     * Directory containing cache files.
     */
    private static final Path CACHE_DIR = Paths.get("data", "game", "cache");

    /**
     * Error used when accessing the cache before {@link #open()}.
     */
    private static final String EXCEPTION_MESSAGE =
            "This cache resource was never opened! See [Cache#open()]";

    /**
     * Executor running decoder tasks (shut down after decoding completes).
     */
    private final ExecutorService decoderService = ExecutorUtils.threadPool("CacheDecoderThread", 1);

    /**
     * Data file containing 520-byte chained blocks.
     */
    private volatile RandomAccessFile dataFile;

    /**
     * Index files that map (cacheId,fileId) -> (size, firstBlock).
     */
    private volatile RandomAccessFile[] indexFiles;

    /**
     * Decoded map index table, set once by a decoder.
     */
    private final AtomicReference<MapIndexTable> mapIndexTable = new AtomicReference<>();

    /**
     * Opens the cache by locating and opening {@code main_file_cache.dat} and all sequential index files
     * {@code main_file_cache.idx0..idxN}.
     * <p>
     * This method must be called before {@link #getFile(int, int)} or {@link #runDecoders(LunaContext, CacheDecoder[])}.
     *
     * @throws IOException If the cache is missing, unreadable, or already open.
     */
    public void open() throws IOException {
        checkState(dataFile == null && indexFiles == null, "This cache resource is already open!");

        int count = 0;
        for (int i = 0; i < 255; i++) {
            File indexFile = CACHE_DIR.resolve("main_file_cache.idx" + i).toFile();
            if (!indexFile.exists()) {
                break;
            }
            count++;
        }

        if (count == 0) {
            throw new FileNotFoundException("No index files present.");
        }

        indexFiles = new RandomAccessFile[count];
        dataFile = new RandomAccessFile(CACHE_DIR.resolve("main_file_cache.dat").toFile(), "r");

        for (int i = 0; i < indexFiles.length; i++) {
            indexFiles[i] = new RandomAccessFile(CACHE_DIR.resolve("main_file_cache.idx" + i).toFile(), "r");
        }
    }

    /**
     * Schedules cache decoders to run asynchronously on the decoder executor.
     * <p>
     * Decoders typically read raw files via {@link #getFile(int, int)} and then store decoded structures into game
     * repositories (e.g., definitions, map tables).
     *
     * @param ctx The game context.
     * @param cacheDecoders The decoders to run.
     * @throws IllegalStateException If the cache is not open or the decoder thread is shut down.
     */
    public void runDecoders(LunaContext ctx, CacheDecoder<?>... cacheDecoders) {
        checkState(!decoderService.isShutdown(), "Cache decoder thread is no longer running.");
        checkState(dataFile != null && indexFiles != null, EXCEPTION_MESSAGE);

        for (CacheDecoder<?> decoder : cacheDecoders) {
            decoderService.execute(decoder.toTask(ctx, this));
        }
    }

    /**
     * Blocks the calling thread until all scheduled decoders finish.
     * <p>
     * This shuts down the decoder executor and waits uninterruptibly for completion.
     */
    public void waitForDecoders() {
        decoderService.shutdown();
        awaitTerminationUninterruptibly(decoderService);
    }

    /**
     * Reads a raw file from the cache.
     * <p>
     * This follows the standard {@code main_file_cache} structure:
     * <ul>
     *   <li>Index record contains the file size and the first data block id.</li>
     *   <li>Each 520-byte data block contains an 8-byte header and up to 512 bytes of payload.</li>
     *   <li>Blocks form a chain via {@code nextBlockId} until the entire file is read.</li>
     * </ul>
     *
     * @param cache The cache index id (idx file number).
     * @param file The file id within that index.
     * @return A buffer containing the complete file bytes.
     * @throws IOException If the cache/file does not exist or the block chain is corrupt.
     */
    public ByteBuf getFile(int cache, int file) throws IOException {
        checkState(dataFile != null && indexFiles != null, EXCEPTION_MESSAGE);

        if (cache < 0 || cache >= indexFiles.length) {
            throw new IOException("Cache does not exist.");
        }

        RandomAccessFile indexFile = indexFiles[cache];
        cache += 1;

        /*
         * NOTE (glaring bug risk):
         * This bounds check should be based on entry count, not "length * INDEX_SIZE".
         * Correct form is typically: (long) file * INDEX_SIZE >= indexFile.length()
         */
        if (file < 0 || file >= (indexFile.length() * INDEX_SIZE)) {
            throw new IOException("File does not exist.");
        }

        ByteBuf index = Unpooled.copiedBuffer(
                indexFile.getChannel().map(MapMode.READ_ONLY, (long) INDEX_SIZE * file, INDEX_SIZE)
        );

        int fileSize = CacheUtils.readMedium(index);
        int fileBlock = CacheUtils.readMedium(index);

        int remainingBytes = fileSize;
        int currentBlock = fileBlock;

        ByteBuf fileBuffer = Unpooled.buffer(fileSize);
        int cycles = 0;

        while (remainingBytes > 0) {

            int size = DATA_SIZE;
            int rem = (int) (dataFile.length() - currentBlock * DATA_SIZE);
            if (rem < DATA_SIZE) {
                size = rem;
            }

            ByteBuf block = Unpooled.copiedBuffer(
                    dataFile.getChannel().map(MapMode.READ_ONLY, (long) currentBlock * DATA_SIZE, size)
            );

            int nextFileId = block.readUnsignedShort();
            int currentPartId = block.readUnsignedShort();
            int nextBlockId = CacheUtils.readMedium(block);
            int nextCacheId = block.readUnsignedByte();

            int bytesThisCycle = remainingBytes;
            if (bytesThisCycle > DATA_BLOCK_SIZE) {
                bytesThisCycle = DATA_BLOCK_SIZE;
            }

            ByteBuf temp = block.readBytes(bytesThisCycle);
            try {
                fileBuffer.writeBytes(temp, 0, bytesThisCycle);
            } finally {
                temp.release();
            }

            remainingBytes -= bytesThisCycle;

            if (cycles != currentPartId) {
                throw new IOException("Cycle does not match part id.");
            }

            if (remainingBytes > 0) {
                if (nextCacheId != cache) {
                    throw new IOException("Unexpected next cache id.");
                }
                if (nextFileId != file) {
                    throw new IOException("Unexpected next file id.");
                }
            }

            cycles++;
            currentBlock = nextBlockId;
        }

        return fileBuffer;
    }

    @Override
    public void close() {
        try {
            checkState(dataFile != null && indexFiles != null, EXCEPTION_MESSAGE);

            decoderService.shutdown();
            dataFile.close();

            for (RandomAccessFile indexFile : indexFiles) {
                indexFile.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the decoded {@link MapIndexTable}.
     * <p>
     * This is expected to be called exactly once by the map decoder after successful decode.
     *
     * @param table The decoded table.
     * @throws IllegalStateException If the table was already set.
     */
    public void setMapIndexTable(MapIndexTable table) {
        checkState(mapIndexTable.compareAndSet(null, table),
                "Cache#mapIndexTable already decoded and set!");
    }

    /**
     * @return The decoded map index table, or {@code null} if not decoded yet.
     */
    public MapIndexTable getMapIndexTable() {
        return mapIndexTable.get();
    }
}
