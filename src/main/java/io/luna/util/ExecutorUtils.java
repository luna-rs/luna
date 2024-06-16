package io.luna.util;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
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
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Create a new thread pool with {@code threads} workers.
     *
     * @param name The naming scheme for the workers in the pool.
     * @param threads The amount of workers in the pool.
     * @return The thread pool.
     */
    public static ListeningExecutorService threadPool(String name, int threads) {
        int maxRecommendedThreads = ThreadUtils.cpuCount() * 2;
        if (threads > maxRecommendedThreads) {
            logger.warn("Exceeding maximum number of recommended threads for cached thread pool (threads: {}).", threads);
        }
        var threadPool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        var threadFactory = new ThreadFactoryBuilder().setNameFormat(name).build();
        threadPool.setThreadFactory(threadFactory);
        threadPool.setRejectedExecutionHandler(new CallerRunsPolicy());
        return MoreExecutors.listeningDecorator(threadPool);
    }

    /**
     * Create a new thread pool with {@code cpu_count} workers.
     *
     * @param name The naming scheme for the workers in the pool.
     * @return The new cached thread pool.
     */
    public static ListeningExecutorService threadPool(String name) {
        return threadPool(name, ThreadUtils.cpuCount());
    }

    /**
     * Generates a {@link ThreadFactory} for {@code classType}.
     *
     * @param classType The class type.
     * @return The generated thread factory.
     */
    public static ThreadFactory threadFactory(Class<?> classType) {
        return threadFactory(classType.getSimpleName() + "Thread");
    }

    /**
     * Generates a {@link ThreadFactory} with {@code nameFormat}.
     *
     * @param nameFormat The name of the threads.
     * @return The generated thread factory.
     */
    public static ThreadFactory threadFactory(String nameFormat) {
        return new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
    }

    /**
     * Prevent instantiation.
     */
    private ExecutorUtils() {
    }
}