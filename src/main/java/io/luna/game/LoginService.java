package io.luna.game;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import io.luna.game.LoginService.LoginRequest;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.persistence.PlayerData;
import io.luna.net.client.LoginClient;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Persistence-backed login worker service.
 * <p>
 * {@link LoginService} receives login requests from the networking layer, dispatches blocking persistence work onto
 * {@link #workers}, and then finalizes successful logins on the game thread via
 * {@link AuthenticationService#finishRequests()}.
 * <p>
 * <strong>Threading:</strong> worker threads perform the load + password verification decision; final world mutation
 * occurs during {@link #finishRequest(String, LoginRequest)} (game thread).
 *
 * @author lare96
 */
public final class LoginService extends AuthenticationService<LoginRequest> {

    /**
     * Async logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Container for a single login attempt.
     * <p>
     * Holds request context (player/client/message) plus the loaded persistence result. The {@link #loadedData} field
     * is set by the worker thread and read later during finalization.
     */
    public static final class LoginRequest {

        /**
         * Player instance being logged in.
         */
        private final Player player;

        /**
         * Login client that will receive login responses.
         */
        private final LoginClient client;

        /**
         * Raw login request message (credentials / seeds / protocol fields).
         */
        private final LoginRequestMessage message;

        /**
         * Loaded persistence data (written by worker thread, read by game thread).
         */
        private volatile PlayerData loadedData;

        /**
         * Creates a new {@link LoginRequest}.
         *
         * @param player The player.
         * @param client The player's client.
         * @param message The login request message.
         */
        public LoginRequest(Player player, LoginClient client, LoginRequestMessage message) {
            this.player = player;
            this.client = client;
            this.message = message;
        }
    }

    /**
     * Map of async load results keyed by username.
     * <p>
     * Values complete with {@code true} when the login should proceed to final response, or {@code false} when a
     * response was already sent and the login should not proceed.
     */
    private final Map<String, CompletableFuture<Boolean>> loadMap = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link LoginService}.
     *
     * @param world The world.
     */
    public LoginService(World world) {
        super(world);
    }

    @Override
    boolean addRequest(String username, LoginRequest request) {
        if (world.isFull()) {
            // Too many players online.
            request.client.sendLoginResponse(request.player, LoginResponse.WORLD_FULL);
            return false;
        }
        if (world.getBots().exists(username)) {
            // Regular player trying to log in as a bot.
            request.client.sendLoginResponse(request.player, LoginResponse.COULD_NOT_COMPLETE_LOGIN);
            return false;
        }
        if (world.getPlayerMap().containsKey(username) || world.getLogoutService().isSavePending(username)) {
            // Account already online, or a save in progress.
            request.client.sendLoginResponse(request.player, LoginResponse.ACCOUNT_ONLINE);
            return false;
        }

        logger.trace("Sending {}'s login request to a worker...", username);

        // Schedules the load in the worker pool and records the future so finishRequests() can finalize later.
        loadMap.computeIfAbsent(username,
                key -> CompletableFuture.supplyAsync(startWorker(username, request), workers));
        return true;
    }

    @Override
    boolean canFinishRequest(String username, LoginRequest request) {
        CompletableFuture<Boolean> result = loadMap.get(username);
        return result != null && result.isDone();
    }

    @Override
    void finishRequest(String username, LoginRequest request) {
        Boolean result = loadMap.remove(username).join();
        if (Boolean.FALSE.equals(result)) {
            // Load failed or response already sent; do not proceed.
            return;
        }

        logger.trace("Sending {}'s final login response.", username);

        var player = request.player;
        var client = request.client;

        // If final response succeeds, register player and set ACTIVE.
        if (client.sendFinalLoginResponse(player, request.loadedData, request.message)) {
            world.getPlayers().add(player);
            player.setState(EntityState.ACTIVE);
            logger.info("{} has logged in.", username);
        }
    }

    @Override
    protected void shutDown() {
        logger.trace("A shutdown of the login service has been requested.");
        workers.shutdownNow();
        awaitTerminationUninterruptibly(workers);
        logger.fatal("The login service has been shutdown.");
    }

    /**
     * Builds a worker task that loads persistence data and determines the login response.
     * <p>
     * The worker:
     * <ol>
     *   <li>loads {@link PlayerData} using the configured serializer</li>
     *   <li>computes a {@link LoginResponse} using password verification</li>
     *   <li>either stores {@link LoginRequest#loadedData} and returns {@code true}, or sends a login response and
     *   returns {@code false}</li>
     * </ol>
     *
     * @param username Player username.
     * @param request Request context.
     * @return Worker task that returns {@code true} when login should proceed to finalization.
     */
    private Supplier<Boolean> startWorker(String username, LoginRequest request) {
        return () -> {
            var client = request.client;
            try {
                var player = request.player;
                var timer = Stopwatch.createStarted();

                var loadedData = world.getSerializerManager().getSerializer().loadPlayer(world, username);
                var response = client.getLoginResponse(loadedData, player.getPassword());

                if (response == LoginResponse.NORMAL) {
                    request.loadedData = loadedData;
                    logger.debug("Finished loading {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
                    return true;
                } else {
                    // Load/verification wasn't successful, disconnect with login response.
                    client.sendLoginResponse(player, response);
                    return false;
                }
            } catch (Exception e) {
                logger.error("Issue servicing {}'s login request!", username, e);
                client.disconnect();
            }
            return false;
        };
    }
}
