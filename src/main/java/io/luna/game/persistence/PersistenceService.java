package io.luna.game.persistence;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractIdleService;
import io.luna.LunaContext;
import io.luna.game.GameService;
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
 * Asynchronous service for loading, saving, transforming, and deleting player data.
 * <p>
 * This service keeps persistence work off the main game thread by using a dedicated single-threaded worker. Expensive
 * file/database operations run on the worker, while reads or writes that touch live player state are synchronized back
 * onto the game thread through {@link GameService#sync(Runnable)}.
 * <p>
 * This is used by login, logout, administrative tooling, mass saves, and offline data edits.
 *
 * @author lare96
 */
public final class PersistenceService extends AbstractIdleService {

    /**
     * The logger for persistence events.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The active game world.
     */
    private final World world;

    /**
     * The shared Luna context.
     */
    private final LunaContext context;

    /**
     * The single-threaded executor used for persistence work.
     */
    private final ExecutorService worker;

    /**
     * Creates a new {@link PersistenceService}.
     *
     * @param world The world this service belongs to.
     */
    public PersistenceService(World world) {
        this.world = world;
        this.context = world.getContext();
        worker = ExecutorUtils.threadPool("PersistenceServiceThread", 1);
    }

    /**
     * Starts this service.
     * <p>
     * Persistence has no eager startup work, so this method intentionally does nothing.
     */
    @Override
    protected void startUp() throws Exception {
    }

    /**
     * Stops this service and waits for queued persistence work to finish.
     */
    @Override
    protected void shutDown() throws Exception {
        worker.shutdown();
        awaitTerminationUninterruptibly(worker);
        logger.warn("The persistence service has been shutdown.");
    }

    /**
     * Loads a player's data, applies a mutation, and saves or reapplies the result.
     * <p>
     * If the player is online, a fresh save snapshot is created from the live player on the game thread, transformed,
     * and loaded back into that same player. If the player is offline, the saved data is loaded and saved on the
     * persistence worker.
     * <p>
     * Any pending logout save for the same username is allowed to complete before the transform begins.
     *
     * @param username The username whose data should be transformed.
     * @param action The mutation to apply to the player's saved data.
     * @return A future that completes when the transform has finished.
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
     * Loads a player's data.
     * <p>
     * Online players are snapshotted from live state on the game thread. Offline players are loaded from persistent
     * storage on the persistence worker.
     *
     * @param username The username to load.
     * @return A future that completes with the player's data, or {@code null} if no saved data exists.
     */
    public CompletableFuture<PlayerData> load(String username) {
        Player player = world.getPlayerMap().get(username);
        if (player != null) {
            return context.getGame().sync(player::createSaveData);
        }
        return CompletableFuture.supplyAsync(() -> {
            var timer = Stopwatch.createStarted();
            var data = world.getSerializerManager().getSerializer().loadPlayer(world, username);
            logger.debug("Finished loading {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return data;
        }, worker);
    }

    /**
     * Saves a player's current live state.
     * <p>
     * The player's save data is first created on the game thread, then passed to {@link #save(String, PlayerData)} for
     * asynchronous persistence.
     *
     * @param player The player to save.
     * @return A future that completes when the save finishes.
     */
    public CompletableFuture<Void> save(Player player) {
        return context.getGame().sync(player::createSaveData).
                thenCompose(data -> save(player.getUsername(), data));
    }

    /**
     * Saves the supplied player data under the given username.
     * <p>
     * This request fails if the same player is currently being serviced by {@link LogoutService}, because logout already
     * owns that save operation. A {@code null} data payload is treated as a no-op.
     *
     * @param username The username to save under.
     * @param data The player data to save.
     * @return A future that completes when the save finishes.
     */
    public CompletableFuture<Void> save(String username, PlayerData data) {
        IllegalStateException ex = new IllegalStateException("This player is already being serviced by LogoutService.");
        if (data == null) {
            // No data to save.
            return CompletableFuture.completedFuture(null);
        } else if (world.getLogoutService().hasRequest(username)) {
            // The LogoutService will handle the saving.
            return CompletableFuture.failedFuture(ex);
        }
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
     * Saves all online non-temporary players.
     * <p>
     * Temporary bots are skipped. Players already being handled by {@link LogoutService} are also skipped because logout
     * owns their save operation.
     * <p>
     * During normal runtime, save snapshots are created on the game thread and serialized on the persistence worker.
     * During shutdown, this method runs synchronously and creates snapshots directly to avoid scheduling more async work.
     *
     * @param shutdown {@code true} if this mass save is being performed during server shutdown.
     *
     * @return A future that completes when the mass save finishes.
     */
    public CompletableFuture<Void> saveAll(boolean shutdown) {
        Runnable r = () -> {
            // Send all requests to a worker.
            var timer = Stopwatch.createStarted();
            for (Player player : world.getPlayerMap().values()) {
                if (player.isBot() && player.asBot().isTemporary()) {
                    continue;
                }
                String username = player.getUsername();
                if (world.getLogoutService().hasRequest(username)) {
                    // The LogoutService will handle the saving.
                    continue;
                }
                try {
                    PlayerData data = shutdown ? player.createSaveData() : context.getGame().sync(player::createSaveData).join();
                    world.getSerializerManager().getSerializer().savePlayer(world, username, data);
                } catch (Exception e) {
                    logger.error("Issue saving {}'s data during mass save.", username, e);
                }
            }
            logger.info("Mass save complete (took {}ms).", box(timer.elapsed().toMillis()));
        };
        if (shutdown) {
            r.run();
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.runAsync(r, worker);
        }
    }

    /**
     * Saves all online non-temporary players asynchronously.
     *
     * @return A future that completes when the mass save finishes.
     */
    public CompletableFuture<Void> saveAll() {
        return saveAll(false);
    }

    /**
     * Deletes all saved data for a username.
     * <p>
     * If the player is online, they are forcefully logged out first. Deletion only continues after the player has left
     * the world and any pending logout save has completed.
     *
     * @param username The username whose save data should be deleted.
     * @return A future that completes with {@code true} if a save record was deleted, or {@code false} if no record was
     * found.
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