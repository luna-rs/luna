package io.luna.game.service;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.ArrayList;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link PersistenceService} implementation that handles logout requests.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LogoutService extends PersistenceService<Player> {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Creates a new {@link LogoutService}.
     *
     * @param world The world.
     */
    public LogoutService(World world) {
        super(world);
    }

    @Override
    void addRequest(String username, Player request) {
        logger.trace("Sending {}'s logout request to a worker...", username);
        workers.execute(() -> {
            try {
                var timer = Stopwatch.createStarted();
                PERSISTENCE.save(request);
                pending.put(username, request);
                logger.debug("Finished saving {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            } catch (Exception e) {
                logger.error(new ParameterizedMessage("Issue servicing {}'s logout request!", username), e);
            }
        });
    }

    @Override
    boolean canFinishRequest(String username, Player request) {
        // TODO Has the player been out of combat for 10 seconds?
        // TODO Has the player been stuck in the pending map for too long?
        return true;
    }

    @Override
    void finishRequest(String username, Player request) {
        world.getPlayers().remove(request);
        logger.info("{} has logged out.", username);
    }

    @Override
    protected void shutDown() {
        logger.trace("A shutdown of the logout service has been requested.");
        workers.shutdown();
        awaitTerminationUninterruptibly(workers);
        logger.fatal("The logout service has been shutdown. Logout and save requests can no longer be serviced.", serviceName());
    }

    /**
     * Saves all players that are currently online. Note that this may take longer than expected to complete since
     * it uses one worker to save all players.
     * <p>
     * It is only safe to call this function from the game thread.
     *
     * @return A listenable future describing the result of the mass save.
     */
    public ListenableFuture<Void> saveAll() {
        logger.trace("Sending mass save request to a worker...");

        // Dump all players into list to ensure thread safety, prepare their save data.
        var savePlayers = new ArrayList<Player>(world.getPlayers().size());
        for (Player player : world.getPlayers()) {
            player.createSaveData();
            savePlayers.add(player);
        }

        return workers.submit(() -> {
            var timer = Stopwatch.createStarted();
            for (Player player : savePlayers) {
                String username = player.getUsername();
                if (!pending.containsKey(username)) {
                    try {
                        PERSISTENCE.save(player);
                        logger.trace("Saved {}'s data.", username);
                    } catch (Exception e) {
                        // To make sure the mass save won't stop because one save failed.
                        logger.error(new ParameterizedMessage("Issue saving {}'s data during mass save.", username), e);
                    }
                }
            }
            logger.debug("Finished saving all online players' data (took {}ms).", box(timer.elapsed().toMillis()));
            return null;
        });
    }

    /**
     * Saves the data for {@code player}. It is only safe to call this function from the game thread.
     *
     * @param player The player to save.
     * @return A listenable future describing the result of the save.
     */
    public ListenableFuture<Void> save(Player player) {
        String username = player.getUsername();
        logger.trace("Sending save request for {} to a worker...", username);
        if (player.getState() != EntityState.ACTIVE) {
            return Futures.immediateFailedFuture(new IllegalStateException(player + " must be ACTIVE in order to request a save."));
        }
        if (pending.containsKey(username)) {
            return Futures.immediateFailedFuture(new IllegalStateException(player + " already has an in-progress save."));
        }
        player.createSaveData();
        return workers.submit(() -> {
            var timer = Stopwatch.createStarted();
            PERSISTENCE.save(player);
            logger.debug("Finished saving {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            return null;
        });
    }
}