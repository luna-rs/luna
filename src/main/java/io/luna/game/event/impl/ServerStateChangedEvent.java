package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.util.AsyncExecutor;

import java.util.concurrent.ExecutorService;

/**
 * A server state change event. Not intended for interception.
 *
 * @author lare96
 */
public class ServerStateChangedEvent extends Event {

    /**
     * A specialized event type sent when the game thread launches. All code is ran on the game thread. To load blocking resources,
     * please use the provided {@link #taskPool}. The server will not go online until all asynchronous tasks complete.
     *
     * @author lare96
     */
    public static final class ServerLaunchEvent extends ServerStateChangedEvent {

        /**
         * Creates a new {@link ServerLaunchEvent}.
         *
         * @param taskPool The executor to handle tasks.
         */
        public ServerLaunchEvent(AsyncExecutor taskPool) {
            super(taskPool);
        }
    }

    /**
     * A specialized event type sent when the server begins gracefully shutting down. Logic within these intercepted events
     * is executed on the game thread. The provided {@link ExecutorService} can be used for scripts that require
     * asynchronous shutdown logic. The game will not shutdown until the asynchronous tasks finish.
     *
     * @author lare96
     */
    public static final class ServerShutdownEvent extends ServerStateChangedEvent {

        /**
         * Creates a new {@link ServerShutdownEvent}.
         *
         * @param taskPool The executor to handle tasks.
         */
        public ServerShutdownEvent(AsyncExecutor taskPool) {
            super(taskPool);
        }
    }

    /**
     * The executor to handle tasks.
     */
    private final AsyncExecutor taskPool;

    /**
     * Creates a new {@link ServerStateChangedEvent}.
     *
     * @param taskPool The executor to handle tasks.
     */
    public ServerStateChangedEvent(AsyncExecutor taskPool) {
        this.taskPool = taskPool;
    }

    /**
     * The executor to handle tasks.
     */
    public AsyncExecutor getTaskPool() {
        return taskPool;
    }
}
