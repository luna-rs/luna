package io.luna.game.service;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.service.LoginService.LoginRequest;
import io.luna.net.client.LoginClient;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
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

    private final Map<String, Future<Boolean>> loadResultMap = new ConcurrentHashMap<>();

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
        logger.trace("Sending {}'s login request to a worker...", username);
        Callable<Boolean> loadTask = startWorker(username, request);
        loadResultMap.putIfAbsent(username, workers.submit(loadTask));
        return true;
    }

    @Override
    boolean canFinishRequest(String username, LoginRequest request) {
        Future<Boolean> result = loadResultMap.get(username);
        return result.isDone();
    }

    @Override
    void finishRequest(String username, LoginRequest request) {
        try {
            Future<Boolean> result = loadResultMap.remove(username);
            if (!Futures.getDone(result)) {
                // Load failed, don't send final login response.
                return;
            }
        } catch (ExecutionException e) {
            logger.error("Failed to get result from the login persistence worker.", e);
            return; // Load failed for some other reason.
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
    protected void shutDown() throws Exception {
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
    private Callable<Boolean> startWorker(String username, LoginRequest request) {
        return () -> {
            var client = request.client;
            try {
                var player = request.player;
                var timer = Stopwatch.createStarted();
                var loadedData = world.getSerializerManager().getSerializer().load(world, username);
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
                logger.error(new ParameterizedMessage("Issue servicing {}'s login request!", username), e);
                client.disconnect();
            }
            return false;
        };
    }

    public boolean isLoadPending(String username) {
        return loadResultMap.containsKey(username);
    }
}