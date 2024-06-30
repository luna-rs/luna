package io.luna.game.service;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.luna.game.model.World;
import io.luna.util.ExecutorUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AbstractIdleService} implementation that manages threads for persistence based services, required for logging in
 * and out. This is necessary in order to avoid overworking the networking threads.
 *
 * @param <T> The request type.
 * @author lare96
 */
abstract class AuthenticationService<T> extends AbstractIdleService {

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
    private final ConcurrentHashMap<String, T> pending = new ConcurrentHashMap<>();

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
     * Iterates the set of pending requests and finalizes them.
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
        if (state() == State.RUNNING) {
            // Atomically compute the value (or no value) since we have multiple login workers potentially
            // accessing this.
            pending.computeIfAbsent(username, key -> addRequest(username, request) ? request : null);
        }
    }

    /**
     * Determines if the request can be added to {@link #pending}.
     *
     * @return {@code true} to add the request, {@code false} to cancel.
     */
    abstract boolean addRequest(String username, T request);

    /**
     * Determines if the request is no longer pending and can be finalized.
     *
     * @return {@code true} if the request is no longer pending.
     */
    abstract boolean canFinishRequest(String username, T request);

    /**
     * Run after the request is finalized and removed from {@link #pending}.
     */
    abstract void finishRequest(String username, T request);
}