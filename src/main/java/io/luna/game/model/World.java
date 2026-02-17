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
 * A model that performs world processing and synchronization for mobs.
 *
 * @author lare96
 */
public final class World {


    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    private static final String UPDATING_THREADS_NAME = "PlayerUpdatingThread";

    public static boolean isUpdatingThread() {
        return Objects.equals(Thread.currentThread().getName(), UPDATING_THREADS_NAME);
    }

    /**
     * A model that sends {@link Player} and {@link Npc} synchronization packets.
     */
    private final class PlayerSynchronizationTask implements Runnable {

        /**
         * The player.
         */
        private final Player player;
        private final Collection<Player> localPlayers;
        private final Collection<Npc> localNpcs;


        /**
         * Creates a new {@link PlayerSynchronizationTask}.
         *
         * @param player The player.
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
                    synchronizer.arriveAndDeregister();
                }
            }
        }
    }

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * A list of active players. Default according to OSRS is 2000, but his can be changed to be effectively limitless
     * (Integer.MAX_VALUE).
     */
    private final MobList<Player> playerList = new MobList<>(this, 2000);

    /**
     * A list of active npcs.
     */
    private final MobList<Npc> npcList = new MobList<>(this, 16_384);

    /**
     * A list of active bots.
     */
    private final BotRepository botRepository;

    /**
     * The bot manager.
     */
    private final BotManager botManager;

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
    private final PersistenceService persistenceService;

    /**
     * The serializer manager.
     */
    private final GameSerializerManager serializerManager = new GameSerializerManager();

    /**
     * The chunk manager.
     */
    private final ChunkManager chunks = new ChunkManager(this);

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
     * A synchronization barrier.
     */
    private final Phaser synchronizer = new Phaser(1);

    /**
     * A thread pool for parallel updating.
     */
    private final ExecutorService updatePool;

    /**
     * The current tick.
     */
    private final AtomicLong currentTick = new AtomicLong();

    /**
     * The map of online players. Can be accessed safely from any thread.
     */
    private final ConcurrentMap<String, Player> playerMap;

    /**
     * The collision map.
     */
    private final CollisionManager collisionManager;

    /**
     * The connection pool.
     */
    private final SqlConnectionPool connectionPool;

    /**
     * Creates a new {@link World}.
     *
     * @param context The context instance
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
     * Starts any miscellaneous world services. This method is executed on the game thread.
     */
    public void start() {
        items.startExpirationTask();
        collisionManager.build(false);
        botManager.load();
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
     * than the {@link GameService}. This function and the functions invoked within follow a very specific order and
     * should not be changed.
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

        chunks.resetUpdatedChunks();
        botManager.getInjectorManager().clearEvents();
        collisionManager.handleSnapshots();

        // Increment tick counter.
        currentTick.incrementAndGet();
    }

    /**
     * Pre-synchronization part of the game loop, process all tick-dependant player logic.
     */
    private void preSynchronize() {

        // First handle all client input from players.
        for (Player player : playerList) {
            if (player.getClient().isPendingLogout()) {
                // No input to handle, client should already appear logged out here.
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

        /* Finally, pre-process player walking and action queues. Bot 'input'
            and brain processing is also handled here. */
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
     * Synchronization part of the game loop, apply the update procedure in parallel.
     */
    private void synchronize() {
        // Build update block data.
        for (Player player : playerList) {
            player.buildBlockData();
        }
        for (Npc npc : npcList) {
            npc.buildBlockData();
        }

        // Prepare synchronizer for parallel updating.
        synchronizer.bulkRegister(playerList.size());
        for (Player player : playerList) {
            if (player.getClient().isPendingLogout() || player.isBot()) {
                // No point of sending updates to a client that can't see entities.
                synchronizer.arriveAndDeregister();
                continue;
            }
            /*
                Handle region changes before player updating to ensure no other packets
                related to it are sent. Queued data will be sent after updating
                completes, within the synchronization task.
            */
            player.updateLocalView(player.getPosition());

            // Prepare local mobs for updating and encode them using our thread pool.
            Collection<Player> localPlayers = chunks.findUpdateMobs(player, Player.class);
            Collection<Npc> localNpcs = chunks.findUpdateMobs(player, Npc.class);
            updatePool.execute(new PlayerSynchronizationTask(player, localPlayers, localNpcs));
        }
        synchronizer.arriveAndAwaitAdvance();
    }

    /**
     * Post-synchronization part of the game loop, reset update flags.
     */
    private void postSynchronize() {

        // Reset data related to player and NPC updating.
        for (Npc npc : npcList) {
            try {
                npc.resetFlags();
                npc.clearCachedBlock();
            } catch (Exception e) {
                npcList.remove(npc);
                logger.warn("{} could not complete post-synchronization.", npc, e);
            }
        }
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
        return Optional.ofNullable(playerMap.get(username));
    }

    /**
     * Asynchronously saves all players using the {@link PersistenceService}.
     *
     * @return The result of the mass save.
     */
    public CompletableFuture<Void> saveAll() {
        return persistenceService.saveAll();
    }

    // thread safe boolean to determeine if world is fulll
    public boolean isFull() {
        return playerMap.size() >= playerList.capacity() - 1;
    }


    /**
     * Finds {@code type} entities matching {@code cond} within {@code distance} to {@code base}.
     * The entities will be stored in a set generated by {@code setFunc}.
     *
     * @param base The base position to find entities around.
     * @param type The type of entity to search for.
     * @param setFunc Generates the set that the entities will be stored in.
     * @param cond Filters the entities that will be found.
     * @param distance The distance to check for.
     * @param <T> The type of entity to find.
     * @return The set of entities.
     */
    public <V extends Entity, C extends Collection<V>> C find(
            Position base, Class<V> type, Supplier<C> out,
            Predicate<? super V> filter, int distance) {
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
     * Finds {@code type} entities viewable from {@code position}.
     *
     * @param position The position.
     * @param type The type of entity to find.
     * @param <T> The type of entity to find.
     * @return The set of entities.
     */
    public <T extends Entity> HashSet<T> findViewable(Position position, Class<T> type) {
        return find(position, type, HashSet::new, entity ->
                entity.isWithinDistance(position, Position.VIEWING_DISTANCE), Position.VIEWING_DISTANCE);
    }

    /**
     * Finds {@code type} entities matching {@code cond} within {@code distance} to {@code base}.
     * The entities will be stored in a set generated by {@code setFunc}.
     *
     * @param base The base position to find entities around.
     * @param type The type of entity to search for.
     * @param <T> The type of entity to find.
     * @return The set of entities.
     */
    public <T extends Entity> HashSet<T> findOnTile(Position base, Class<T> type) {
        return find(base, type, HashSet::new, it -> it.getPosition().equals(base), 1);
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
     * @return A list of active npcs.
     */
    public MobList<Npc> getNpcs() {
        return npcList;
    }

    /**
     * @return A list of active bots.
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

    /**
     * @return The collision map.
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
     * @return The connection pool.
     */
    public SqlConnectionPool getConnectionPool() {
        return connectionPool;
    }
}
