package io.luna.game.model.chunk;

import io.luna.game.model.*;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ClearChunkMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model that loads new chunks and manages loaded chunks.
 *
 * @author lare96
 */
public final class ChunkManager implements Iterable<ChunkRepository> {

    /**
     * Determines the local player count at which prioritized updating will start.
     */
    public static final int UNSORTED_THRESHOLD = 50;

    /**
     * How many layers of chunks will be loaded when looking for viewable entities.
     */
    public static final int VIEWABLE_RADIUS = 3;

    /**
     * A map of loaded chunks.
     */
    private final Map<Chunk, ChunkRepository> repositories = new ConcurrentHashMap<>(29_278);

    /**
     * A queue of chunks that updates were sent to.
     */
    private final Queue<ChunkRepository> updated = new ArrayDeque<>();

    /**
     * The world instance.
     */
    private final World world;

    /**
     * Creates a new {@link ChunkManager}.
     *
     * @param world The world instance.
     */
    public ChunkManager(World world) {
        this.world = world;
    }

    @Override
    public Spliterator<ChunkRepository> spliterator() {
        return Spliterators.spliterator(repositories.values(), Spliterator.NONNULL);
    }

    @Override
    public Iterator<ChunkRepository> iterator() {
        return repositories.values().iterator();
    }

    /**
     * Loads a {@link ChunkRepository} based on the argued chunk, constructing and loading a new one if needed.
     *
     * @param chunk The chunk to get the repository of.
     * @return The existing or newly loaded chunk.
     */
    public ChunkRepository load(Chunk chunk) {
        return repositories.computeIfAbsent(chunk, key -> new ChunkRepository(world, key));
    }

    /**
     * Loads a {@link ChunkRepository} based on the argued position, constructing and loading a new one if needed.
     *
     * @param position The position to get the repository of.
     * @return The existing or newly loaded chunk.
     */
    public ChunkRepository load(Position position) {
        return load(position.getChunk());
    }

    /**
     * Returns a set of all nearby {@link Mob} types matching {@code type} that need to be updated for {@code player}.
     *
     * @param player The player.
     * @param type   The mob type.
     * @param <T>    The mob type.
     * @return The set of mobs that need to be updated.
     */
    public <T extends Mob> Set<T> findUpdateMobs(Player player, Class<T> type) {
        int count;
        if (type == Player.class) {
            count = player.getLocalPlayers().size();
        } else if (type == Npc.class) {
            count = player.getLocalNpcs().size();
        } else {
            throw new IllegalStateException("Invalid mob type.");
        }
        return find(player.getPosition(), type,
                () -> count > UNSORTED_THRESHOLD ? new TreeSet<>(new ChunkMobComparator(player)) : new HashSet<>(),
                entity -> entity.isViewableFrom(player),
                Position.VIEWING_DISTANCE);
    }

    /**
     * Finds {@code type} entities viewable from {@code position}.
     *
     * @param position The position.
     * @param type     The type of entity to find.
     * @param <T>      The type of entity to find.
     * @return The set of entities.
     */
    public <T extends Entity> Set<T> findViewable(Position position, Class<T> type) {
        return find(position, type, HashSet::new,
                entity -> entity.isWithinDistance(position, Position.VIEWING_DISTANCE), Position.VIEWING_DISTANCE);
    }

    /**
     * Finds a set of surrounding chunks to the chunk located on {@code base}.
     *
     * @param base The base position.
     * @return The set of chunks.
     */
    public Set<ChunkRepository> findViewableChunks(Position base) {
        Chunk chunk = base.getChunk();
        Set<ChunkRepository> viewable = new HashSet<>(16);
        for (int x = -VIEWABLE_RADIUS; x < VIEWABLE_RADIUS; x++) {
            for (int y = -VIEWABLE_RADIUS; y < VIEWABLE_RADIUS; y++) {
                ChunkRepository repository = load(chunk.translate(x, y));
                viewable.add(repository);
            }
        }
        return viewable;
    }

    /**
     * Refreshes {@link StationaryEntity} types within all viewable chunks of {@code player}.
     *
     * @param player      The player.
     * @param oldPosition The old position of the player.
     * @param fullRefresh If a full refresh is being performed.
     */
    public void sendUpdates(Player player, Position oldPosition, boolean fullRefresh) {
        Set<ChunkRepository> oldChunks = findViewableChunks(oldPosition);
        Set<ChunkRepository> newChunks = findViewableChunks(player.getPosition());
        Set<ChunkRepository> viewableOldChunks = new HashSet<>();

        if (!fullRefresh) {
            // Don't need a full refresh, partial update of old viewable chunks. Only do a full update
            // of new chunks.
            viewableOldChunks.addAll(newChunks);
            viewableOldChunks.retainAll(oldChunks);

            newChunks.removeAll(oldChunks);
        }

        // Send out grouped entity updates for old chunks that still remain in view.
        for (ChunkRepository chunk : viewableOldChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);
            if (!updates.isEmpty()) {
                updated.add(chunk);
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }

        // Send out grouped entity updates for new chunks in view.
        for (ChunkRepository chunk : newChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);
            // Chunk is being cleared, so resend static updates like displaying registered objects and items.
            for (ChunkUpdatableRequest request : chunk.getPersistentUpdates()) {
                ChunkUpdatableView view = request.getUpdatable().computeCurrentView();
                if (view.isViewableFor(player)) {
                    updates.add(request.getMessage());
                }
            }
            if (!updates.isEmpty()) {
                updated.add(chunk);
                player.queue(new ClearChunkMessageWriter(player.getLastRegion(), chunk));
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }
    }

    /**
     * Resets the list of pending updates for all updated chunks.
     */
    public void resetUpdatedChunks() {
        for (; ; ) {
            ChunkRepository chunk = updated.poll();
            if (chunk == null) {
                break;
            }
            chunk.resetUpdates();
        }
    }

    /**
     * Finds {@code type} entities matching {@code cond} within {@code distance} to {@code base}.
     * The entities will be stored in a set generated by {@code setFunc}.
     *
     * @param base     The base position to find entities around.
     * @param type     The type of entity to search for.
     * @param setFunc  Generates the set that the entities will be stored in.
     * @param cond     Filters the entities that will be found.
     * @param distance The distance to check for.
     * @param <T>      The type of entity to find.
     * @return The set of entities.
     */
    public <T extends Entity> Set<T> find(Position base, Class<T> type, Supplier<Set<T>> setFunc, Predicate<T> cond, int distance) {
        checkArgument(distance > 0, "[distance] cannot be below 1.");
        int radius = Math.floorDiv(distance, Chunk.SIZE) + 2;
        Set<T> found = setFunc.get();
        EntityType entityType = EntityType.CLASS_TO_TYPE.get(type);
        Chunk chunk = base.getChunk();
        for (int x = -radius; x < radius; x++) {
            for (int y = -radius; y < radius; y++) {
                ChunkRepository repository = load(chunk.translate(x, y));
                Set<T> entities = repository.getAll(entityType);
                for (T entity : entities) {
                    if (cond.test(entity)) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    /**
     * @return All chunks being managed by this repository.
     */
    public Collection<ChunkRepository> getAll() {
        return Collections.unmodifiableCollection(repositories.values());
    }

    /**
     * @return A stream over every single chunk.
     */
    public Stream<ChunkRepository> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
