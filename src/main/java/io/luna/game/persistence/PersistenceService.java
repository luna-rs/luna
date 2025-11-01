package io.luna.game.persistence;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractIdleService;
import io.luna.LunaContext;
import io.luna.game.LoginService;
import io.luna.game.LogoutService;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.util.ExecutorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A background service responsible for loading, saving, and transforming player data asynchronously.
 * <p>
 * The {@code PersistenceService} is intended to offload work from the {@link LoginService} and
 * {@link LogoutService} to keep the main game thread responsive. All I/O operations and heavy data processing
 * are handled in a dedicated single-threaded executor.
 * <p>
 * Threading model:
 * <ul>
 *     <li>All database and filesystem work runs on the persistence worker thread.</li>
 *     <li>Any in-game state changes are synchronized back onto the main game thread using
 *         {@code context.getGame().sync(...)} for thread safety.</li>
 * </ul>
 * <p>
 * Methods in this class are fully thread-safe.
 *
 * @author lare96
 */
public final class PersistenceService extends AbstractIdleService {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The active game world.
     */
    private final World world;

    /**
     * The Luna context.
     */
    private final LunaContext context;

    /**
     * The single-threaded worker that executes all persistence operations.
     */
    private final ExecutorService worker;

    /**
     * Creates a new {@link PersistenceService}.
     *
     * @param world The world.
     */
    public PersistenceService(World world) {
        this.world = world;
        this.context = world.getContext();
        worker = ExecutorUtils.threadPool("PersistenceServiceThread", 1);
    }

    @Override
    protected void startUp() throws Exception {
        logger.trace("Starting the persistence service.");
    }

    @Override
    protected void shutDown() throws Exception {
        logger.trace("A shutdown of the persistence service has been requested.");
        worker.shutdown();
        awaitTerminationUninterruptibly(worker);
        logger.warn("The persistence service has been shutdown.");
    }

