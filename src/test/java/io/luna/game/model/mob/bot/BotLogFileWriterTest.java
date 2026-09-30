package io.luna.game.model.mob.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BotLogFileWriter}.
 * <p>
 * Writes run on a multi-threaded pool, the same way bot logs run on the game service's worker pool, so any write that
 * escapes the writer's ordering would be free to land out of order.
 *
 * @author TheLining
 */
final class BotLogFileWriterTest {

    /**
     * The pool that runs the writer task.
     */
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    /**
     * The directory that log files are written to.
     */
    @TempDir
    Path dir;

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @Test
    void appendsKeepCallOrder() throws Exception {
        Path path = dir.resolve("logs").resolve("bot.txt");
        BotLogFileWriter writer = new BotLogFileWriter(path, pool);

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            String line = "line " + i;
            expected.add(line);
            writer.append(line + "\n");
        }
        awaitWrites();

        assertEquals(expected, Files.readAllLines(path));
    }

    @Test
    void overwriteIsOrderedWithAppends() throws Exception {
        Path path = dir.resolve("bot.txt");
        BotLogFileWriter writer = new BotLogFileWriter(path, pool);

        writer.append("before 1\n");
        writer.append("before 2\n");
        CompletableFuture<Boolean> result = writer.overwrite("buffer 1\nbuffer 2\n");
        writer.append("after 1\n");
        writer.append("after 2\n");
        awaitWrites();

        assertTrue(result.join());
        assertEquals(List.of("buffer 1", "buffer 2", "after 1", "after 2"), Files.readAllLines(path));
    }

    @Test
    void concurrentAppendsAreAllWrittenInPerThreadOrder() throws Exception {
        Path path = dir.resolve("bot.txt");
        BotLogFileWriter writer = new BotLogFileWriter(path, pool);
        int threadCount = 4;
        int linesPerThread = 2_000;

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            String prefix = t + " ";
            threads.add(Thread.startVirtualThread(() -> {
                for (int i = 0; i < linesPerThread; i++) {
                    writer.append(prefix + i + "\n");
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        awaitWrites();

        int[] nextLine = new int[threadCount];
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String[] parts = line.split(" ");
            int t = Integer.parseInt(parts[0]);
            assertEquals(nextLine[t]++, Integer.parseInt(parts[1]), "Out of order: " + line);
        }
        assertEquals(threadCount * linesPerThread, lines.size());
    }

    @Test
    void overwriteCompletesWithFalseWhenWriteFails() throws IOException {
        Path directory = Files.createDirectory(dir.resolve("not-a-file"));
        BotLogFileWriter writer = new BotLogFileWriter(directory, pool);

        assertFalse(writer.overwrite("text\n").join());
    }

    /**
     * Waits for all queued writes to be applied. Writer tasks never reschedule themselves, so once the pool has
     * finished every task it was given, nothing is left to write.
     */
    private void awaitWrites() throws InterruptedException {
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
}
