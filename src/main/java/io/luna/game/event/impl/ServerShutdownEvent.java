package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.util.AsyncExecutor;

import java.util.concurrent.ExecutorService;

/**
 * A specialized event type sent when the server begins gracefully shutting down. Logic within these intercepted events
 * is executed on the game thread. The provided {@link ExecutorService} can be used for scripts that require
 * asynchronous shutdown logic. The game will not shutdown until the asynchronous tasks finish.
 *
 * @author lare96
 */
public final class ServerShutdownEvent extends Event {

    /**
     * The executor to handle tasks.
     */
    private final AsyncExecutor taskPool;

    /**
     * Creates a new {@link ServerShutdownEvent}.
     *
     * @param taskPool The executor to handle tasks.
     */
    public ServerShutdownEvent(AsyncExecutor taskPool) {
        this.taskPool = taskPool;
    }

    /**
     * @return The executor to handle tasks.
     */
    public AsyncExecutor getTaskPool() {
        return taskPool;
    }
}
