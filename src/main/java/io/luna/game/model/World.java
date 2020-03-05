package io.luna.game.model;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.item.GroundItemList;
import io.luna.game.model.item.shop.ShopManager;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObjectList;
import io.luna.game.service.GameService;
import io.luna.game.service.LoginService;
import io.luna.game.service.LogoutService;
import io.luna.game.service.PersistenceService;
import io.luna.game.task.Task;
import io.luna.game.task.TaskManager;
import io.luna.net.msg.out.NpcUpdateMessageWriter;
import io.luna.net.msg.out.PlayerUpdateMessageWriter;
import io.luna.util.ThreadUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
                    logger.warn(new ParameterizedMessage("{} could not complete synchronization.", player, e));
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
    private final Logger logger = LogManager.getLogger();

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * A list of active players.
     */
    private final MobList<Player> playerList = new MobList<>(this, 2048);

    /**
     * A list of active npc.
     */
    private final MobList<Npc> npcList = new MobList<>(this, 16384);

    /**
     * The login service.
     */
    private final LoginService loginService = new LoginService(this);

    /**
     * The logout service.
     */
    private final LogoutService logoutService = new LogoutService(this);

    /**
     * The persistence service.
     */
    private final PersistenceService persistenceService = new PersistenceService(this);

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
     * The map of online players. Can be accessed safely from any thread.
     */
    private final ConcurrentMap<String, Player> playerMap;

    /**
     * An immutable view of {@link #playerMap}.
     */
    private final Map<String, Player> immutablePlayerMap;

    /**
     * Creates a new {@link World}.
     *
     * @param context The context instance
     */
    public World(LunaContext context) {
        this.context = context;
        this.playerMap = new ConcurrentHashMap<>();
        this.immutablePlayerMap = Collections.unmodifiableMap(playerMap);

        // Initialize synchronization thread pool.
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("WorldSynchronizationThread").build();
        this.service = Executors.newFixedThreadPool(ThreadUtils.cpuCount(), tf);
    }

    /**
     * Starts any miscellaneous world services. This function is applied on the game thread.
     */
    public void start() {
        items.startExpirationTask();
    }

    /**
     * Adds a player to the backing concurrent map.
     */
    public void addPlayer(Player player) {
        playerMap.put(player.getUsername(), player);
    }

    /**
     * Removes a player from the backing concurrent map.
     */
    public void removePlayer(Player player) {
        playerMap.remove(player.getUsername());
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
     * Runs one iteration of the main game loop. This method should <strong>never</strong> be called by anything other
     * than the {@link GameService}.
     */
    public void loop() {
        // Add pending players that have just logged in.
        loginService.finishRequests();

        // Remove pending players that have just logged out.
        logoutService.finishRequests();

        // Process all tasks.
        tasks.runTaskIteration();

        // Handle world synchronization.
        preSynchronize();
        synchronize();
        postSynchronize();

        // Increment tick counter.
        currentTick.incrementAndGet();
    }

    /**
     * Pre-synchronization part of the game loop, process all tick-dependant player logic.
     */
    private void preSynchronize() {
        for (Player player : playerList) {
            try {
                if (player.getClient().isPendingLogout()) {
                    player.cleanUp();
                    continue;
                }
                player.getClient().handleDecodedMessages(player);
                player.getWalking().process();
                player.getClient().flush();
            } catch (Exception e) {
                player.logout();
                logger.warn(new ParameterizedMessage("{} could not complete pre-synchronization.", player, e));
            }
        }

        for (Npc npc : npcList) {
            try {
                npc.getWalking().process();
            } catch (Exception e) {
                npcList.remove(npc);
                logger.warn(new ParameterizedMessage("{} could not complete pre-synchronization.", npc, e));
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
                logger.warn(player + " could not complete post-synchronization.", e);
            }
        }

        for (Npc npc : npcList) {
            try {
                npc.resetFlags();
            } catch (Exception e) {
                npcList.remove(npc);
                logger.warn(npc + " could not complete post-synchronization.", e);
            }
        }
    }

    /**
     * Retrieves a player by their username hash. Faster than {@link World#getPlayer(String)}.
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
        return playerList.findFirst(player -> player.getUsername().equalsIgnoreCase(username));
    }

    /**
     * Asynchronously saves all players using the {@link PersistenceService}.
     *
     * @return The result of the mass save.
     */
    public ListenableFuture<Void> saveAll() {
        return persistenceService.saveAll();
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The login service.
     */
    public LoginService getLoginService() {
        return loginService;
    }

    /**
     * @return The logout service.
     */
    public LogoutService getLogoutService() {
        return logoutService;
    }

    /**
     * @return The persistence service.
     */
    public PersistenceService getPersistenceService() {
        return persistenceService;
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
     * @return The current tick.
     */
    public long getCurrentTick() {
        return currentTick.get();
    }

    /**
     * @return The map of online players. Can be accessed safely from any thread.
     */
    public Map<String, Player> getPlayerMap() {
        return playerMap;
    }
}
