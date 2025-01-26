package io.luna.game.service;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.service.LogoutService.LogoutRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link AuthenticationService} implementation that handles logout requests.
 *
 * @author lare96
 */
public final class LogoutService extends AuthenticationService<LogoutRequest> {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The maximum duration that a player can stay in the logout queue for.
     */
    private static final Duration FORCE_LOGOUT_THRESHOLD = Duration.ofHours(1);

    /**
     * The login request model.
     */
    public static final class LogoutRequest {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The player's client.
         */
        private Instant timeoutAt;

        /**
         * Creates a new {@link LoginService.LoginRequest}.
         *
         * @param player The player.
         */
        public LogoutRequest(Player player) {
            this.player = player;
        }

        /**
         * Sets the {@link Instant} at which this logout request will time out.
         */
        private void setTimeout() {
            timeoutAt = Instant.now().plus(FORCE_LOGOUT_THRESHOLD);
        }

        /**
         * @return {@code true} if this logout request has timed out.
         */
        private boolean isTimeout() {
            return timeoutAt != null && Instant.now().isAfter(timeoutAt);
        }
    }

    /**
     * A set of players pending saves. Used to ensure that a login cannot occur until the player's data is done being
     * saved.
     */
    private final Map<String, Future<?>> saves = new LinkedHashMap<>();

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

        return (/* TODO No combat for 30 seconds && */ player.getControllers().checkLogout() && !player.isLocked()) ||
                request.isTimeout();
    }

    @Override
    void finishRequest(String username, LogoutRequest request) {
        world.getPlayers().remove(request.player);
        saves.put(username, startWorker(username, request.player));
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
     * Starts a worker that will handle a save request and returns the future result of the task.
     *
     * @param username The username of the player being saved.
     * @param request The player being saved.
     * @return The result of the save.
     */
    private Future<?> startWorker(String username, Player request) {
        logger.trace("Servicing {}'s logout request...", username);
        PlayerData saveData = request.createSaveData();
        return workers.submit(() -> {
            try {
                Stopwatch timer = Stopwatch.createStarted();
                world.getSerializerManager().getSerializer().save(world, request.getUsername(), saveData);
                logger.debug("Finished saving {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            } catch (Exception e) {
                logger.error(new ParameterizedMessage("Issue servicing {}'s logout request!", username), e);
            } finally {
                saves.remove(username);
            }
        });
    }

    /**
     * Determines if the player's data is currently being saved.
     *
     * @param username The username of the player.
     * @return {@code true} if their data is being saved.
     */
    public boolean isSavePending(String username) {
        return saves.containsKey(username);
    }
}