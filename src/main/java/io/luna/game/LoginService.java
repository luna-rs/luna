package io.luna.game;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import io.luna.game.LoginService.LoginRequest;
import io.luna.game.GameSettings.PasswordStrength;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.bot.PlayerAutomation;
import io.luna.Luna;
import io.luna.net.LunaChannelFilter;
import io.luna.game.persistence.PlayerData;
import io.luna.net.client.LoginClient;
import io.luna.net.msg.login.LoginRequestMessage;
import io.luna.net.msg.login.LoginResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

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
         * Prepared password hash handed from the worker to the final player before world registration.
         */
        private volatile String passwordHash;

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
     * Password preparation performed only by authentication workers.
     */
    private final Function<String, String> passwordHasher;

    /**
     * Creates a new {@link LoginService}.
     *
     * @param world The world.
     */
    public LoginService(World world) {
        super(world);
        passwordHasher = LoginService::hashPassword;
    }

    /**
     * Creates a login service with explicit worker and password preparation dependencies.
     *
     * @param world The world.
     * @param workers The workers owned by this service.
     * @param passwordHasher The function used to prepare new passwords.
     */
    LoginService(World world, ExecutorService workers, Function<String, String> passwordHasher) {
        super(world, workers);
        this.passwordHasher = passwordHasher;
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

        var client = request.client;
        PlayerRights rights = PlayerRights.forLogin(request.player.getUsername(),
                request.loadedData == null ? null : request.loadedData.rights,
                Luna.settings().game().betaMode(), LunaChannelFilter.WHITELIST.contains(client.getIpAddress()));
        var player = PlayerAutomation.prepareLoginPlayer(request.player, rights);

        // If final response succeeds, register player and set ACTIVE.
        if (client.sendFinalLoginResponse(player, request.loadedData, request.message)) {
            player.setHashedPassword(request.passwordHash);
            world.getPlayers().add(player);
            player.setState(EntityState.ACTIVE);
            logger.info("{} has logged in.", username);
        }
    }

    @Override
    protected void shutDown() {
        workers.shutdownNow();
        awaitTerminationUninterruptibly(workers);
        logger.fatal("The login service has been shutdown.");
    }

    /**
     * Prepares a password on an authentication worker and installs it on the same active session before saving.
     * Call on the game thread. Superseded requests and sessions that leave the world complete with {@code false}.
     *
     * @param player The active player requesting the change.
     * @param password The new plaintext password, between one and twenty characters.
     * @return Whether this request was saved while it still belonged to the same active session.
     */
    public CompletableFuture<Boolean> changePassword(Player player, String password) {
        String normalizedPassword = password == null ? null : PlayerCredentials.normalizePassword(password);
        if (normalizedPassword == null || normalizedPassword.isEmpty() || normalizedPassword.length() > 20) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Password length must be 1-20 characters."));
        }
        String username = player.getUsername();
        var target = new WeakReference<>(player);
        if (!isCurrentSession(player, username)) {
            return CompletableFuture.completedFuture(false);
        }
        long revision = player.beginPasswordChange();
        var result = new CompletableFuture<Boolean>();
        try {
            CompletableFuture.supplyAsync(() -> passwordHasher.apply(normalizedPassword), workers).
                    whenComplete((hash, error) -> onGameThread(() ->
                            finishPasswordChange(target, username, revision, normalizedPassword, hash, error, result), result));
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    /**
     * Installs a prepared password before a game-thread save snapshot, without exposing the password to logs.
     */
    private void finishPasswordChange(WeakReference<Player> target, String username, long revision, String password,
                                      String hash, Throwable error, CompletableFuture<Boolean> result) {
        Player player = target.get();
        if (!isCurrentSession(player, username) || !player.isPasswordChangeCurrent(revision)) {
            result.complete(false);
        } else if (error != null) {
            logger.error("Issue preparing {}'s password change.", username, error);
            result.completeExceptionally(error);
        } else {
            player.applyPasswordChange(revision, password, hash);
            player.save().whenComplete((ignored, saveError) -> onGameThread(() -> {
                Player current = target.get();
                if (!isCurrentSession(current, username) || !current.isPasswordChangeCurrent(revision)) {
                    result.complete(false);
                } else if (saveError != null) {
                    logger.error("Issue saving {}'s password change.", username, saveError);
                    result.completeExceptionally(saveError);
                } else {
                    result.complete(true);
                }
            }, result));
        }
    }

    /**
     * Checks identity as well as username so completion cannot change a replacement login.
     */
    private boolean isCurrentSession(Player player, String username) {
        return player != null && player.getState() == EntityState.ACTIVE &&
                world.getPlayerMap().get(username) == player && !player.getClient().isPendingLogout() &&
                !world.getLogoutService().isSavePending(username);
    }

    /**
     * Completes failed scheduling or game-thread continuations instead of abandoning the requesting future.
     */
    private void onGameThread(Runnable action, CompletableFuture<?> result) {
        try {
            world.getContext().getGame().sync(action).whenComplete((ignored, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        } catch (Error error) {
            result.completeExceptionally(error);
            throw error;
        }
    }

    /**
     * Prepares the configured stored credential for a new account or changed password.
     */
    private static String hashPassword(String password) {
        PasswordStrength strength = Luna.settings().game().passwordStrength();
        return strength == PasswordStrength.NONE ? "password" :
                BCrypt.hashpw(password, BCrypt.gensalt(strength.getRounds()));
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
                    request.passwordHash = loadedData == null ? passwordHasher.apply(request.message.getPassword()) :
                            loadedData.password;
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
