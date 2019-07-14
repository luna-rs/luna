package io.luna.game.service;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.luna.game.model.World;
import io.luna.game.model.mob.persistence.PlayerPersistence;
import io.luna.util.ExecutorUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AbstractIdleService} implementation that manages threads for persistence based services, required for logging in
 * and out. This is necessary in order to avoid overworking the networking threads.
 *
 * @param <T> The request type.
 * @author lare96 <http://github.com/lare96>
 */
abstract class AuthenticationService<T> extends AbstractIdleService {

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
    final ListeningExecutorService workers;

    /**
     * The map of pending requests.
     */
    final Map<String, T> pending = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link AuthenticationService}.
     *
     * @param world The world.
     */
    AuthenticationService(World world) {
        this.world = world;
        workers = ExecutorUtils.threadPool(serviceName() + "Worker");
    }

    @Override
    protected final void startUp() throws Exception {
        // Persistence services don't require startup operations.
    }

    /**
     * Iterates the set of pending requests and finishes them.
     */
    public final void finishRequests() {
        if (state() == State.RUNNING) {
            var iterator = pending.entrySet().iterator();
            for (int loop = 0; loop < REQUESTS_THRESHOLD; loop++) {
                if (!iterator.hasNext()) {
                    break;
                }
                var next = iterator.next();
                String username = next.getKey();
                T request = next.getValue();
                if (canFinishRequest(username, request)) {
                    finishRequest(username, request);
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Determines if there is a pending request with {@code username} as a key.
     *
     * @param username The key.
     * @return {@code true} if the key is present.
     */
    public final boolean hasRequest(String username) {
        return pending.containsKey(username);
    }

    /**
     * Submits a new request to be serviced.
     *
     * @param request The request.
     */
    public final void submit(String username, T request) {
        if (state() == State.RUNNING && !pending.containsKey(username)) {
            addRequest(username, request);
        }
    }

    /**
     * Invoked before the request is added to the pending set. This function must make an attempt to add the request to
     * the pending set, either in the calling thread or another thread.
     */
    abstract void addRequest(String username, T request);

    /**
     * Invoked to determine if the request can be removed from the pending set. Return {@code true} to remove the request.
     */
    abstract boolean canFinishRequest(String username, T request);

    /**
     * Invoked after the request has been removed from the pending set. Perform finalization processing here.
     */
    abstract void finishRequest(String username, T request);
}