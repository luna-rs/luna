package io.luna.game.service;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.luna.game.model.World;
import io.luna.game.model.mob.persistence.PlayerPersistence;
import io.luna.util.ExecutorUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An {@link AbstractIdleService} implementation that manages threads for persistence based services, required for logging in
 * and out. This is necessary in order to avoid overworking the networking threads.
 *
 * @author lare96 <http://github.com/lare96>
 */
abstract class PersistenceService<T> extends AbstractIdleService {

    /**
     * The player persistence manager.
     */
    static final PlayerPersistence PERSISTENCE = new PlayerPersistence();

    /**
     * The amount of requests to service per tick.
     */
    static final int REQUESTS_THRESHOLD = 50;

    /**
     * The world.
     */
    final World world;

    /**
     * The workers that will service requests.
     */
    final ListeningExecutorService workers = ExecutorUtils.newCachedThreadPool();

    /**
     * The queue of pending requests.
     */
    final Queue<T> pending = new ConcurrentLinkedQueue<>();

    /**
     * Creates a new {@link PersistenceService}.
     *
     * @param world The world.
     */
    PersistenceService(World world) {
        this.world = world;
    }

    @Override
    protected final void startUp() throws Exception {
        // Persistence services don't require startup operations.
    }

    /**
     * Polls the queue of pending requests and finishes them.
     */
    public final void finishPendingRequests() {
        if (state() == State.RUNNING) {
            for (int loop = 0; loop < REQUESTS_THRESHOLD; loop++) {
                var next = pending.poll();
                if (next == null) {
                    break;
                }
                finishRequest(next);
            }
        }
    }

    /**
     * Submits a new request to be serviced.
     *
     * @param request The request.
     */
    public final void submit(T request) {
        if (state() == State.RUNNING && !pending.contains(request)) {
            addRequest(request);
        }
    }

    /**
     * Invoked before the request is added to the pending queue. It must be done within this function to ensure proper
     * behaviour.
     */
    abstract void addRequest(T request);

    /**
     * Invoked after the request has been polled from the pending queue. Perform finalization processing here.
     */
    abstract void finishRequest(T request);
}