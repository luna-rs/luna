package io.luna.game.service;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.model.mob.persistence.PlayerSerializerManager;
import io.luna.util.ExecutorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An {@link AbstractIdleService} responsible for arbitrary loads and saves. This service exists to take any potential
 * load off of the {@link LoginService} and {@link LogoutService}.
 * <p>
 * It's backed by a single thread, so requests are considered low priority and are not guaranteed to execute right
 * away. These functions should be used on the game thread to ensure complete thread safety.
 *
 * @author lare96
 */
public final class PersistenceService extends AbstractIdleService {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The world.
     */
    private final World world;

    /**
     * The worker that will run all persistence tasks.
     */
    private final ListeningExecutorService worker;

    /**
     * Creates a new {@link PersistenceService}.
     *
     * @param world The world.
     */
    public PersistenceService(World world) {
        this.world = world;

        ThreadFactory threadFactory = ExecutorUtils.threadFactory(PersistenceService.class);
        worker = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    protected void startUp() throws Exception {

    }

    @Override
    protected void shutDown() throws Exception {
        logger.trace("A shutdown of the persistence service has been requested.");
        worker.shutdown();
        awaitTerminationUninterruptibly(worker);
        logger.fatal("The persistence service has been shutdown.");
    }

    /**
     * Asynchronously loads a player's data, applies {@code action} to it, and then saves the modified data.
     *
     * @param username The username of the player.
     * @param action The action to apply.
     * @return The future, describing the result of the task.
     */
    public ListenableFuture<Void> transform(String username, Consumer<PlayerData> action) {
        if (world.getPlayerMap().containsKey(username)) {
            Optional<Player> optionalPlayer = world.getPlayer(username);
            if (optionalPlayer.isEmpty()) {
                throw new IllegalStateException("Player exists in player map but not game map.");
            }
            Player player = optionalPlayer.get();
            PlayerData saveData = player.createSaveData(); // Create current save data.
            action.accept(saveData); // Transform it.
            saveData.load(player); // Load it into the player since they're online.
            return save(username, saveData); // Send a save request.
        }

        logger.trace("Sending data transformation request for {} to a worker...", username);
        return worker.submit(() -> {
            Stopwatch timer = Stopwatch.createStarted();
            PlayerSerializerManager serializerManager = world.getSerializerManager();
            PlayerData data = serializerManager.getSerializer().load(world, username);
            if (data == null) {
                throw new NoSuchElementException("No player data available for " + username);
            }
            action.accept(data);
            serializerManager.getSerializer().save(world, username, data);
            logger.debug("Finished transforming {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return null;
        });
    }

    /**
     * Asynchronously loads a player's data.
     *
     * @param username The username of the player.
     * @return The future, describing the result of the task.
     */
    public ListenableFuture<PlayerData> load(String username) {
        Player player = world.getPlayerMap().get(username);
        if (player != null) {
            var data = new PlayerData(username).save(player);
            return Futures.immediateFuture(data);
        }
        logger.trace("Sending load request for {} to a worker...", username);
        return worker.submit(() -> {
            var timer = Stopwatch.createStarted();
            var data = world.getSerializerManager().getSerializer().load(world, username);
            logger.debug("Finished loading {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return data;
        });
    }

    /**
     * Asynchronously saves {@code player}'s data. The task will fail if the player is being serviced by the
     * {@link LogoutService}.
     *
     * @param player The player to save.
     * @return The future, describing the result of the task.
     */
    public ListenableFuture<Void> save(Player player) {
        return save(player.getUsername(), player.createSaveData());
    }

    /**
     * Asynchronously saves {@code data} under the key {@code username}. The task will fail if the player is being serviced
     * by the {@link LogoutService}.
     *
     * @param username The player's username.
     * @param data The data to save.
     * @return A listenable future describing the result of the save.
     */
    public ListenableFuture<Void> save(String username, PlayerData data) {
        if (world.getLogoutService().hasRequest(username)) {
            // The LogoutService will handle the saving.
            IllegalStateException ex = new IllegalStateException("This player is already being serviced by LogoutService.");
            return Futures.immediateFailedFuture(ex);
        }
        logger.trace("Sending save request for {} to a worker...", username);
        return worker.submit(() -> {
            var timer = Stopwatch.createStarted();
            world.getSerializerManager().getSerializer().save(world, username, data);
            logger.debug("Finished saving {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return null;
        });
    }

    /**
     * Saves all players that are currently online. Note that this may take longer than expected to complete since it saves
     * every player in one task.
     * <p>
     * <strong>Caution: Overuse of this method can put significant stress on the backing worker. Invocations should not exceed
     * more than a 1-2 times per minute.</strong>
     *
     * @return A listenable future describing the result of the mass save.
     */
    public ListenableFuture<Void> saveAll() {
        logger.trace("Sending mass save request to a worker...");
        List<PlayerData> saveList = new ArrayList<>(world.getPlayers().size());
        for (Player player : world.getPlayers()) {
            String username = player.getUsername();
            if (world.getLogoutService().hasRequest(username)) {
                // The LogoutService will handle the saving.
                continue;
            }
            saveList.add(player.createSaveData());
        }
        return worker.submit(() -> {
            var timer = Stopwatch.createStarted();
            for (PlayerData data : saveList) {
                String username = data.getUsername();
                if (world.getLogoutService().hasRequest(username)) {
                    // The LogoutService will handle the saving.
                    continue;
                }
                try {
                    world.getSerializerManager().getSerializer().save(world, username, data);
                    logger.trace("Saved {}'s data.", username);
                } catch (Exception e) {
                    logger.error(new ParameterizedMessage("Issue saving {}'s data during mass save.", username), e);
                }
            }
            logger.info("Mass save complete (took {}ms).", box(timer.elapsed().toMillis()));
            return null;
        });
    }

    /**
     * Deletes the record of save data for {@code username}. The player should be logged out when using this
     * to prevent re-saving of the deleted data.
     *
     * @param username The username of the player to delete.
     * @return A listenable future describing the result of the deletion.
     */
    public ListenableFuture<Boolean> delete(String username) {
        if (world.getLogoutService().hasRequest(username) || world.getPlayerMap().containsKey(username)) {
            IllegalStateException exception =
                    new IllegalStateException("The player should be fully logged out before deleting its record, to prevent overwrites!");
            return Futures.immediateFailedFuture(exception);
        }
        return worker.submit(() -> {
            Stopwatch timer = Stopwatch.createStarted();
            boolean successful = world.getSerializerManager().getSerializer().delete(world, username);
            if (successful) {
                logger.info("Save record for {} has been deleted (took {}ms).", username, box(timer.elapsed().toMillis()));
            } else {
                logger.warn("Could not find record to delete for {}.", username);
            }
            return successful;
        });
    }
}