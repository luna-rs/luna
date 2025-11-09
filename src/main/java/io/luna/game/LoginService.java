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
 * A {@link AuthenticationService} implementation that handles login requests.
 *
 * @author lare96
 */
public final class LoginService extends AuthenticationService<LoginRequest> {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The login request model.
     */
    public static final class LoginRequest {

        /**
         * The player.
         */
        private final Player player;

        /**
         * The player's client.
         */
        private final LoginClient client;

        /**
         * The login request message.
         */
        private final LoginRequestMessage message;

        /**
         * The loaded data.
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
     * A map containing the results of all load requests.
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
        if(world.getBots().exists(username)) {
            // Regular player trying to log in as a bot.
            request.client.sendLoginResponse(request.player, LoginResponse.COULD_NOT_COMPLETE_LOGIN);
            return false;
        }
        if (world.getPlayerMap().containsKey(username) ||
                world.getLogoutService().isSavePending(username)) {
            // Short-circuit here, faster and prevents wasting resources.
            request.client.sendLoginResponse(request.player, LoginResponse.ACCOUNT_ONLINE);
            return false;
        }
        logger.trace("Sending {}'s login request to a worker...", username);
        Supplier<Boolean> loadTask = startWorker(username, request);
        loadMap.putIfAbsent(username, CompletableFuture.supplyAsync(loadTask, workers));
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
            // Load failed, don't send final login response.
            return;
        }
        logger.trace("Sending {}'s final login response.", username);
        var player = request.player;
        var client = request.client;
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
     * Starts a worker that will handle a load request and returns the future result of the task.
     *
     * @param username The username of the player being loaded.
     * @param request The request to handle.
     * @return The result of the load ({@code true} if the login response was normal).
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
                    // Load wasn't successful, disconnect with login response.
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