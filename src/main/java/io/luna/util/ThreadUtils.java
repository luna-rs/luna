package io.luna.util;

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
}
