package io.luna.game.model;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.item.GroundItemList;
import io.luna.game.model.item.shop.ShopManager;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.persistence.PlayerPersistence;
import io.luna.game.model.object.GameObjectList;
import io.luna.game.task.Task;
import io.luna.game.task.TaskManager;
import io.luna.net.codec.login.LoginResponse;
import io.luna.net.msg.out.NpcUpdateMessageWriter;
import io.luna.net.msg.out.PlayerUpdateMessageWriter;
import io.luna.util.ThreadUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

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
                    player.queue(new PlayerUpdateMessageWriter());
                    player.queue(new NpcUpdateMessageWriter());
                    player.getClient().flush();
                } catch (Exception e) {
                    LOGGER.warn(new ParameterizedMessage("{} could not complete synchronization.", player, e));
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
     * A list of active npc.
     */
    private final MobList<Npc> npcList = new MobList<>(16384);

    /**
     * A queue of login requests.
     */
    private final Queue<Player> logins = new ConcurrentLinkedQueue<>();

    /**
     * A queue of players awaiting logout.
     */
    private final Queue<Player> logouts = new ConcurrentLinkedQueue<>();

    /**
     * The chunk manager.
     */
    private final ChunkManager chunks = new ChunkManager();

    /**
     * The task manager.
     */
    private final TaskManager tasks = new TaskManager();

    /**
     * The shop manager.
     */
    private final ShopManager shops = new ShopManager();

    /**
     * The game object manager.
     */
    private final GameObjectList objects = new GameObjectList(this);

    /**
     * The ground item manager.
     */
    private final GroundItemList items = new GroundItemList(this);

    /**
     * The area manager.
     */
    private final AreaManager areas = new AreaManager(this);

    /**
     * The player persistence manager.
     */
    private final PlayerPersistence persistence = new PlayerPersistence();

    /**
     * A concurrent map of online players, used to check online players from non-game threads.
     */
    private final Map<Long, Player> playerMap = new ConcurrentHashMap<>();

    /**
     * A synchronization barrier.
     */
    private final Phaser barrier = new Phaser(1);

    /**
     * A thread pool for parallel updating.
     */
    private final ExecutorService service;

    /**
     * The current tick.
     */
    private final AtomicLong currentTick = new AtomicLong();

    /**
     * Creates a new {@link World}.
     *
     * @param context The context instance
     */
    public World(LunaContext context) {
        this.context = context;
    }

    {
        // Initialize synchronization thread pool.
        ThreadFactory tf = new ThreadFactoryBuilder().
                setNameFormat("WorldSynchronizationThread").build();
        service = Executors.newFixedThreadPool(ThreadUtils.cpuCount(), tf);
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

            try {
                playerList.add(player);
            } catch (Exception e) {
                LOGGER.catching(e);
            }
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
     * Runs one iteration of the main game loop. This method should <strong>never</strong> be called by anything other
     * than the {@link GameService}.
     */
    public void loop() {
        // Handle logins.
        dequeueLogins();

        // Process all tasks.
        tasks.runTaskIteration();

        // Handle world synchronization.
        preSynchronize();
        synchronize();
        postSynchronize();

        // Handle logouts.
        dequeueLogouts();

        // Increment tick counter.
        currentTick.incrementAndGet();
    }

    /**
     * Pre-synchronization part of the game loop, process all tick-dependant player logic.
     */
    private void preSynchronize() {
        for (Player player : playerList) {
            try {
                player.getClient().handleDecodedMessages();
                player.getWalking().process();
                player.getClient().flush();
            } catch (Exception e) {
                player.logout();
                LOGGER.warn(new ParameterizedMessage("{} could not complete pre-synchronization.", player, e));
            }
        }

        for (Npc npc : npcList) {
            try {
                npc.getWalking().process();
            } catch (Exception e) {
                npcList.remove(npc);
                LOGGER.warn(new ParameterizedMessage("{} could not complete pre-synchronization.", npc, e));
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
     * Post-synchronization part of the game loop, reset variables.
     */
    private void postSynchronize() {
        for (Player player : playerList) {
            try {
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
     * Asynchronously saves persistent data for {@code player}.
     *
     * @param player The player.
     * @return A future returning {@code true} if the save was successful.
     */
    public Future<Boolean> savePlayer(Player player) {
        return persistence.save(player);
    }

    /**
     * Loads persistent data for {@code player}.
     *
     * @param player The player.
     * @return A future returning the login response.
     */
    public ListenableFuture<LoginResponse> loadPlayer(Player player) {
        return persistence.load(player);
    }

    /**
     * Retrieves a player by their username hash. Much faster than {@link World#getPlayer(String)}.
     *
     * @param username The username hash.
     * @return The player, or no player.
     */
    public Optional<Player> getPlayer(long username) {
        return Optional.ofNullable(playerMap.get(username));
    }

    /**
     * Retrieves a player by their username.
     *
     * @param username The username.
     * @return The player, or no player.
     */
    public Optional<Player> getPlayer(String username) {
        return playerList.findFirst(player -> player.getUsername().equalsIgnoreCase(username));
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The chunk manager.
     */
    public ChunkManager getChunks() {
        return chunks;
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
     * @return A list of active npc.
     */
    public MobList<Npc> getNpcs() {
        return npcList;
    }

    /**
     * @return The game object manager.
     */
    public GameObjectList getObjects() {
        return objects;
    }

    /**
     * @return The ground item manager.
     */
    public GroundItemList getItems() {
        return items;
    }

    /**
     * @return The area manager.
     */
    public AreaManager getAreas() {
        return areas;
    }

    /**
     * @return The shop manager.
     */
    public ShopManager getShops() {
        return shops;
    }

    /**
     * @return The player persistence manager.
     */
    public PlayerPersistence getPersistence() {
        return persistence;
    }

    /**
     * @return A concurrent map of online players. <strong>Warning:</strong> Do not modify this collection
     * unless you understand the implications. It must be in-sync with {@link #playerList}.
     */
    public Map<Long, Player> getPlayerMap() {
        return playerMap;
    }

    /**
     * @return The current tick.
     */
    public long getCurrentTick() {
        return currentTick.get();
    }
}
