package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.mobile.MobList;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.region.RegionManager;
import io.luna.game.task.Task;
import io.luna.game.task.TaskManager;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A model that manages entities.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * A list of active players.
     */
    private final MobList<Player> playerList = new MobList<>(2048);

    /**
     * A list of active npcs.
     */
    private final MobList<Npc> npcList = new MobList<>(16384);

    /**
     * A queue of players awaiting login.
     */
    private final Queue<Player> logins = new ConcurrentLinkedQueue<>();

    /**
     * A queue of players awaiting logout.
     */
    private final Queue<Player> logouts = new ConcurrentLinkedQueue<>();

    /**
     * The region manager.
     */
    private final RegionManager regions = new RegionManager();

    /**
     * The task manager.
     */
    private final TaskManager tasks = new TaskManager();

    /**
     * The world synchronizer.
     */
    private final WorldSynchronizer synchronizer = new WorldSynchronizer(this);

    /**
     * Creates a new {@link World}.
     *
     * @param context The context instance
     */
    public World(LunaContext context) {
        this.context = context;
    }

    /**
     * Schedules {@code task} to run sometime in the future.
     */
    public void schedule(Task task) {
        tasks.schedule(task);
    }

    /**
     * Queues {@code player} for login on the next tick.
     */
    public void queueLogin(Player player) {
        if (player.getState() == EntityState.IDLE && !logins.contains(player)) {
            logins.add(player);
        }
    }

    /**
     * Adds players awaiting login to the world.
     */
    public void dequeueLogins() {
        for (int amount = 0; amount < EntityConstants.LOGIN_THRESHOLD; amount++) {
            Player player = logins.poll();
            if (player == null) {
                break;
            }
            playerList.add(player);
        }
    }

    /**
     * Queues {@code player} for logout on the next tick.
     */
    public void queueLogout(Player player) {
        if (player.getState() == EntityState.ACTIVE && !logouts.contains(player)) {
            logouts.add(player);
        }
    }

    /**
     * Removes players awaiting logout from the world.
     */
    public void dequeueLogouts() {
        for (int amount = 0; amount < EntityConstants.LOGOUT_THRESHOLD; amount++) {
            Player player = logouts.poll();
            if (player == null) {
                break;
            }
            /* TODO: Anti x-logging. */
            playerList.remove(player);
        }
    }

    /**
     * Runs task processing and mob synchronization.
     */
    public void runGameLoop() {
        tasks.runTaskIteration();

        synchronizer.preSynchronize();
        synchronizer.synchronize();
        synchronizer.postSynchronize();
    }

    /**
     * Retrieves a player by their username hash. Faster than {@code getPlayer(String)}.
     */
    public Optional<Player> getPlayer(long username) {
        return playerList.findFirst(player -> player.getUsernameHash() == username);
    }

    /**
     * Retrieves a player by their username.
     */
    public Optional<Player> getPlayer(String username) {
        return playerList.findFirst(player -> player.getUsername().equals(username));
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The region manager.
     */
    public RegionManager getRegions() {
        return regions;
    }

    /**
     * @return The task manager
     */
    public TaskManager getTasks() {
        return tasks;
    }

    /**
     * @return A list of active players.
     */
    public MobList<Player> getPlayers() {
        return playerList;
    }

    /**
     * @return A list of active npcs.
     */
    public MobList<Npc> getNpcs() {
        return npcList;
    }
}
