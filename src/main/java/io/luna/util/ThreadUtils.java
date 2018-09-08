package io.luna.util;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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

    public static ThreadFactory nameThreadFactory(String threadName) {
        return new ThreadFactoryBuilder().setNameFormat(threadName).build();
    }
    public static int getCpuAmount() {
        return Runtime.getRuntime().availableProcessors();
    }
    /**
     * Returns a new fixed thread pool containing] {@code Runtime.getRuntime().cpuCount()}
     * threads.
     */
    public static ListeningExecutorService newFixedThreadPool(ThreadFactory threadFactory, int nThreads) {
       ExecutorService delegate = Executors.newFixedThreadPool(nThreads, threadFactory);
       return MoreExecutors.listeningDecorator(delegate);
    }

    public static ListeningExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        ExecutorService delegate = Executors.newCachedThreadPool(threadFactory);
        return MoreExecutors.listeningDecorator(delegate);
    }
}
