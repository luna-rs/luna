package io.luna.game.service;

import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;

import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;

/**
 * A {@link PersistenceService} implementation that handles logout requests.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LogoutRequestService extends PersistenceService<Player> {

    /**
     * Creates a new {@link LogoutRequestService}.
     *
     * @param world The world.
     */
    public LogoutRequestService(World world) {
        super(world);
    }

    @Override
    void addRequest(Player request) {
        synchronized (request) {
            if (request.getState() == EntityState.ACTIVE) {
                // Finalize player and stop all processing.
                request.setState(EntityState.INACTIVE);
                workers.execute(() -> {
                    // Save player and queue them for removal from the world.
                    if (!pending.contains(request) && !Thread.interrupted()) {
                        PERSISTENCE.save(request);
                        pending.add(request);
                    }
                });
            }
        }
    }

    @Override
    void finishRequest(Player request) {
        // Finish the request by removing the player from the world.
        world.getPlayers().remove(request);
    }

    @Override
    protected void shutDown() {
        // Wait for workers to finish saving players, no matter what.
        workers.shutdown();
        awaitTerminationUninterruptibly(workers);
    }

    /**
     * Attempts to save this players data, independent of the backing service workers.
     *
     * @return {@code true} if the save was successful.
     */
    public boolean save(Player player) {
        if (player.getState() == EntityState.ACTIVE) {
            return PERSISTENCE.save(player);
        }
        return false;
    }
}