    /**
     * Loads a player’s data, applies a transformation to it, and then saves the modified result.
     * <p>
     * If the player is currently online, their data is updated directly on the game thread.
     * Otherwise, the transformation runs on a worker thread using an offline save file.
     *
     * @param username The target username.
     * @param action The transformation to apply to the player’s data.
     * @return A future that completes when the operation finishes.
     */
    public CompletableFuture<Void> transform(String username, Consumer<PlayerData> action) {
        Consumer<Player> playerOnline = player -> {
            try {
                PlayerData saveData = player.createSaveData(); // Create current save data.
                if (saveData == null) {
                    return;
                }
                action.accept(saveData); // Transform it.
                saveData.load(player); // Load it into the player since they're online.
            } catch (Exception e) {
                throw new CompletionException(e);
            }
            logger.debug("Finished transforming {}'s data while online.", username);
        };
        Runnable playerOffline = () -> {
            Stopwatch timer = Stopwatch.createStarted();
            GameSerializerManager serializerManager = world.getSerializerManager();
            try {
                PlayerData data = serializerManager.getSerializer().loadPlayer(world, username);
                if (data == null) {
                    logger.warn("No player data available for {}.", username);
                    return;
                }
                action.accept(data);
                serializerManager.getSerializer().savePlayer(world, username, data);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
            logger.debug("Finished transforming {}'s data while offline (took {}ms).", username,
                    box(timer.elapsed().toMillis()));
        };
        logger.trace("Sending data transformation request for {} to a worker...", username);
        return CompletableFuture.runAsync(() -> {
            // Wait for any pending saves to finish.
            world.getLogoutService().waitForSave(username);
            Optional<Player> playerOptional = world.getPlayer(username);
            if (playerOptional.isPresent()) {
                // Player is online, do online transformation on game thread.
                Player player = playerOptional.get();
                context.getGame().sync(() -> playerOnline.accept(player)).join();
            } else {
                // Otherwise do offline transformation.
                playerOffline.run();
            }
        }, worker);
    }

    /**
     * Asynchronously loads a player’s data from either memory (if online) or persistent storage (if offline).
     *
     * @param username The username to load.
     * @return A future completing with the player’s data.
     */
    public CompletableFuture<PlayerData> load(String username) {
        Player player = world.getPlayerMap().get(username);
        if (player != null) {
            return context.getGame().sync(player::createSaveData);
        }
        logger.trace("Sending load request for {} to a worker...", username);
        return CompletableFuture.supplyAsync(() -> {
            var timer = Stopwatch.createStarted();
            var data = world.getSerializerManager().getSerializer().loadPlayer(world, username);
            logger.debug("Finished loading {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return data;
        }, worker);
    }

    /**
     * Saves the given player’s data asynchronously.
     * <p>
     * If the player is currently being processed by the {@link LogoutService}, this request will fail to
     * avoid duplicate save operations.
     *
     * @param player The player to save.
     * @return A future that completes when the save finishes.
     */
    public CompletableFuture<Void> save(Player player) {
        return context.getGame().sync(player::createSaveData).
                thenCompose(data -> save(player.getUsername(), data));
    }

    /**
     * Asynchronously saves {@code data} under the key {@code username}. The task will fail if the player is being serviced
     * by the {@link LogoutService}.
     *
     * @param username The player's username.
     * @param data The data to save.
     * @return A listenable future describing the result of the save.
     */
    public CompletableFuture<Void> save(String username, PlayerData data) {
        IllegalStateException ex = new IllegalStateException("This player is already being serviced by LogoutService.");
        if (world.getLogoutService().hasRequest(username)) {
            // The LogoutService will handle the saving.
            return CompletableFuture.failedFuture(ex);
        }
        logger.trace("Sending save request for {} to a worker...", username);
        return CompletableFuture.runAsync(() -> {
            if (world.getLogoutService().hasRequest(username)) {
                throw ex;
            }
            var timer = Stopwatch.createStarted();
            world.getSerializerManager().getSerializer().savePlayer(world, username, data);

            long ms = timer.elapsed().toMillis();
            logger.debug("Finished saving {}'s data (took {}ms).", username, box(ms));
        }, worker);
    }

    /**
     * Saves all online players currently in the world.
     * <p>
     * This is a heavy operation and should only be used occasionally (e.g. world backups, shutdowns).
     * Running it too frequently can cause performance degradation, since it serializes every online player in one task.
     *
     * @return A future that completes when the mass save finishes.
     */
    public CompletableFuture<Void> saveAll() {
        logger.trace("Sending mass save request to a worker...");
        return CompletableFuture.runAsync(() -> {
            // Send all requests to a worker.
            var timer = Stopwatch.createStarted();
            for (Player player : world.getPlayerMap().values()) {
                String username = player.getUsername();
                if (world.getLogoutService().hasRequest(username)) {
                    // The LogoutService will handle the saving.
                    continue;
                }
                try {
                    PlayerData data = context.getGame().sync(player::createSaveData).join();
                    world.getSerializerManager().getSerializer().savePlayer(world, username, data);
                    logger.trace("Saved {}'s data.", username);
                } catch (Exception e) {
                    logger.error("Issue saving {}'s data during mass save.", username, e);
                }
            }
            logger.info("Mass save complete (took {}ms).", box(timer.elapsed().toMillis()));
        }, worker);
    }


    /**
     * Deletes all saved data associated with the given username.
     * <p>
     * If the player is online, they will be forcefully logged out first to prevent data corruption.
     * The deletion will only proceed once the player is fully logged out and any pending saves are complete.
     *
     * @param username The username whose data should be deleted.
     * @return A future completing with {@code true} if the record was deleted successfully, or {@code false} if not found.
     */
    public CompletableFuture<Boolean> delete(String username) {
        Player player = world.getPlayerMap().get(username);
        CompletableFuture<Void> logout = player != null ?
                context.getGame().sync(player::forceLogout) : null;
        return CompletableFuture.supplyAsync(() -> {
            if (logout != null) {
                logout.join();
            }
            if (world.getPlayerMap().containsKey(username)) {
                throw new IllegalStateException("The player should be fully logged out before deleting!");
            }
            if (world.getLogoutService().hasRequest(username)) {
                world.getLogoutService().waitForSave(username);
            }
            Stopwatch timer = Stopwatch.createStarted();
            boolean successful = world.getSerializerManager().getSerializer().deletePlayer(world, username);
            if (successful) {
                logger.info("Save record for {} has been deleted (took {}ms).", username, box(timer.elapsed().toMillis()));
            } else {
                logger.warn("Could not find record to delete for {}.", username);
            }
            return successful;
        }, worker);
    }
}