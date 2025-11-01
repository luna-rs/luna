package io.luna.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

/**
 * A static-utility class that contains functions for manipulating {@link Executor}s.
 *
 * @author lare96
 */
public final class ExecutorUtils {

    /**
     * Create a new thread pool with {@code threads} workers.
     *
     * @param name The naming scheme for the workers in the pool.
     * @param threads The amount of workers in the pool.
     * @return The thread pool.
     */
    public static ExecutorService threadPool(String name, int threads) {
        var threadPool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        var threadFactory = new ThreadFactoryBuilder().setNameFormat(name).build();
        threadPool.setThreadFactory(threadFactory);
        threadPool.setRejectedExecutionHandler(new CallerRunsPolicy());
        return threadPool;
    }

    /**
     * Create a new thread pool with {@code cpu_count} workers.
     *
     * @param name The naming scheme for the workers in the pool.
     * @return The new cached thread pool.
     */
    public static ExecutorService threadPool(String name) {
        return threadPool(name, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Prevent instantiation.
     */
    private ExecutorUtils() {
    }
}