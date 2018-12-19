package io.luna.util;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A static-utility class that contains functions for manipulating {@link Executor}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ExecutorUtils {

    /**
     * Create a new cached thread pool with a capacity of {@code maxThreads}.
     *
     * @param maxThreads The thread pool capacity.
     * @return The new cached thread pool.
     */
    public static ListeningExecutorService newCachedThreadPool(int maxThreads) {
        return newCachedThreadPool(maxThreads, Executors.defaultThreadFactory());
    }

    /**
     * Create a new cached thread pool with a capacity of {@code maxThreads}.
     *
     * @param maxThreads The thread pool capacity.
     * @param defaultName The default name for the threads in this pool.
     * @return The new cached thread pool.
     */
    public static ListeningExecutorService newCachedThreadPool(int maxThreads, String defaultName) {
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat(defaultName).build();
        return newCachedThreadPool(maxThreads, tf);
    }

    /**
     * Create a new cached thread pool with a capacity of {@code maxThreads} that uses {@code tf} to instantiate
     * new threads.
     *
     * @param maxThreads The thread pool capacity.
     * @param tf The thread factory.
     * @return The new cached thread pool.
     */
    public static ListeningExecutorService newCachedThreadPool(int maxThreads, ThreadFactory tf) {
        ThreadPoolExecutor delegate =
                new ThreadPoolExecutor(0, maxThreads, 1, TimeUnit.MINUTES, new SynchronousQueue<>());
        delegate.setThreadFactory(tf);
        return MoreExecutors.listeningDecorator(delegate);
    }

    /**
     * Prevent instantiation.
     */
    private ExecutorUtils() {
    }
}