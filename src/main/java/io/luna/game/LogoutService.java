package io.luna.game;

import com.google.common.base.Stopwatch;
import io.luna.game.LogoutService.LogoutRequest;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.persistence.PlayerData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Persistence-backed logout worker service.
 * <p>
 * {@link LogoutService} coordinates safe player removal + persistence saving:
 * <ul>
 *   <li>Logout requests are queued into {@link AuthenticationService#pending}.</li>
 *   <li>Each tick, {@link AuthenticationService#finishRequests()} finalizes requests that are eligible to logout.</li>
 *   <li>Player save work runs on {@link #workers}, while game-world mutation runs on the game thread.</li>
 * </ul>
 * <p>
 * This service also enforces a maximum wait time in the logout queue to prevent stuck sessions.
 *
 * @author lare96
 */
public final class LogoutService extends AuthenticationService<LogoutRequest> {

    /**
     * Async logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Maximum time a player may remain pending logout before being forced out.
     */
    private static final Duration FORCE_LOGOUT_THRESHOLD = Duration.ofMinutes(1);

    /**
     * Container for a logout attempt.
     * <p>
     * Holds the player and a completion latch so other systems can wait until the logout (and save) has finished.
     */
    public static final class LogoutRequest {

        /**
         * Player instance being logged out.
         */
        private final Player player;

        /**
         * Absolute timestamp at which this request will time out and be forced to complete.
         */
        private Instant timeoutAt;

        /**
         * Completion latch used by callers that must block until logout/save completes.
         */
        private final CompletableFuture<Void> syncFuture = new CompletableFuture<>();

        /**
         * Creates a new {@link LogoutRequest}.
         *
         * @param player The player.
         */
        public LogoutRequest(Player player) {
            this.player = player;
        }

        /**
         * Arms the timeout deadline for this request.
         */
        private void setTimeout() {
            timeoutAt = Instant.now().plus(FORCE_LOGOUT_THRESHOLD);
        }

        /**
         * Returns whether this request has exceeded its timeout threshold.
         *
         * @return {@code true} if timed out.
         */
        private boolean isTimeout() {
            return timeoutAt != null && Instant.now().isAfter(timeoutAt);
        }

        /**
         * Marks the logout (and any async save) as complete.
         */
        public void complete() {
            syncFuture.complete(null);
        }

        /**
         * Blocks until {@link #complete()} is called.
         */
        public void waitForCompletion() {
            syncFuture.join();
        }
    }

    /**
     * Players currently being saved.
     * <p>
     * This is used to prevent logins while a save is in progress.
     */
    private final Map<String, LogoutRequest> saves = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link LogoutService}.
     *
     * @param world The world.
     */
    public LogoutService(World world) {
        super(world);
    }

    @Override
    boolean addRequest(String username, LogoutRequest request) {
        if (saves.containsKey(username)) {
            logger.warn("A persistence worker is already saving data for {}, dropping logout request...", username);
            return false;
        }
        logger.trace("Adding {}'s logout request to the pending logout map.", username);
        request.setTimeout();
        return true;
    }

    @Override
    boolean canFinishRequest(String username, LogoutRequest request) {
        Player player = request.player;

        // Forced disconnects skip normal checks.
        if (player.getClient().isForcedLogout()) {
            return true;
        }

        // TODO: combat timer gate can be added here.
        return (player.getControllers().checkLogout() && !player.isLocked()) || request.isTimeout();
    }

    @Override
    void finishRequest(String username, LogoutRequest request) {
        PlayerData saveData = request.player.createSaveData();

        // Flush any buffered outbound writes before disconnect/removal.
        request.player.getClient().releasePendingWrites();

        // Remove player from the world immediately (game thread).
        world.getPlayers().remove(request.player);

        if (saveData != null) {
            // Mark save pending and dispatch async persistence work.
            saves.put(username, request);
            startWorker(username, request, saveData);
        } else {
            // No persistence required (or could not be generated); treat as complete.
            request.complete();
        }

        logger.info("{} has logged out.", username);
    }

    @Override
    protected void shutDown() {
        logger.trace("A shutdown of the logout service has been requested.");
        workers.shutdown();
        awaitTerminationUninterruptibly(workers);
        logger.fatal("The logout service has been shutdown.");
    }

    /**
     * Dispatches an asynchronous save on the worker pool and completes the request when finished.
     * <p>
     * On completion, the request is removed from {@link #saves} and {@link LogoutRequest#complete()} is invoked
     * so waiters can proceed.
     *
     * @param username Username being saved.
     * @param request Logout request context.
     * @param saveData Snapshot of persistence data to write.
     */
    private void startWorker(String username, LogoutRequest request, PlayerData saveData) {
        logger.trace("Servicing {}'s logout request...", username);
        workers.submit(() -> {
            try {
                Stopwatch timer = Stopwatch.createStarted();

                world.getSerializerManager().getSerializer().savePlayer(world, username, saveData);

                if (request.player.isBot()) {
                    world.getBots().remove(request.player.asBot());
                }

                logger.debug("Finished saving {}'s data (took {}ms).",
                        username, box(timer.elapsed().toMillis()));
            } catch (Exception e) {
                logger.error("Issue servicing {}'s logout request!", username, e);
            } finally {
                saves.remove(username);
                request.complete();
            }
        });
    }

    /**
     * Returns whether the specified username is currently blocked from logging in due to a pending logout/save.
     *
     * @param username The username.
     * @return {@code true} if a logout request or save is in-flight.
     */
    public boolean isSavePending(String username) {
        return saves.containsKey(username) || hasRequest(username);
    }

    /**
     * Blocks until any pending logout/save for {@code username} has completed.
     * <p>
     * This is used to guarantee persistence ordering (e.g., preventing login during a save window).
     *
     * @param username The username.
     */
    public void waitForSave(String username) {
        LogoutRequest request = pending.get(username);
        if (request != null) {
            request.waitForCompletion();
        }
        request = saves.get(username);
        if (request != null) {
            request.waitForCompletion();
        }
    }
}
