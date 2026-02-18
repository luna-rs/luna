package io.luna.game;

import com.google.common.util.concurrent.AbstractIdleService;
import io.luna.game.model.World;
import io.luna.util.ExecutorUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Base {@link AbstractIdleService} for authentication-style persistence work (login/logout).
 * <p>
 * This service exists to keep persistence and account I/O off the Netty event loop threads. Subclasses typically:
 * <ul>
 *   <li>accept requests via {@link #submit(String, Object)}</li>
 *   <li>dispatch background work on {@link #workers}</li>
 *   <li>store in-flight requests in {@link #pending}</li>
 *   <li>finalize completed requests on the game thread via {@link #finishRequests()}</li>
 * </ul>
 *
 * <h3>Two-phase model</h3>
 * <ol>
 *   <li><b>Queue/dispatch</b> (any thread): {@link #submit(String, Object)} validates and records a request, and the
 *       subclass usually schedules persistence work on {@link #workers}.</li>
 *   <li><b>Finalize</b> (game thread): {@link #finishRequests()} polls up to {@link #REQUESTS_THRESHOLD} pending
 *       entries per tick and applies results to game state.</li>
 * </ol>
 * The per-tick threshold prevents worst-case spikes (e.g., mass logins/logouts) from monopolizing a single game tick.
 *
 * @param <T> The request type stored in {@link #pending}.
 * @author lare96
 */
abstract class AuthenticationService<T> extends AbstractIdleService {

    /**
     * Maximum number of pending requests to attempt to finalize per tick.
     */
    static final int REQUESTS_THRESHOLD = 50;

    /**
     * World reference for scheduling/finalization context.
     */
    final World world;

    /**
     * Worker pool used for persistence tasks.
     * <p>
     * Subclasses typically submit blocking I/O here (database reads/writes, file system, etc.).
     */
    final ExecutorService workers;

    /**
     * Map of in-flight requests keyed by username.
     * <p>
     * This serves as:
     * <ul>
     *   <li>a de-duplication mechanism (one request per username at a time)</li>
     *   <li>a hand-off structure between worker threads and the game thread</li>
     * </ul>
     */
    final ConcurrentHashMap<String, T> pending = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link AuthenticationService}.
     *
     * @param world The world instance.
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
     * Attempts to finalize pending requests.
     * <p>
     * This should be called from the game loop (once per tick). It iterates up to {@link #REQUESTS_THRESHOLD} entries
     * and finalizes those that are ready according to {@link #canFinishRequest(String, Object)}.
     * <p>
     * Finalization is performed by {@link #finishRequest(String, Object)} and the entry is then removed from {@link #pending}.
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
     * Checks whether there is already a pending request for {@code username}.
     *
     * @param username The username key.
     * @return {@code true} if a request is currently pending for that username.
     */
    public final boolean hasRequest(String username) {
        return pending.containsKey(username);
    }

    /**
     * Submits a new request if the service is running and no request is already pending for {@code username}.
     * <p>
     * The pending insertion is performed atomically via {@link ConcurrentHashMap#computeIfAbsent(Object, Function)}
     * to prevent duplicate submissions when multiple workers/threads submit concurrently.
     * <p>
     * Whether the request is accepted is determined by {@link #addRequest(String, Object)}.
     *
     * @param username The username key for de-duplication.
     * @param request The request payload.
     */
    public final void submit(String username, T request) {
        if (state() == State.RUNNING) {
            // Atomically compute the value (or no value) since we have multiple workers potentially accessing this.
            pending.computeIfAbsent(username, key -> addRequest(username, request) ? request : null);
        }
    }

    /**
     * Validates and begins servicing a request.
     * <p>
     * Implementations typically:
     * <ul>
     *   <li>validate the request</li>
     *   <li>schedule blocking work onto {@link #workers}</li>
     *   <li>return {@code true} if the request should be inserted into {@link #pending}</li>
     * </ul>
     *
     * @param username The username key.
     * @param request The request payload.
     * @return {@code true} to store in {@link #pending}, {@code false} to reject the request.
     */
    abstract boolean addRequest(String username, T request);

    /**
     * Returns whether a pending request is ready to be finalized on the game thread.
     *
     * @param username The username key.
     * @param request The request payload.
     * @return {@code true} if the request can be finalized and removed from {@link #pending}.
     */
    abstract boolean canFinishRequest(String username, T request);

    /**
     * Finalizes a request on the game thread after it is deemed ready.
     * <p>
     * This is where implementations should apply results to game state (log the player in, persist logout state,
     * update registries, etc.). Called immediately before the request is removed from {@link #pending}.
     *
     * @param username The username key.
     * @param request The request payload.
     */
    abstract void finishRequest(String username, T request);
}
