package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.region.RegionManager;
import io.luna.game.task.Task;
import io.luna.game.task.TaskManager;
import io.luna.net.msg.out.NpcUpdateMessageWriter;
import io.luna.net.msg.out.PlayerUpdateMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

import static io.luna.util.ThreadUtils.getCpuAmount;
import static io.luna.util.ThreadUtils.nameThreadFactory;
import static io.luna.util.ThreadUtils.newFixedThreadPool;

/**
 * A model that performs world processing and synchronization for mobs.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    /**
     * A model that sends {@link Player} and {@link Npc} synchronization packets.
     */
    private final class PlayerSynchronizationTask implements Runnable {

        /**
         * The player.
         */
        private final Player player;

        /**
         * Creates a new {@link PlayerSynchronizationTask}.
         *
         * @param player The player.
         */
        private PlayerSynchronizationTask(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            synchronized (player) {
                try {
                    player.queue(new NpcUpdateMessageWriter());
                    player.queue(new PlayerUpdateMessageWriter());
                } catch (Exception e) {
                    LOGGER.warn(player + " could not complete synchronization.", e);
                    player.logout();
                } finally {
                    barrier.arriveAndDeregister();
                }
            }
        }
    }

    /**
     * The asynchronous logger.
     */
    private final Logger LOGGER = LogManager.getLogger();

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
     * A synchronization barrier.
     */
    private final Phaser barrier = new Phaser(1);

    /**
     * A thread pool for parallel updating.
     */
    private final ExecutorService service =
            newFixedThreadPool(nameThreadFactory("WorldSynchronizationThread"), getCpuAmount());

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
     *
     * @param task The task to schedule.
     */
    public void schedule(Task task) {
        tasks.schedule(task);
    }

    /**
     * Queues {@code player} for login on the next tick.
     *
     * @param player The player to queue.
     */
    public void queueLogin(Player player) {
        if (player.getState() == EntityState.NEW && !logins.contains(player)) {
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
     *
     * @param player The player to queue.
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
            playerList.remove(player);
        }
    }

    /**
     * Runs one iteration of the main game loop. This method should <strong>never</strong> be called
     * by anything other than the {@link GameService}.
     */
    public void loop() {

        // Handle logins and logouts.
        dequeueLogins();
        dequeueLogouts();

        // Handle world synchronization.
        preSynchronize();
        synchronize();
        postSynchronize();

        // Process all tasks.
        tasks.runTaskIteration();
    }

    /**
     * Pre-synchronization part of the game loop, process all tick-dependant player logic.
     */
    private void preSynchronize() {
        for (Player player : playerList) {
            try {
                player.getWalkingQueue().process();
                player.sendRegionUpdate();
                player.getSession().dequeueIncomingPackets();
            } catch (Exception e) {
                player.logout();
                LOGGER.warn(player + " could not complete pre-synchronization.", e);
            }
        }

        for (Npc npc : npcList) {
            try {
                npc.getWalkingQueue().process();
            } catch (Exception e) {
                npcList.remove(npc);
                LOGGER.warn(npc + " could not complete pre-synchronization.", e);
            }
        }
    }

    /**
     * Synchronization part of the game loop, apply the update procedure in parallel.
     */
    private void synchronize() {
        barrier.bulkRegister(playerList.size());
        for (Player player : playerList) {
            service.execute(new PlayerSynchronizationTask(player));
        }
        barrier.arriveAndAwaitAdvance();
    }

    /**
     * Post-synchronization part of the game loop, send all queued network data and reset
     * variables.
     */
    private void postSynchronize() {
        for (Player player : playerList) {
            try {
                player.getSession().flush();
                player.resetFlags();
                player.setCachedBlock(null);
            } catch (Exception e) {
                player.logout();
                LOGGER.warn(player + " could not complete post-synchronization.", e);
            }
        }

        for (Npc npc : npcList) {
            try {
                npc.resetFlags();
            } catch (Exception e) {
                npcList.remove(npc);
                LOGGER.warn(npc + " could not complete post-synchronization.", e);
            }
        }
    }

    /**
     * Retrieves a player by their username hash. Faster than {@link World#getPlayer(String)}
     *
     * @param username The username hash.
     * @return The player, or no player.
     */
    public Optional<Player> getPlayer(long username) {
        return playerList.findFirst(player -> player.getUsernameHash() == username);
    }

    /**
     * Retrieves a player by their username.
     *
     * @param username The username.
     * @return The player, or no player.
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
