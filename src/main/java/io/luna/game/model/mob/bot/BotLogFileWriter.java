package io.luna.game.model.mob.bot;

import io.luna.game.model.mob.bot.BotLogManager.FileSizePreference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Applies asynchronous writes to a single bot log file in the same order they were requested.
 * <p>
 * Requested writes are queued and applied by at most one writer task at a time, so they can never race each other,
 * even on an executor with many threads. Consecutive appends are batched into bounded file writes.
 * A bounded file is reset before an append batch would exceed its limit; old file contents are discarded.
 * A long burst may therefore leave only the later batches in the file after a reset.
 * <p>
 * Pending text is limited separately from the file size. Newest requests are rejected when the queue is full or a
 * single request exceeds the text budget. Rejected overwrite requests complete with {@code false}.
 *
 * @author lare96
 * @author TheLining
 */
final class BotLogFileWriter {

    /**
     * The maximum number of queued requests, including requests with empty text.
     */
    private static final int MAX_PENDING_WRITES = 1024;

    /**
     * The maximum queued UTF-16 code units, at most 256 KiB of character payload.
     */
    private static final int MAX_PENDING_CHARS = 131_072;

    /**
     * The maximum UTF-16 code units combined into one append batch. Every admitted request fits this budget.
     */
    private static final int MAX_BATCH_CHARS = MAX_PENDING_CHARS;

    /**
     * The minimum interval between aggregate rejection warnings for this file.
     */
    private static final long REJECTION_WARNING_INTERVAL = TimeUnit.MINUTES.toNanos(1);

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
     * Identifies the task that exclusively owns file writes, including while its queue is temporarily empty.
     *
     * @author lare96
     */
    private final class WriterTask implements Runnable {

        @Override
        public void run() {
            drain(this);
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
     * Guards queue accounting and writer ownership. File I/O, scheduling and future completion use no queue lock.
     */
    private final Object queueLock = new Object();

    /**
     * Writes waiting to be applied, in the order they were admitted, guarded by {@link #queueLock}.
     */
    private final ArrayDeque<PendingWrite> pendingWrites = new ArrayDeque<>();

    /**
     * Character payload retained by the queue, guarded by {@link #queueLock}.
     */
    private int pendingChars;

    /**
     * The scheduled or running file owner, or {@code null}, guarded by {@link #queueLock}.
     */
    private WriterTask writerTask;

    /**
     * Rejected requests since this writer was created, guarded by {@link #queueLock}.
     */
    private long rejectedWrites;

    /**
     * The rejection total included in the previous warning, guarded by {@link #queueLock}.
     */
    private long reportedRejections;

    /**
     * The monotonic time of the previous warning, guarded by {@link #queueLock}.
     */
    private long lastRejectionWarning;

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
     * Appends {@code text} after every accepted write requested before it, unless the pending budget rejects it.
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
     * failed or the pending budget rejected it.
     */
    CompletableFuture<Boolean> overwrite(String text) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        queue(new PendingWrite(text, result));
        return result;
    }

    /**
     * Admits a request within both queue budgets and schedules its writer outside the queue lock.
     *
     * @param write The write to queue.
     */
    private void queue(PendingWrite write) {
        requireNonNull(write.text, "Log text cannot be null.");
        WriterTask task = null;
        boolean rejected;
        synchronized (queueLock) {
            rejected = pendingWrites.size() >= MAX_PENDING_WRITES ||
                    write.text.length() > MAX_PENDING_CHARS - pendingChars;
            if (rejected) {
                rejectedWrites++;
            } else {
                pendingWrites.addLast(write);
                pendingChars += write.text.length();
                if (writerTask == null) {
                    writerTask = new WriterTask();
                    task = writerTask;
                }
            }
        }
        if (rejected) {
            failWrite(write);
            reportRejections("pending text or request limit");
        } else if (task != null) {
            try {
                executor.execute(task);
            } catch (RuntimeException error) {
                failTask(task, null);
                reportRejections("writer scheduling failure");
            } catch (Error error) {
                failTask(task, null);
                throw error;
            }
        }
    }

    /**
     * Applies queued requests while retaining exclusive ownership through the final append flush.
     * Polled text leaves the queue budget, so temporary memory also includes one bounded batch and current request.
     *
     * @param task The exclusive file owner.
     */
    private void drain(WriterTask task) {
        PendingWrite current = null;
        try {
            StringBuilder appends = new StringBuilder();
            for (; ; ) {
                synchronized (queueLock) {
                    if (writerTask != task) {
                        return;
                    }
                    current = pendingWrites.pollFirst();
                    if (current != null) {
                        pendingChars -= current.text.length();
                    }
                }
                if (current == null) {
                    flushAppends(appends);
                    synchronized (queueLock) {
                        if (pendingWrites.isEmpty()) {
                            writerTask = null;
                            return;
                        }
                    }
                } else if (current.overwriteResult == null) {
                    if (current.text.length() > MAX_BATCH_CHARS - appends.length()) {
                        flushAppends(appends);
                    }
                    appends.append(current.text);
                    current = null;
                } else {
                    flushAppends(appends);
                    current.overwriteResult.complete(writeFile(current.text, StandardOpenOption.TRUNCATE_EXISTING));
                    current = null;
                }
            }
        } catch (RuntimeException | Error error) {
            failTask(task, current);
            throw error;
        }
    }

    /**
     * Releases requests abandoned by this task and restores admission without touching a newer file owner.
     *
     * @param task The failed owner.
     * @param current A polled request that failed, or {@code null}.
     */
    private void failTask(WriterTask task, PendingWrite current) {
        List<PendingWrite> abandoned;
        synchronized (queueLock) {
            if (writerTask != task) {
                return;
            }
            abandoned = new ArrayList<>(pendingWrites);
            pendingWrites.clear();
            pendingChars = 0;
            writerTask = null;
            rejectedWrites += abandoned.size() + (current == null ? 0 : 1);
        }
        if (current != null) {
            failWrite(current);
        }
        abandoned.forEach(this::failWrite);
    }

    /**
     * Finishes an unsuccessful overwrite outside the queue lock.
     *
     * @param write The unsuccessful request.
     */
    private void failWrite(PendingWrite write) {
        if (write.overwriteResult != null) {
            write.overwriteResult.complete(false);
        }
    }

    /**
     * Reports aggregate rejected requests at most once per minute, including the first rejection.
     *
     * @param reason The cause of the latest rejection.
     */
    private void reportRejections(String reason) {
        long total;
        synchronized (queueLock) {
            long now = System.nanoTime();
            if (rejectedWrites == reportedRejections || reportedRejections != 0 &&
                    now - lastRejectionWarning < REJECTION_WARNING_INTERVAL) {
                return;
            }
            total = rejectedWrites;
            reportedRejections = total;
            lastRejectionWarning = now;
        }
        logger.warn("Rejected {} bot log write requests for {}; latest reason: {}.", total, path, reason);
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
