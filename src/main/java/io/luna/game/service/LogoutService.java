package io.luna.game.service;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Set;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link AuthenticationService} implementation that handles logout requests.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LogoutService extends AuthenticationService<Player> {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A set of players pending saves. Used to ensure that a login cannot occur until the player's data is done being
     * saved.
     */
    private final Set<String> pendingSaves = Sets.newConcurrentHashSet();

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
        if (pendingSaves.contains(username)) {
            logger.warn("Duplicate save request from " + username + " stopped.");
            return;
        }
        logger.trace("Adding {}'s logout request to the pending map.", username);
        pending.put(username, request);
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
        logger.trace("Servicing {}'s logout request...", username);
        pendingSaves.add(username);
        workers.execute(() -> {
            try {
                var timer = Stopwatch.createStarted();
                PERSISTENCE.save(request);
                pendingSaves.remove(username);
                logger.debug("Finished saving {}'s data (took {}ms).", username, box(timer.elapsed().toMillis()));
            } catch (Exception e) {
                logger.error(new ParameterizedMessage("Issue servicing {}'s logout request!", username), e);
            }
        });
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
     * Determines if the player's data is currently being saved.
     *
     * @param username The username of the player.
     * @return {@code true} if their data is being saved.
     */
    public boolean isSavePending(String username) {
        return pendingSaves.contains(username);
    }
}