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
import io.luna.util.ExecutorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An {@link AbstractIdleService} responsible for arbitrary loads and saves. This service exists to take any potential
 * load off of the {@link LoginService} and {@link LogoutService}. It's backed by a single thread, so requests are considered low priority
 * and are not guaranteed to execute right away. All functions can be used safely across multiple threads.
 *
 * @author lare96 <http://github.com/lare96>
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

        var threadFactory = ExecutorUtils.threadFactory(PersistenceService.class);
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
     * Asynchronously loads a player's data, applies {@code action} to it, and then saves the modified data. The task will fail
     * if the player is logged in.
     *
     * @param username The username of the player.
     * @param action The action to apply.
     * @return The future, describing the result of the task.
     */
    public ListenableFuture<Void> transform(String username, Consumer<PlayerData> action) {
        logger.trace("Sending data transformation request for {} to a worker...", username);
        return worker.submit(() -> {
            if (world.getPlayerMap().containsKey(username)) {
                throw new IllegalStateException("Cannot perform data transformation on logged in player.");
            }

            var timer = Stopwatch.createStarted();
            var data = AuthenticationService.PERSISTENCE.load(username);
            if (data == null) {
                throw new NoSuchElementException("No player data available for " + username);
            }
            action.accept(data);
            AuthenticationService.PERSISTENCE.save(username, data);
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
            var data = new PlayerData().save(player);
            return Futures.immediateFuture(data);
        }
        logger.trace("Sending load request for {} to a worker...", username);
        return worker.submit(() -> {
            var timer = Stopwatch.createStarted();
            var data = AuthenticationService.PERSISTENCE.load(username);
            if (data == null) {
                throw new NoSuchElementException("No player data available for " + username);
            }
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
        String username = player.getUsername();
        if (world.getLogoutService().hasRequest(username)) {
            // The LogoutService will handle the saving.
            IllegalStateException ex = new IllegalStateException("This player is already being serviced by LogoutService.");
            return Futures.immediateFailedFuture(ex);
        }
        player.createSaveData();
        return save(username, player.getSaveData());
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
            AuthenticationService.PERSISTENCE.save(username, data);
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
        return worker.submit(() -> {
            var timer = Stopwatch.createStarted();
            for (Player player : world.getPlayerMap().values()) {
                String username = player.getUsername();
                if (world.getLogoutService().hasRequest(username)) {
                    // The LogoutService will handle the saving.
                    continue;
                }
                try {
                    player.createSaveData();
                    AuthenticationService.PERSISTENCE.save(player);
                    logger.trace("Saved {}'s data.", username);
                } catch (Exception e) {
                    logger.error(new ParameterizedMessage("Issue saving {}'s data during mass save.", username), e);
                }
            }
            logger.debug("Mass save complete (took {}ms).", box(timer.elapsed().toMillis()));
            return null;
        });
    }
}