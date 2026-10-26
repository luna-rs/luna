package io.luna.game.model.mob.bot;

import io.luna.game.model.mob.bot.BotLogManager.FileSizePreference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Applies asynchronous writes to a single bot log file in the same order they were requested.
 * <p>
 * Requested writes are queued and applied by at most one writer task at a time, so they can never race each other,
 * even on an executor with many threads. Consecutive appends are batched into a single file write.
 * A bounded file is reset before an append would exceed its limit; old file contents are discarded.
 *
 * @author lare96
 * @author TheLining
 */
final class BotLogFileWriter {

    /**
     * A write that has been requested but not yet applied to the file.
     */
    private static final class PendingWrite {

        /**
         * The text to write.
         */
        private final String text;

        /**
         * The result of an overwrite, or {@code null} if this write is an append.
         */
        private final CompletableFuture<Boolean> overwriteResult;

        /**
         * Creates a new {@link PendingWrite}.
         *
         * @param text The text to write.
         * @param overwriteResult The result of an overwrite, or {@code null} if this write is an append.
         */
        private PendingWrite(String text, CompletableFuture<Boolean> overwriteResult) {
            this.text = text;
            this.overwriteResult = overwriteResult;
        }
    }

    /**
     * Logger used for reporting file errors.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The log file being written to.
     */
    private final Path path;

    /**
     * The executor that runs the writer task.
     */
    private final Executor executor;

    /**
     * Writes waiting to be applied, in the order they were requested.
     */
    private final Queue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();

    /**
     * Whether a writer task is scheduled or running. Only the task holding this flag touches the file.
     */
    private final AtomicBoolean writerScheduled = new AtomicBoolean();

    /**
     * The size policy read once at the start of each file write.
     */
    private volatile FileSizePreference fileSizePreference = FileSizePreference.REGULAR;

    /**
     * Sets the policy enforced by subsequent writes, without performing file I/O on the calling thread.
     *
     * @param preference The new non-null size preference.
     */
    void setFileSizePreference(FileSizePreference preference) {
        fileSizePreference = requireNonNull(preference, "File size preference cannot be null.");
    }

    /**
     * @return The current file size preference.
     */
    FileSizePreference getFileSizePreference() {
        return fileSizePreference;
    }

    /**
     * Creates a new {@link BotLogFileWriter}.
     *
     * @param path The log file to write to. Missing parent directories are created on the first write.
     * @param executor The executor that runs the writer task.
     */
    BotLogFileWriter(Path path, Executor executor) {
        this.path = path;
        this.executor = executor;
    }

    /**
     * Appends {@code text} to the log file, after every write requested before it.
     *
     * @param text The text to append.
     */
    void append(String text) {
        queue(new PendingWrite(text, null));
    }

    /**
     * Replaces the contents of the log file with {@code text}, after every write requested before it. Writes
     * requested afterward are applied on top of the new contents.
     *
     * @param text The new file contents.
     * @return A future that completes with {@code true} if the file was overwritten, or {@code false} if the write
     * failed.
     */
    CompletableFuture<Boolean> overwrite(String text) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        queue(new PendingWrite(text, result));
        return result;
    }

    /**
     * Queues {@code write} and schedules the writer task if one isn't already scheduled or running.
     *
     * @param write The write to queue.
     */
    private void queue(PendingWrite write) {
        pendingWrites.add(write);
        if (writerScheduled.compareAndSet(false, true)) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException e) {
                writerScheduled.set(false);
                throw e;
            }
        }
    }

    /**
     * The writer task. Applies queued writes until none are left.
     */
    private void drain() {
        do {
            try {
                applyPendingWrites();
            } finally {
                writerScheduled.set(false);
            }
            // A write queued after the last poll, but before the flag was cleared, didn't schedule a task of its own.
        } while (!pendingWrites.isEmpty() && writerScheduled.compareAndSet(false, true));
    }

    /**
     * Applies every queued write in order, batching consecutive appends.
     */
    private void applyPendingWrites() {
        StringBuilder appends = new StringBuilder();
        PendingWrite write;
        while ((write = pendingWrites.poll()) != null) {
            if (write.overwriteResult == null) {
                appends.append(write.text);
            } else {
                flushAppends(appends);
                write.overwriteResult.complete(writeFile(write.text, StandardOpenOption.TRUNCATE_EXISTING));
            }
        }
        flushAppends(appends);
    }

    /**
     * Appends the batched text to the log file, then clears the batch.
     *
     * @param appends The batched text.
     */
    private void flushAppends(StringBuilder appends) {
        if (!appends.isEmpty()) {
            writeFile(appends.toString(), StandardOpenOption.APPEND);
            appends.setLength(0);
        }
    }

    /**
     * Writes {@code text} to the log file, creating the file and its parent directories if needed.
     * <p>
     * The current preference limits both appends and overwrites in UTF-8 bytes. An append that would exceed the
     * limit starts a fresh file. A single oversized write is clipped to the limit without splitting a UTF-8 character.
     *
     * @param text The text to write.
     * @param mode {@link StandardOpenOption#APPEND} or {@link StandardOpenOption#TRUNCATE_EXISTING}.
     * @return {@code true} if the write succeeded. Failures are logged rather than thrown, so every overwrite result
     * completes and later writes still run.
     */
    private boolean writeFile(String text, StandardOpenOption mode) {
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            long maxBytes = fileSizePreference.getMaxBytes();
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxBytes) {
                int end = Math.toIntExact(maxBytes);
                while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
                    end--;
                }
                bytes = Arrays.copyOf(bytes, end);
                mode = StandardOpenOption.TRUNCATE_EXISTING;
            } else if (mode == StandardOpenOption.APPEND && Files.exists(path) &&
                    Files.size(path) > maxBytes - bytes.length) {
                mode = StandardOpenOption.TRUNCATE_EXISTING;
            }
            Files.write(path, bytes, mode, StandardOpenOption.CREATE);
            return true;
        } catch (IOException | RuntimeException e) {
            logger.catching(e);
            return false;
        }
    }
}
