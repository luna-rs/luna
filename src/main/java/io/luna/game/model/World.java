package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.LoginService;
import io.luna.game.LogoutService;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.item.GroundItemList;
import io.luna.game.model.item.shop.ShopManager;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.BotManager;
import io.luna.game.model.mob.bot.BotRepository;
import io.luna.game.model.object.GameObjectList;
import io.luna.game.persistence.GameSerializerManager;
import io.luna.game.persistence.PersistenceService;
import io.luna.game.task.Task;
import io.luna.game.task.TaskManager;
import io.luna.net.msg.out.NpcUpdateMessageWriter;
import io.luna.net.msg.out.PlayerUpdateMessageWriter;
import io.luna.util.ExecutorUtils;
import io.luna.util.SqlConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The primary world state container and tick processor.
 * <p>
 * {@link World} owns and coordinates:
 * <ul>
 *     <li>active players / NPCs ({@link MobList})</li>
 *     <li>login + logout staging ({@link LoginService}, {@link LogoutService})</li>
 *     <li>tick tasks ({@link TaskManager})</li>
 *     <li>chunk/entity spatial indexing ({@link ChunkManager})</li>
 *     <li>game objects and ground items ({@link GameObjectList}, {@link GroundItemList})</li>
 *     <li>collision ({@link CollisionManager})</li>
 *     <li>persistence ({@link PersistenceService})</li>
 *     <li>bots ({@link BotRepository}, {@link BotManager})</li>
 * </ul>
 * <h2>Threading model</h2>
 * <ul>
 *     <li>The main game loop ({@link #process()}) runs on the game thread (owned by {@link GameService}).</li>
 *     <li>Player/NPC logic, movement, actions, adding/removing mobs should occur on the game thread.</li>
 *     <li>Synchronization packet encoding is parallelized using {@link #updatePool}.</li>
 *     <li>{@link #playerMap} is a thread-safe index intended for lookups from any thread.</li>
 * </ul>
 *
 * @author lare96
 */
public final class World {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Base name used for the update worker threads.
     */
    private static final String UPDATING_THREADS_NAME = "PlayerUpdatingThread";

    /**
     * Checks whether the current thread appears to be one of the update worker threads.
     *
     * @return {@code true} if the current thread name matches {@link #UPDATING_THREADS_NAME}.
     */
    public static boolean isUpdatingThread() {
        return Objects.equals(Thread.currentThread().getName(), UPDATING_THREADS_NAME);
    }

    /**
     * A runnable that encodes and queues per-player update packets.
     * <p>
     * This is executed on {@link #updatePool}. It is responsible for:
     * <ul>
     *     <li>encoding player updates ({@link PlayerUpdateMessageWriter})</li>
     *     <li>encoding NPC updates ({@link NpcUpdateMessageWriter})</li>
     *     <li>ensuring the {@link #synchronizer} barrier is released via {@code arriveAndDeregister()}</li>
     * </ul>
     */
    private final class PlayerSynchronizationTask implements Runnable {

        /**
         * The player being synchronized.
         */
        private final Player player;

        /**
         * Local players currently in-view for {@link #player}.
         */
        private final Collection<Player> localPlayers;

        /**
         * Local NPCs currently in-view for {@link #player}.
         */
        private final Collection<Npc> localNpcs;

        /**
         * Creates a new {@link PlayerSynchronizationTask}.
         *
         * @param player The player.
         * @param localPlayers The players local to {@code player}.
         * @param localNpcs The NPCs local to {@code player}.
         */
        private PlayerSynchronizationTask(Player player, Collection<Player> localPlayers, Collection<Npc> localNpcs) {
            this.player = player;
            this.localPlayers = localPlayers;
            this.localNpcs = localNpcs;
        }

        @Override
        public void run() {
            synchronized (player) {
                try {
                    player.queue(new PlayerUpdateMessageWriter(localPlayers));
                    player.queue(new NpcUpdateMessageWriter(localNpcs));
                } catch (Exception e) {
                    logger.warn("{} could not complete synchronization.", player, e);
                    player.forceLogout();
                } finally {
                    // Always release the barrier party for this player.
                    synchronizer.arriveAndDeregister();
                }
            }
        }
    }

    /**
     * The runtime context (configuration, cache, plugin system, etc).
     */
    private final LunaContext context;

    /**
     * Active players currently in the world.
     * <p>
     * Capacity controls the maximum simultaneous players this world can hold. This list should be mutated
     * on the game thread.
     */
    private final MobList<Player> playerList = new MobList<>(this, 2000);

    /**
     * Active NPCs currently in the world.
     * <p>
     * This list should be mutated on the game thread.
     */
    private final MobList<Npc> npcList = new MobList<>(this, 16_384);

    /**
     * Repository of active bots (players flagged as bots).
     */
    private final BotRepository botRepository;

    /**
     * Bot definition/behavior manager.
     */
    private final BotManager botManager;

    /**
     * Login flow orchestrator.
     */
    private final LoginService loginService = new LoginService(this);

    /**
     * Logout flow orchestrator.
     */
    private final LogoutService logoutService = new LogoutService(this);

    /**
     * Persistence subsystem for saving/loading player data.
     */
    private final PersistenceService persistenceService;

    /**
     * Serializer registry used by persistence.
     */
    private final GameSerializerManager serializerManager = new GameSerializerManager();

    /**
     * Spatial chunk manager (lookup + view updates).
     */
    private final ChunkManager chunks = new ChunkManager(this);

    /**
     * Scheduled task manager (tick-based tasks).
     */
    private final TaskManager tasks = new TaskManager();

    /**
     * Shop manager.
     */
    private final ShopManager shops = new ShopManager();

    /**
     * Global game object list / manager.
     */
    private final GameObjectList objects = new GameObjectList(this);

    /**
     * Global ground item list / manager.
     */
    private final GroundItemList items = new GroundItemList(this);

    /**
     * Synchronization barrier used by {@link #synchronize()} to block until all per-player update tasks complete.
     * <p>
     * Initialized with 1 party for the game thread.
     */
    private final Phaser synchronizer = new Phaser(1);

    /**
     * Thread pool used to parallelize player synchronization packet encoding.
     */
    private final ExecutorService updatePool;

    /**
     * Monotonic tick counter (increments once per {@link #process()} call).
     */
    private final AtomicLong currentTick = new AtomicLong();

    /**
     * Thread-safe index of online players by username.
     * <p>
     * This is safe to access from any thread. It is intentionally separate from {@link #playerList}.
     */
    private final ConcurrentMap<String, Player> playerMap;

    /**
     * Collision system backing maps and snapshots.
     */
    private final CollisionManager collisionManager;

    /**
     * Database connection pool.
     */
    private final SqlConnectionPool connectionPool;

    /**
     * Creates a new {@link World}.
     *
     * @param context The runtime context instance.
     */
    public World(LunaContext context) {
        this.context = context;

        playerMap = new ConcurrentHashMap<>();
        collisionManager = new CollisionManager(this);
        botRepository = new BotRepository(this);
        persistenceService = new PersistenceService(this);
        botManager = new BotManager();

        // Initialize the connection pool.
        try {
            connectionPool = new SqlConnectionPool.Builder()
                    .poolName("LunaSqlPool")
                    .database("luna_players")
                    .build();
        } catch (Exception e) {
            logger.fatal("Fatal error creating SQL pool!", e);
            throw new RuntimeException(e);
        }

        // Initialize synchronization thread pool.
        updatePool = ExecutorUtils.threadPool(UPDATING_THREADS_NAME);
    }

    /**
     * Starts world subsystems that need to run once after construction.
     * <p>
     * This method is executed on the game thread.
     */
    public void start() {
        items.startExpirationTask();
        collisionManager.build(false);
        botManager.load();
    }

    /**
     * Adds a player to the global online index.
     * <p>
     * This does not add them to {@link #playerList}; that is handled by login flow.
     *
     * @param player The player to index.
     */
    public void addPlayer(Player player) {
        playerMap.put(player.getUsername(), player);
    }

    /**
     * Removes a player from the global online index.
     * <p>
     * This does not remove them from {@link #playerList}; that is handled by logout flow.
     *
     * @param player The player to unindex.
     */
    public void removePlayer(Player player) {
        playerMap.remove(player.getUsername());
    }

    /**
     * Schedules {@code task} to run on a future tick.
     *
     * @param task The task to schedule.
     */
    public void schedule(Task task) {
        tasks.schedule(task);
    }

    /**
     * Runs one iteration of the main game loop.
     * <p>
     * This method should <strong>never</strong> be called by anything other than the {@link GameService}.
     * The ordering of phases is important for correctness:
     * <ol>
     *     <li>finish login + logout staging</li>
     *     <li>run scheduled tasks</li>
     *     <li>pre-synchronization: input + movement + actions</li>
     *     <li>synchronization: build block data + send update packets (parallel)</li>
     *     <li>post-synchronization: reset flags + flush</li>
     *     <li>housekeeping: chunk updates reset, bot event cleanup, collision snapshots</li>
     *     <li>tick increment</li>
     * </ol>
     */
    public void process() {

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

        // Housekeeping that depends on synchronization having completed.
        chunks.resetUpdatedChunks();
        botManager.getInjectorManager().clearEvents();
        collisionManager.handleSnapshots();

        // Increment tick counter.
        currentTick.incrementAndGet();
    }

    /**
     * Pre-synchronization phase: consume client input and run tick-dependent logic.
     * <p>
     * Responsibilities:
     * <ul>
     *     <li>decode and handle incoming client messages (for non-logging-out players)</li>
     *     <li>process NPC walking and actions (skipping locked NPCs)</li>
     *     <li>process player controllers, walking, actions, and bot "brain" logic</li>
     * </ul>
     */
    private void preSynchronize() {

        // First handle all client input from players.
        for (Player player : playerList) {
            if (player.getClient().isPendingLogout()) {
                // No input to handle; the client should already appear logged out here.
                continue;
            }
            player.getClient().handleDecodedMessages();
        }

        // Then, pre-process NPC walking and action queues.
        for (Npc npc : npcList) {
            try {
                if (npc.isLocked()) {
                    // Skip pre-processing for locked NPCs.
                    continue;
                }
                npc.getWalking().process();
                npc.getActions().process();
            } catch (Exception e) {
                npcList.remove(npc);
                logger.warn("{} could not complete pre-synchronization.", npc, e);
            }
        }

        /*
         * Finally, pre-process player walking and action queues.
         * Bot 'input' and brain processing is also handled here.
         */
        for (Player player : playerList) {
            try {
                player.getControllers().process();
                player.getWalking().process();
                player.getActions().process();

                if (player.isBot()) {
                    player.asBot().process();
                }
            } catch (Exception e) {
                player.logout();
                logger.warn("{} could not complete pre-synchronization.", player, e);
            }
        }
    }

    /**
     * Synchronization phase: build update blocks and send synchronization packets.
     * <p>
     * This phase parallelizes per-player packet encoding using {@link #updatePool}. It uses {@link #synchronizer}
     * to ensure the game thread waits until all player tasks have completed.
     */
    private void synchronize() {

        // Build update block data (done on the game thread).
        for (Player player : playerList) {
            player.buildBlockData();
        }
        for (Npc npc : npcList) {
            npc.buildBlockData();
        }

        // Prepare synchronizer for parallel updating.
        synchronizer.bulkRegister(playerList.size());
        for (Player player : playerList) {
            try {
                if (player.getClient().isPendingLogout() || player.isBot()) {
                    // No point sending updates to a client that is leaving, or to bots (no real client view).
                    synchronizer.arriveAndDeregister();
                    continue;
                }

                /*
                 * Handle region changes before player updating to ensure no other packets related to it are sent.
                 * Queued data will be sent after updating completes, within the synchronization task.
                 */
                player.updateLocalView(player.getPosition());

                // Prepare local mobs for updating and encode them using our thread pool.
                Collection<Player> localPlayers = chunks.findUpdateMobs(player, Player.class);
                Collection<Npc> localNpcs = chunks.findUpdateMobs(player, Npc.class);

                updatePool.execute(new PlayerSynchronizationTask(player, localPlayers, localNpcs));
            } catch (Exception e) {
                logger.error("Error occurred while preparing player update request.", e);
                synchronizer.arriveAndDeregister();
            }
        }

        // Wait for all registered parties to finish.
        synchronizer.arriveAndAwaitAdvance();
    }

    /**
     * Post-synchronization phase: reset per-tick update state and flush outbound buffers.
     * <p>
     * Responsibilities:
     * <ul>
     *     <li>reset update flags</li>
     *     <li>clear cached update block data</li>
     *     <li>flush player client buffers</li>
     * </ul>
     */
    private void postSynchronize() {

        // Reset data related to NPC updating.
        for (Npc npc : npcList) {
            try {
                npc.resetFlags();
                npc.clearCachedBlock();
            } catch (Exception e) {
                npcList.remove(npc);
                logger.warn("{} could not complete post-synchronization.", npc, e);
            }
        }

        // Reset data related to player updating.
        for (Player player : playerList) {
            try {
                player.resetFlags();
                player.clearCachedBlock();
                player.getClient().flush();
            } catch (Exception e) {
                player.logout();
                logger.warn("{} could not complete post-synchronization.", player, e);
            }
        }
    }

    /**
     * Retrieves a player by username hash.
     * <p>
     * This scans {@link #playerList} and is intended for in-world logic where you already have hashes.
     *
     * @param username The username hash.
     * @return The player, if online.
     */
    public Optional<Player> getPlayer(long username) {
        return playerList.findFirst(player -> player.getUsernameHash() == username);
    }

    /**
     * Retrieves an online player by username.
     * <p>
     * This uses {@link #playerMap} and is safe to call from any thread.
     *
     * @param username The username.
     * @return The player, if online.
     */
    public Optional<Player> getPlayer(String username) {
        return Optional.ofNullable(playerMap.get(username));
    }

    /**
     * Asynchronously saves all players via {@link PersistenceService}.
     *
     * @return A future that completes when all save tasks complete.
     */
    public CompletableFuture<Void> saveAll() {
        return persistenceService.saveAll();
    }

    /**
     * Checks whether this world is at (or above) capacity.
     * <p>
     * This uses {@link #playerMap} size, which may include players in transitional states depending on your
     * login/logout flow. If you ever notice off-by-one behavior, consider basing this on {@link #playerList}
     * occupancy instead.
     *
     * @return {@code true} if the world is considered full.
     */
    public boolean isFull() {
        return playerMap.size() >= playerList.capacity() - 1;
    }

    /**
     * Finds entities near {@code base} within {@code distance} tiles.
     * <p>
     * This is a chunk-based spatial query:
     * <ol>
     *     <li>Computes a chunk radius large enough to cover {@code distance}.</li>
     *     <li>Loads each chunk repository in that radius.</li>
     *     <li>Scans entities of the requested type and applies {@code filter}.</li>
     *     <li>Adds matches to a collection produced by {@code out}.</li>
     * </ol>
     * <p>
     * Complexity is roughly {@code O(chunks_scanned + entities_scanned)}. Keep {@code distance} small in hot paths.
     *
     * @param base The base position to search around.
     * @param type The entity class to search for.
     * @param out A supplier for the output collection instance.
     * @param filter A predicate to filter matches.
     * @param distance The search radius in tiles (must be {@code >= 1}).
     * @param <V> The entity type.
     * @param <C> The output collection type.
     * @return The output collection containing all matching entities.
     * @throws IllegalArgumentException if {@code distance < 1}.
     */
    public <V extends Entity, C extends Collection<V>> C find(
            Position base,
            Class<V> type,
            Supplier<C> out,
            Predicate<? super V> filter,
            int distance) {

        checkArgument(distance > 0, "[distance] cannot be below 1.");

        int radius = Math.floorDiv(distance, Chunk.SIZE) + 2;
        C found = out.get();

        EntityType entityType = EntityType.CLASS_TO_TYPE.get(type);
        Chunk chunk = base.getChunk();

        for (int x = -radius; x < radius; x++) {
            for (int y = -radius; y < radius; y++) {
                ChunkRepository repository = chunks.load(chunk.translate(x, y));
                Set<V> entities = repository.getAll(entityType);

                for (V entity : entities) {
                    if (filter.test(entity)) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Finds entities of {@code type} that are within viewing distance of {@code position}.
     *
     * @param position The base position.
     * @param type The entity class to find.
     * @param <T> The entity type.
     * @return A set of entities within {@link Position#VIEWING_DISTANCE}.
     */
    public <T extends Entity> HashSet<T> findViewable(Position position, Class<T> type) {
        return find(position, type, HashSet::new,
                entity -> entity.isWithinDistance(position, Position.VIEWING_DISTANCE),
                Position.VIEWING_DISTANCE);
    }

    /**
     * Finds all entities of {@code type} whose position exactly equals {@code base}.
     * <p>
     * This uses {@link #find(Position, Class, Supplier, Predicate, int)} with {@code distance=1}. That still scans
     * a few surrounding chunks due to radius math. If this becomes hot, consider adding a dedicated fast path:
     * load only {@code base.getChunk()} and scan its entity sets.
     *
     * @param base The tile to search.
     * @param type The entity class to find.
     * @param <T> The entity type.
     * @return A set of entities on the exact tile.
     */
    public <T extends Entity> HashSet<T> findOnTile(Position base, Class<T> type) {
        return find(base, type, HashSet::new, it -> it.getPosition().equals(base), 1);
    }

    /**
     * @return The runtime context.
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
     * @return The task manager.
     */
    public TaskManager getTasks() {
        return tasks;
    }

    /**
     * @return The active player list (game-thread owned).
     */
    public MobList<Player> getPlayers() {
        return playerList;
    }

    /**
     * @return The active NPC list (game-thread owned).
     */
    public MobList<Npc> getNpcs() {
        return npcList;
    }

    /**
     * @return The bot repository.
     */
    public BotRepository getBots() {
        return botRepository;
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
     * @return The shop manager.
     */
    public ShopManager getShops() {
        return shops;
    }

    /**
     * @return The current world tick (monotonic).
     */
    public long getCurrentTick() {
        return currentTick.get();
    }

    /**
     * Returns the thread-safe online player index.
     * <p>
     * Safe to access from any thread. Treat {@link #playerList} separately (game-thread owned).
     *
     * @return A map of username -> player.
     */
    public Map<String, Player> getPlayerMap() {
        return playerMap;
    }

    /**
     * @return The collision manager.
     */
    public CollisionManager getCollisionManager() {
        return collisionManager;
    }

    /**
     * @return The serializer manager.
     */
    public GameSerializerManager getSerializerManager() {
        return serializerManager;
    }

    /**
     * @return The bot manager.
     */
    public BotManager getBotManager() {
        return botManager;
    }

    /**
     * @return The SQL connection pool.
     */
    public SqlConnectionPool getConnectionPool() {
        return connectionPool;
    }
}
