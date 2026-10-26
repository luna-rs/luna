package io.luna.game.model;

import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Utility for locating world entities relative to a base location.
 * <p>
 * This class provides several common search operations used by gameplay logic, including:
 * <ul>
 *     <li>Range-based searches</li>
 *     <li>Viewing-distance searches</li>
 *     <li>Same-tile searches</li>
 *     <li>Nearest-entity lookups</li>
 * </ul>
 * Most searches are chunk-based and narrow the search space by converting tile distance into a chunk radius before
 * scanning repositories.
 */
public final class WorldLocator {

    /**
     * Default maximum tile distance for nearest-entity searches (ten chunks).
     */
    private static final int DEFAULT_NEAREST_DISTANCE = Chunk.SIZE * 10;

    /**
     * The world being searched.
     */
    private final World world;

    /**
     * Creates a new {@link WorldLocator}.
     *
     * @param world The world to search in.
     */
    public WorldLocator(World world) {
        this.world = world;
    }

    /**
     * Finds all entities of {@code type} within {@code distance} tiles of {@code base} and stores them in a
     * caller-supplied collection type.
     * <p>
     * The search traverses a computed chunk radius, then filters entities by exact tile distance and the supplied
     * predicate.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching entities must satisfy.
     * @param collectionSupplier Supplies the collection implementation used to accumulate results.
     * @param <C> The collection type returned.
     * @param <T> The entity type being searched for.
     * @return A collection containing all matching entities within range.
     */
    public <C extends Collection<T>, T extends Entity> C find(EntityType type, Locatable base, int distance,
                                                              Predicate<T> filter, Supplier<C> collectionSupplier) {
        C found = collectionSupplier.get();
        Position abs = base.abs();
        Chunk baseChunk = abs.getChunk();
        int radius = radiusForDistance(distance);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                ChunkRepository repository = world.getChunks().load(baseChunk.translate(x, y));
                Set<T> entities = repository.getAll(type);

                for (T entity : entities) {
                    if (entity.getPosition().isWithinDistance(abs, distance) && filter.test(entity)) {
                        found.add(entity);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Finds all entities of {@code type} within {@code distance} tiles of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param <T> The entity type being searched for.
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching entities must satisfy.
     * @return A set containing all matching entities within range.
     */
    public <T extends Entity> Set<T> find(EntityType type, Locatable base, int distance, boolean sorted, Predicate<T> filter) {
        return find(type, base, distance, filter, sorted ?
                () -> new TreeSet<>(new LocatableDistanceComparator(base)) : HashSet::new);
    }

    /**
     * Finds all entities of {@code type} within {@code distance} tiles of {@code base}.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching entities must satisfy.
     * @param <T> The entity type being searched for.
     * @return An unordered set containing all matching entities within range.
     */
    public <T extends Entity> Set<T> find(EntityType type, Locatable base, int distance, Predicate<T> filter) {
        return find(type, base, distance, false, filter);
    }

    /**
     * Finds all ground items within {@code distance} tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching items must satisfy.
     * @return All matching ground items within range.
     */
    public Set<GroundItem> findItems(Locatable base, int distance, Predicate<GroundItem> filter) {
        return find(EntityType.ITEM, base, distance, filter);
    }

    /**
     * Finds all NPCs within {@code distance} tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching NPCs must satisfy.
     * @return All matching NPCs within range.
     */
    public Set<Npc> findNpcs(Locatable base, int distance, Predicate<Npc> filter) {
        return find(EntityType.NPC, base, distance, filter);
    }

    /**
     * Finds all objects within {@code distance} tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching objects must satisfy.
     * @return All matching objects within range.
     */
    public Set<GameObject> findObjects(Locatable base, int distance, Predicate<GameObject> filter) {
        return find(EntityType.OBJECT, base, distance, filter);
    }

    /**
     * Finds all players within {@code distance} tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param filter Additional filter that matching players must satisfy.
     * @return All matching players within range.
     */
    public Set<Player> findPlayers(Locatable base, int distance, Predicate<Player> filter) {
        return find(EntityType.PLAYER, base, distance, filter);
    }

    /**
     * Finds all ground items within {@code distance} tiles of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching items must satisfy.
     * @return All matching ground items within range.
     */
    public Set<GroundItem> findItems(Locatable base, int distance, boolean sorted, Predicate<GroundItem> filter) {
        return find(EntityType.ITEM, base, distance, sorted, filter);
    }

    /**
     * Finds all NPCs within {@code distance} tiles of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching NPCs must satisfy.
     * @return All matching NPCs within range.
     */
    public Set<Npc> findNpcs(Locatable base, int distance, boolean sorted, Predicate<Npc> filter) {
        return find(EntityType.NPC, base, distance, sorted, filter);
    }

    /**
     * Finds all objects within {@code distance} tiles of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching objects must satisfy.
     * @return All matching objects within range.
     */
    public Set<GameObject> findObjects(Locatable base, int distance, boolean sorted, Predicate<GameObject> filter) {
        return find(EntityType.OBJECT, base, distance, sorted, filter);
    }

    /**
     * Finds all players within {@code distance} tiles of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param distance The maximum tile distance from {@code base}.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching players must satisfy.
     * @return All matching players within range.
     */
    public Set<Player> findPlayers(Locatable base, int distance, boolean sorted, Predicate<Player> filter) {
        return find(EntityType.PLAYER, base, distance, sorted, filter);
    }

    /**
     * Finds all entities of {@code type} within normal viewing distance of {@code base} that satisfy {@code filter}.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param filter Additional filter that matching entities must satisfy.
     * @param <T> The entity type being searched for.
     * @return All matching entities within viewing distance.
     */
    public <T extends Entity> Set<T> findViewable(EntityType type, Locatable base, Predicate<T> filter) {
        return find(type, base, Position.VIEWING_DISTANCE, filter);
    }

    /**
     * Finds all entities of {@code type} within normal viewing distance of {@code base} that satisfy {@code filter}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param filter Additional filter that matching entities must satisfy.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param <T> The entity type being searched for.
     * @return All matching entities within viewing distance.
     */
    public <T extends Entity> Set<T> findViewable(EntityType type, Locatable base, Predicate<T> filter,
                                                  boolean sorted) {
        return find(type, base, Position.VIEWING_DISTANCE, sorted, filter);
    }

    /**
     * Finds all entities of {@code type} within normal viewing distance of {@code base}.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param <T> The entity type being searched for.
     * @return All entities of {@code type} within viewing distance.
     */
    public <T extends Entity> Set<T> findViewable(EntityType type, Locatable base) {
        return find(type, base, Position.VIEWING_DISTANCE, entity -> true);
    }

    /**
     * Finds all entities of {@code type} within normal viewing distance of {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param <T> The entity type being searched for.
     * @param type The entity type to search for.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param base The origin of the search.
     * @return All entities of {@code type} within viewing distance.
     */
    public <T extends Entity> Set<T> findViewable(EntityType type, boolean sorted, Locatable base) {
        return find(type, base, Position.VIEWING_DISTANCE, sorted, entity -> true);
    }

    /**
     * Finds all viewable ground items relative to {@code base}.
     *
     * @param base The origin of the search.
     * @return All viewable ground items.
     */
    public Set<GroundItem> findViewableItems(Locatable base) {
        return findViewable(EntityType.ITEM, base);
    }

    /**
     * Finds all viewable ground items relative to {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @return All viewable ground items.
     */
    public Set<GroundItem> findViewableItems(Locatable base, boolean sorted) {
        return findViewable(EntityType.ITEM, sorted, base);
    }

    /**
     * Finds all viewable ground items relative to {@code base} that satisfy {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching items must satisfy.
     * @return All matching viewable ground items.
     */
    public Set<GroundItem> findViewableItems(Locatable base, Predicate<GroundItem> filter) {
        return findViewable(EntityType.ITEM, base, filter);
    }

    /**
     * Finds all viewable ground items relative to {@code base} that satisfy {@code filter}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching items must satisfy.
     * @return All matching viewable ground items.
     */
    public Set<GroundItem> findViewableItems(Locatable base, boolean sorted, Predicate<GroundItem> filter) {
        return findViewable(EntityType.ITEM, base, filter, sorted);
    }

    /**
     * Finds all viewable NPCs relative to {@code base}.
     *
     * @param base The origin of the search.
     * @return All viewable NPCs.
     */
    public Set<Npc> findViewableNpcs(Locatable base) {
        return findViewable(EntityType.NPC, base);
    }

    /**
     * Finds all viewable NPCs relative to {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @return All viewable NPCs.
     */
    public Set<Npc> findViewableNpcs(Locatable base, boolean sorted) {
        return findViewable(EntityType.NPC, sorted, base);
    }

    /**
     * Finds all viewable NPCs relative to {@code base} that satisfy {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching NPCs must satisfy.
     * @return All matching viewable NPCs.
     */
    public Set<Npc> findViewableNpcs(Locatable base, Predicate<Npc> filter) {
        return findViewable(EntityType.NPC, base, filter);
    }

    /**
     * Finds all viewable NPCs relative to {@code base} that satisfy {@code filter}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching NPCs must satisfy.
     * @return All matching viewable NPCs.
     */
    public Set<Npc> findViewableNpcs(Locatable base, boolean sorted, Predicate<Npc> filter) {
        return findViewable(EntityType.NPC, base, filter, sorted);
    }

    /**
     * Finds all viewable objects relative to {@code base}.
     *
     * @param base The origin of the search.
     * @return All viewable objects.
     */
    public Set<GameObject> findViewableObjects(Locatable base) {
        return findViewable(EntityType.OBJECT, base);
    }

    /**
     * Finds all viewable objects relative to {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @return All viewable objects.
     */
    public Set<GameObject> findViewableObjects(Locatable base, boolean sorted) {
        return findViewable(EntityType.OBJECT, sorted, base);
    }

    /**
     * Finds all viewable objects relative to {@code base} that satisfy {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching objects must satisfy.
     * @return All matching viewable objects.
     */
    public Set<GameObject> findViewableObjects(Locatable base, Predicate<GameObject> filter) {
        return findViewable(EntityType.OBJECT, base, filter);
    }

    /**
     * Finds all viewable objects relative to {@code base} that satisfy {@code filter}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching objects must satisfy.
     * @return All matching viewable objects.
     */
    public Set<GameObject> findViewableObjects(Locatable base, boolean sorted, Predicate<GameObject> filter) {
        return findViewable(EntityType.OBJECT, base, filter, sorted);
    }

    /**
     * Finds all viewable players relative to {@code base}.
     *
     * @param base The origin of the search.
     * @return All viewable players.
     */
    public Set<Player> findViewablePlayers(Locatable base) {
        return findViewable(EntityType.PLAYER, base);
    }

    /**
     * Finds all viewable players relative to {@code base}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @return All viewable players.
     */
    public Set<Player> findViewablePlayers(Locatable base, boolean sorted) {
        return findViewable(EntityType.PLAYER, sorted, base);
    }

    /**
     * Finds all viewable players relative to {@code base} that satisfy {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching players must satisfy.
     * @return All matching viewable players.
     */
    public Set<Player> findViewablePlayers(Locatable base, Predicate<Player> filter) {
        return findViewable(EntityType.PLAYER, base, filter);
    }

    /**
     * Finds all viewable players relative to {@code base} that satisfy {@code filter}.
     * <p>
     * Results may optionally be returned in distance order relative to {@code base}.
     *
     * @param base The origin of the search.
     * @param sorted {@code true} to return results sorted by distance, {@code false} to return an unordered set.
     * @param filter Additional filter that matching players must satisfy.
     * @return All matching viewable players.
     */
    public Set<Player> findViewablePlayers(Locatable base, boolean sorted, Predicate<Player> filter) {
        return findViewable(EntityType.PLAYER, base, filter, sorted);
    }

    /**
     * Finds all entities of {@code type} located exactly on the tile occupied by {@code base}.
     *
     * @param type The entity type to search for.
     * @param base The tile to search on.
     * @param filter Additional filter that matching entities must satisfy.
     * @param <T> The entity type.
     * @return A set containing all matching entities on the tile.
     */
    public <T extends Entity> Set<T> findOnTile(EntityType type, Locatable base, Predicate<T> filter) {
        Position abs = base.abs();
        ChunkRepository repository = world.getChunks().load(abs.getChunk());
        Set<T> found = new HashSet<>();
        Set<T> entities = repository.getAll(type);

        for (T entity : entities) {
            if (entity.getPosition().equals(abs) && filter.test(entity)) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * Finds all entities of {@code type} located exactly on the tile occupied by {@code base}.
     *
     * @param type The entity type to search for.
     * @param base The tile to search on.
     * @param <T> The entity type.
     * @return A set containing all matching entities on the tile.
     */
    public <T extends Entity> Set<T> findOnTile(EntityType type, Locatable base) {
        return findOnTile(type, base, entity -> true);
    }

    /**
     * Finds all ground items on the same tile as {@code base}.
     *
     * @param base The tile to search on.
     * @return The matching ground items.
     */
    public Set<GroundItem> findItemsOnTile(Locatable base) {
        return findOnTile(EntityType.ITEM, base);
    }

    /**
     * Finds all ground items on the same tile as {@code base} that satisfy {@code filter}.
     *
     * @param base The tile to search on.
     * @param filter The additional filter to apply.
     * @return The matching ground items.
     */
    public Set<GroundItem> findItemsOnTile(Locatable base, Predicate<GroundItem> filter) {
        return findOnTile(EntityType.ITEM, base, filter);
    }

    /**
     * Finds all objects on the same tile as {@code base}.
     *
     * @param base The tile to search on.
     * @return The matching objects.
     */
    public Set<GameObject> findObjectsOnTile(Locatable base) {
        return findOnTile(EntityType.OBJECT, base);
    }

    /**
     * Finds all objects on the same tile as {@code base} that satisfy {@code filter}.
     *
     * @param base The tile to search on.
     * @param filter The additional filter to apply.
     * @return The matching objects.
     */
    public Set<GameObject> findObjectsOnTile(Locatable base, Predicate<GameObject> filter) {
        return findOnTile(EntityType.OBJECT, base, filter);
    }

    /**
     * Finds all players on the same tile as {@code base}.
     *
     * @param base The tile to search on.
     * @return The matching players.
     */
    public Set<Player> findPlayersOnTile(Locatable base) {
        return findOnTile(EntityType.PLAYER, base);
    }

    /**
     * Finds all players on the same tile as {@code base} that satisfy {@code filter}.
     *
     * @param base The tile to search on.
     * @param filter The additional filter to apply.
     * @return The matching players.
     */
    public Set<Player> findPlayersOnTile(Locatable base, Predicate<Player> filter) {
        return findOnTile(EntityType.PLAYER, base, filter);
    }

    /**
     * Finds all NPCs on the same tile as {@code base}.
     *
     * @param base The tile to search on.
     * @return The matching NPCs.
     */
    public Set<Npc> findNpcsOnTile(Locatable base) {
        return findOnTile(EntityType.NPC, base);
    }

    /**
     * Finds all NPCs on the same tile as {@code base} that satisfy {@code filter}.
     *
     * @param base The tile to search on.
     * @param filter The additional filter to apply.
     * @return The matching NPCs.
     */
    public Set<Npc> findNpcsOnTile(Locatable base, Predicate<Npc> filter) {
        return findOnTile(EntityType.NPC, base, filter);
    }

    /**
     * Finds the nearest active entity of {@code type} within eighty tiles of {@code base}.
     * <p>
     * Searches use the same plane and square tile-distance rules as {@link Position#isWithinDistance(Position, int)}.
     * Use {@link #findNearest(EntityType, Locatable, int, Predicate)} to specify a different maximum distance.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param filter Additional filter that matching entities must satisfy.
     * @param <T> The entity type being searched for.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     */
    public <T extends Entity> T findNearest(EntityType type, Locatable base, Predicate<T> filter) {
        return findNearest(type, base, DEFAULT_NEAREST_DISTANCE, filter);
    }

    /**
     * Finds the nearest active entity of {@code type} within {@code distance} tiles of {@code base}.
     * <p>
     * Only chunks intersecting the padded search area are inspected; this method never falls back to a world-wide
     * scan. Candidates must be on the same plane and within the inclusive square tile radius. Equally distant
     * candidates are ordered by {@link LocatableDistanceComparator}.
     *
     * @param type The entity type to search for.
     * @param base The origin of the search.
     * @param distance The maximum tile distance, including zero for a same-tile search.
     * @param filter Additional filter that matching entities must satisfy.
     * @param <T> The entity type being searched for.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    public <T extends Entity> T findNearest(EntityType type, Locatable base, int distance, Predicate<T> filter) {
        checkArgument(distance >= 0, "Distance must be non-negative.");
        Set<T> candidates = find(type, base, distance,
                entity -> entity.getState() == EntityState.ACTIVE && filter.test(entity));
        LocatableDistanceComparator comparator = new LocatableDistanceComparator(base);
        T nearest = null;
        for (T candidate : candidates) {
            if (nearest == null || comparator.compare(candidate, nearest) < 0) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Finds the nearest ground item within eighty tiles of {@code base} that satisfies {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching items must satisfy.
     * @return The nearest matching ground item, or {@code null} if none is found.
     */
    public GroundItem findNearestItem(Locatable base, Predicate<GroundItem> filter) {
        return findNearest(EntityType.ITEM, base, filter);
    }

    /**
     * Finds the nearest active ground item within {@code distance} tiles on the same plane.
     *
     * @param base The origin of the search.
     * @param distance The maximum inclusive tile distance.
     * @param filter Additional filter that matching entities must satisfy.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    public GroundItem findNearestItem(Locatable base, int distance, Predicate<GroundItem> filter) {
        return findNearest(EntityType.ITEM, base, distance, filter);
    }

    /**
     * Finds the nearest ground item with the given id within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param id The item id to match.
     * @return The nearest matching ground item, or {@code null} if none is found.
     */
    public GroundItem findNearestItem(Locatable base, int id) {
        return findNearest(EntityType.ITEM, base, item -> item.getId() == id);
    }

    /**
     * Finds the nearest ground item with the given name within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param name The case-insensitive item name to match.
     * @return The nearest matching ground item, or {@code null} if none is found.
     */
    public GroundItem findNearestItem(Locatable base, String name) {
        return findNearest(EntityType.ITEM, base, item -> item.def().getName().equalsIgnoreCase(name));
    }

    /**
     * Finds the nearest NPC within eighty tiles of {@code base} that satisfies {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching NPCs must satisfy.
     * @return The nearest matching NPC, or {@code null} if none is found.
     */
    public Npc findNearestNpc(Locatable base, Predicate<Npc> filter) {
        return findNearest(EntityType.NPC, base, filter);
    }

    /**
     * Finds the nearest active NPC within {@code distance} tiles on the same plane.
     *
     * @param base The origin of the search.
     * @param distance The maximum inclusive tile distance.
     * @param filter Additional filter that matching entities must satisfy.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    public Npc findNearestNpc(Locatable base, int distance, Predicate<Npc> filter) {
        return findNearest(EntityType.NPC, base, distance, filter);
    }

    /**
     * Finds the nearest NPC with the given id within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param id The NPC id to match.
     * @return The nearest matching NPC, or {@code null} if none is found.
     */
    public Npc findNearestNpc(Locatable base, int id) {
        return findNearest(EntityType.NPC, base, npc -> npc.getId() == id);
    }

    /**
     * Finds the nearest NPC with the given name within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param name The case-insensitive NPC name to match.
     * @return The nearest matching NPC, or {@code null} if none is found.
     */
    public Npc findNearestNpc(Locatable base, String name) {
        return findNearest(EntityType.NPC, base, npc -> npc.def().getName().equalsIgnoreCase(name));
    }

    /**
     * Finds the nearest object within eighty tiles of {@code base} that satisfies {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching objects must satisfy.
     * @return The nearest matching object, or {@code null} if none is found.
     */
    public GameObject findNearestObject(Locatable base, Predicate<GameObject> filter) {
        return findNearest(EntityType.OBJECT, base, filter);
    }

    /**
     * Finds the nearest active object within {@code distance} tiles on the same plane.
     *
     * @param base The origin of the search.
     * @param distance The maximum inclusive tile distance.
     * @param filter Additional filter that matching entities must satisfy.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    public GameObject findNearestObject(Locatable base, int distance, Predicate<GameObject> filter) {
        return findNearest(EntityType.OBJECT, base, distance, filter);
    }

    /**
     * Finds the nearest object with the given id within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param id The object id to match.
     * @return The nearest matching object, or {@code null} if none is found.
     */
    public GameObject findNearestObject(Locatable base, int id) {
        return findNearest(EntityType.OBJECT, base, object -> object.getId() == id);
    }

    /**
     * Finds the nearest object with the given name within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param name The case-insensitive object name to match.
     * @return The nearest matching object, or {@code null} if none is found.
     */
    public GameObject findNearestObject(Locatable base, String name) {
        return findNearest(EntityType.OBJECT, base, object -> object.def().getName().equalsIgnoreCase(name));
    }

    /**
     * Finds the nearest player within eighty tiles of {@code base} that satisfies {@code filter}.
     *
     * @param base The origin of the search.
     * @param filter Additional filter that matching players must satisfy.
     * @return The nearest matching player, or {@code null} if none is found.
     */
    public Player findNearestPlayer(Locatable base, Predicate<Player> filter) {
        return findNearest(EntityType.PLAYER, base, filter);
    }

    /**
     * Finds the nearest active player within {@code distance} tiles on the same plane.
     *
     * @param base The origin of the search.
     * @param distance The maximum inclusive tile distance.
     * @param filter Additional filter that matching entities must satisfy.
     * @return The nearest matching entity within range, or {@code null} if none is found.
     * @throws IllegalArgumentException If {@code distance} is negative.
     */
    public Player findNearestPlayer(Locatable base, int distance, Predicate<Player> filter) {
        return findNearest(EntityType.PLAYER, base, distance, filter);
    }

    /**
     * Finds the nearest player with the given username within eighty tiles of {@code base}.
     *
     * @param base The origin of the search.
     * @param name The case-insensitive username to match.
     * @return The nearest matching player, or {@code null} if none is found.
     */
    public Player findNearestPlayer(Locatable base, String name) {
        return findNearest(EntityType.PLAYER, base, player -> player.getUsername().equalsIgnoreCase(name));
    }

    /**
     * Finds the nearest active player within eighty tiles.
     *
     * @param base The origin of the search.
     * @return The nearest active player within eighty tiles, or {@code null} if none is found.
     */
    public Player findNearestPlayer(Locatable base) {
        return findNearest(EntityType.PLAYER, base, player -> true);
    }

    /**
     * Computes the list of players visible to {@code player}.
     * <p>
     * This is typically used when building player update state for the current cycle.
     *
     * @param player The player whose visible players are being resolved.
     * @return A list of visible players.
     */
    public List<Player> computeVisiblePlayersFor(Player player) {
        return computeVisibleMobsFor(EntityType.PLAYER, player);
    }

    /**
     * Computes the list of NPCs visible to {@code player}.
     * <p>
     * This is typically used when building player update state for the current cycle.
     *
     * @param player The player whose visible NPCs are being resolved.
     * @return A list of visible NPCs.
     */
    public List<Npc> computeVisibleNpcsFor(Player player) {
        return computeVisibleMobsFor(EntityType.NPC, player);
    }

    /**
     * Computes the list of visible mobs of {@code type} for {@code player}.
     *
     * @param type The mob type to search for.
     * @param player The player whose visible mob list is being resolved.
     * @param <T> The mob type being returned.
     * @return A list of visible mobs of the requested type.
     */
    private <T extends Mob> List<T> computeVisibleMobsFor(EntityType type, Player player) {
        return find(type, player.getPosition(), Position.VIEWING_DISTANCE, mob -> mob.isViewableFrom(player),
                ArrayList::new);
    }

    /**
     * Converts a tile distance into a chunk radius large enough to cover the relevant search area.
     * <p>
     * The extra padding helps avoid missing edge cases near chunk boundaries.
     *
     * @param distance The tile distance to convert.
     * @return The chunk radius required to cover that distance.
     */
    private int radiusForDistance(int distance) {
        return Math.floorDiv(distance, Chunk.SIZE) + 1;
    }
}