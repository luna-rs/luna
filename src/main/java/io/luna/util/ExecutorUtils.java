package io.luna.util;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
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
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("LunaWorkerThread").build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, maxThreads, 60L,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.setThreadFactory(threadFactory);
        executor.setRejectedExecutionHandler(new CallerRunsPolicy()); // TODO custom exception that explains issue, if tasks have been blocked longer than x times
        return MoreExecutors.listeningDecorator(executor);
    }

    /**
     * Create a new cached thread pool with a capacity of {@code ThreadUtils.cpuCount() * 2}.
     *
     * @return The new cached thread pool.
     */
    public static ListeningExecutorService newCachedThreadPool() {
        return newCachedThreadPool(ThreadUtils.cpuCount() * 2);
    }

    /**
     * Prevent instantiation.
     */
    private ExecutorUtils() {
    }
}