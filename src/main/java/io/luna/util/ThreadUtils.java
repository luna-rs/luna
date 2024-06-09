package io.luna.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A static-utility class that contains functions for manipulating threads.
 *
 * @author lare96
 */
public final class ThreadUtils {

    /**
     * Shortcut function to {@link Runtime#availableProcessors()}.
     */
    public static int cpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Awaits termination of {@code service} without interruption.
     *
     * @param service The executor to wait for.
     */
    public static void awaitTerminationUninterruptibly(ExecutorService service) {
        awaitTerminationUninterruptibly(service, Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Awaits termination of {@code service} for the designated time without interruption.
     *
     * @param service The executor to wait for.
     * @param timeout The time to wait for.
     * @param unit The time unit.
     */
    public static void awaitTerminationUninterruptibly(ExecutorService service, long timeout, TimeUnit unit) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    service.awaitTermination(timeout, unit);
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
