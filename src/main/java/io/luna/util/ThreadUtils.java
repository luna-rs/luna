package io.luna.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkState;

/**
 * A static-utility class that contains functions for manipulating threads.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ThreadUtils {

    /**
     * Throws an {@link IllegalStateException} if the current thread is not an initialization thread.
     */
    public static void ensureInitThread() {
        Thread currentThread = Thread.currentThread();
        boolean isInitThread = currentThread.getName().equals("LunaInitializationThread");
        checkState(isInitThread, String.format("thread[%s] not an initialization thread", currentThread));
    }

    /**
     * Returns a new fixed thread pool containing] {@code Runtime.getRuntime().availableProcessors()}
     * threads.
     */
    public static ExecutorService newThreadPool(String name) {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                new ThreadFactoryBuilder().setNameFormat(name).build());
    }
}
