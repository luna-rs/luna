package io.luna.game.cache;

import io.luna.LunaContext;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.util.AsyncExecutor;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

/**
 * A resource representing the #377 cache.
 *
 * @author Graham Edgecombe
 * @author lare96
 */
public final class Cache implements Closeable {

    /**
     * The size of a block in the index file.
     */
    public static final int INDEX_SIZE = 6;

    /**
     * The size of a data block in the data file.
     */
    public static final int DATA_BLOCK_SIZE = 512;

    /**
     * The size of a header block in the data file.
     */
    public static final int DATA_HEADER_SIZE = 8;

    /**
     * The overall size of a block in the data file.
     */
    public static final int DATA_SIZE = DATA_BLOCK_SIZE + DATA_HEADER_SIZE;

    /**
     * The cache path directory.
     */
    private static final Path CACHE_DIR = Paths.get("data", "game", "cache");

    /**
     * The exception message if this cache is accessed without {@link #open()} being called first.
     */
    private static final String EXCEPTION_MESSAGE = "This cache resource was never opened! See [Cache#open()]";

    /**
     * The executor that will run the decoders. Will be shutdown once the task completes.
     */
    private final AsyncExecutor decoderService = new AsyncExecutor(1, "CacheDecoderThread");

    /**
     * The data random access file.
     */
    private volatile RandomAccessFile dataFile;

    /**
     * The index random access files.
     */
    private volatile RandomAccessFile[] indexFiles;

    /**
     * The map index table.
     */
    private final AtomicReference<MapIndexTable> mapIndexTable = new AtomicReference<>();

    /**
     * Synchronously loads this cache. If this server is in beta mode, the cache may not finish fully loading
     * before players are able to log in.
     *
     * @throws IOException If any errors occur.
     */
    public void open() throws IOException {
        checkState(dataFile == null && indexFiles == null, "This cache resource is already open!");
        int count = 0;
        for (int i = 0; i < 255; i++) {
            File indexFile = CACHE_DIR.resolve("main_file_cache.idx" + i).toFile();
            if (!indexFile.exists()) {
                break;
            } else {
                count++;
            }
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
     * Asynchronously runs {@code cacheDecoders} that will extract and store data from this cache.
     *
     * @param cacheDecoders The decoders to run.
     */
    public void runDecoders(LunaContext ctx, CacheDecoder<?>... cacheDecoders) {
        checkState(decoderService.isRunning(), "Cache decoder thread is no longer running.");
        checkState(dataFile != null && indexFiles != null, EXCEPTION_MESSAGE);
        for (CacheDecoder<?> decoder : cacheDecoders) {
            decoderService.execute(decoder.toTask(ctx, this));
        }
    }

    /**
     * Blocks the current thread until {@link #runDecoders(LunaContext, CacheDecoder[])} has finished.
     */
    public void waitForDecoders() throws ExecutionException {
        decoderService.await(true);
    }

    /**
     * Gets a file from the cache.
     *
     * @param cache The cache id.
     * @param file The file id.
     * @return The file.
     * @throws IOException if an I/O error occurs.
     */
    public ByteBuf getFile(int cache, int file) throws IOException {
        checkState(dataFile != null && indexFiles != null, EXCEPTION_MESSAGE);
        if (cache < 0 || cache >= indexFiles.length) {
            throw new IOException("Cache does not exist.");
        }

        RandomAccessFile indexFile = indexFiles[cache];
        cache += 1;

        if (file < 0 || file >= (indexFile.length() * INDEX_SIZE)) {
            throw new IOException("File does not exist.");
        }

        ByteBuf index = Unpooled.copiedBuffer(indexFile.getChannel().map(MapMode.READ_ONLY, (long) INDEX_SIZE * file, INDEX_SIZE));
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

            ByteBuf block = Unpooled.copiedBuffer(dataFile.getChannel().map(MapMode.READ_ONLY, (long) currentBlock * DATA_SIZE, size));
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
     * Sets the map index table. Can only be done once, and the value must be non-null.
     *
     * @param table The new table.
     */
    public void setMapIndexTable(MapIndexTable table) {
        checkState(mapIndexTable.compareAndSet(null, table), "Cache#mapIndexTable already decoded and set!");
    }

    /**
     * @return The map index table.
     */
    public MapIndexTable getMapIndexTable() {
        return mapIndexTable.get();
    }
}