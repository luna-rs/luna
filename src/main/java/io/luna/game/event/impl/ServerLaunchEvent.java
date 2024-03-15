package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.util.AsyncExecutor;

/**
 * A specialized event type sent when the server fully launches. All code is ran on the game thread. To load blocking resources,
 * please use the provided {@link AsyncExecutor}. The game will not go online until all asynchronous tasks complete.
 *
 * @author lare96
 */
public final class ServerLaunchEvent extends Event {

    /**
     * The executor to handle tasks.
     */
    private final AsyncExecutor taskPool;

    /**
     * Creates a new {@link ServerShutdownEvent}.
     *
     * @param taskPool The executor to handle tasks.
     */
    public ServerLaunchEvent(AsyncExecutor taskPool) {
        this.taskPool = taskPool;
    }

    /**
     * The executor to handle tasks.
     */
    public AsyncExecutor getTaskPool() {
        return taskPool;
    }
}
