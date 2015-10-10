package io.luna.game.model;

import io.luna.game.model.mobile.MobileEntityList;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.region.RegionManager;
import io.luna.net.session.GameSession;
import io.luna.net.session.SessionState;
import io.luna.util.StringUtils;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the various types in the {@code io.luna.game.model} package and
 * subpackages.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    // TODO Prevent player from logging out while they're in combat

    /**
     * The total amount of {@link Player}s that can be either logged in or out
     * per game loop.
     */
    public static final int DEQUEUE_LIMIT = 50;

    /**
     * The list of {@link Player}s in the world.
     */
    private final MobileEntityList<Player> players = new MobileEntityList<>(EntityConstants.MAXIMUM_PLAYERS);

    /**
     * The list of {@link Npc}s in the world.
     */
    private final MobileEntityList<Npc> npcs = new MobileEntityList<>(EntityConstants.MAXIMUM_NPCS);

    /**
     * A {@link ConcurrentLinkedQueue} of {@link Player}s awaiting login.
     */
    private final Queue<Player> logins = new ConcurrentLinkedQueue<>();

    /**
     * A {@link ConcurrentLinkedQueue} of {@link Player}s awaiting logout.
     */
    private final Queue<Player> logouts = new ConcurrentLinkedQueue<>();

    /**
     * The {@link RegionManager} that manages region caching.
     */
    private final RegionManager regions = new RegionManager();

    /**
     * Queues {@code player} to be logged in on the next game loop.
     * 
     * @param player The {@link Player} to be logged in.
     */
    public void queueLogin(Player player) {
        GameSession session = player.getSession();

        if (session.getState() == SessionState.LOGGING_IN && !logins.contains(player)) {
            session.setState(SessionState.LOGOUT_QUEUE);
            logins.add(player);
        }
    }

    /**
     * Dequeues the {@link Queue} of {@link Player}s awaiting login.
     */
    public void dequeueLogins() {
        for (int amount = 0; amount < DEQUEUE_LIMIT; amount++) {
            Player player = logins.poll();
            if (player == null) {
                break;
            }
            players.add(player);
        }
    }

    /**
     * Runs one iteration of the main game loop.
     */
    public void runGameLoop() {

    }

    /**
     * Queues {@code player} to be logged out on the next game loop.
     * 
     * @param player The {@link Player} to be logged out.
     */
    public void queueLogout(Player player) {
        GameSession session = player.getSession();

        if (session.getState() == SessionState.LOGGED_IN && !logouts.contains(player)) {
            session.dispose();
            session.setState(SessionState.LOGOUT_QUEUE);
            logouts.add(player);
        }
    }

    /**
     * Dequeues the {@link Queue} of {@link Player}s awaiting logout.
     */
    public void dequeueLogouts() {
        for (int amount = 0; amount < DEQUEUE_LIMIT; amount++) {
            Player player = logouts.poll();
            if (player == null) {
                break;
            }
            players.remove(player);
        }
    }

    /**
     * Retrieves a {@link Player} instance by its {@code username}.
     * 
     * @param username The username hash of the {@code Player}.
     * @return The {@code Player} instance wrapped in an {@link Optional}, or an
     *         empty {@code Optional} if no {@code Player} was found.
     */
    public Optional<Player> getPlayer(long username) {
        return players.findFirst(it -> it.getUsernameHash() == username);
    }

    /**
     * Retrieves a {@link Player} instance by its {@code username}.
     * 
     * @param username The username of the {@code Player}.
     * @return The {@code Player} instance wrapped in an {@link Optional}, or an
     *         empty {@code Optional} if no {@code Player} was found.
     */
    public Optional<Player> getPlayer(String username) {
        return getPlayer(StringUtils.encodeToBase37(username));
    }

    /**
     * @return The {@link RegionManager} instance.
     */
    public RegionManager getRegions() {
        return regions;
    }

    /**
     * @return The list of {@link Player}s in the world.
     */
    public MobileEntityList<Player> getPlayers() {
        return players;
    }

    /**
     * @return The list of {@link Npc}s in the world.
     */
    public MobileEntityList<Npc> getNpcs() {
        return npcs;
    }
}
