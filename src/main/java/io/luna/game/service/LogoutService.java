package io.luna.game.service;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

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
     * Creates a new {@link LogoutService}.
     *
     * @param world The world.
     */
    public LogoutService(World world) {
        super(world);
    }

    @Override
    void addRequest(String username, Player request) {
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
        workers.execute(() -> {
            try {
                var timer = Stopwatch.createStarted();
                PERSISTENCE.save(request);
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
}