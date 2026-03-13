package io.luna.game.model.collision;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import io.luna.LunaContext;
import io.luna.game.cache.map.MapIndex;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.game.cache.map.MapObject;
import io.luna.game.cache.map.MapTileGrid;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.collision.CollisionUpdate.DirectionFlag;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.object.GameObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Central manager for world collision data.
 * <p>
 * Collision is stored per {@link ChunkRepository} in one or more {@link CollisionMatrix} layers, one for each height
 * level. Repositories may also maintain collision snapshots for safe concurrent reads.
 *
 * @author Major
 * @author lare96
 */
public final class CollisionManager {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Globally blocked tiles keyed by chunk.
     * <p>
     * Positions stored here are treated as fully blocked in all directions when collision is built.
     */
    private final Multimap<Chunk, Position> blocked = Multimaps.synchronizedMultimap(HashMultimap.create());

    /**
     * Tiles that belong to bridged structures.
     * <p>
     * These positions require special height handling when collision is applied or queried.
     */
    private final Set<Position> bridges = Sets.newConcurrentHashSet();

    /**
     * The repositories that need their collision snapshots refreshed this tick.
     */
    private final Set<ChunkRepository> pendingSnapshots = new HashSet<>();

    /**
     * The world that owns this collision manager.
     */
    private final World world;

    /**
     * The chunk manager used to load collision repositories.
     */
    private final ChunkManager chunks;

    /**
     * Creates a new {@link CollisionManager}.
     *
     * @param world The backing world.
     */
    public CollisionManager(World world) {
        this.world = world;
        this.chunks = world.getChunks();
    }

    /**
     * Applies all pending collision snapshot refreshes.
     * <p>
     * Repositories are queued here when live collision is modified through {@link #apply(CollisionUpdate, boolean)}
     * with {@code building = false}. Each queued repository is snapshot once and then removed from the pending set.
     */
    public void handleSnapshots() {
        Iterator<ChunkRepository> it = pendingSnapshots.iterator();
        while (it.hasNext()) {
            it.next().snapshotCollisionMap();
            it.remove();
        }
    }

    /**
     * Builds or rebuilds all world collision data.
     * <p>
     * This method optionally clears existing matrices, imports blocked and bridged tile data from the cache, registers
     * static map objects into the world, applies global blocked tiles as collision, and then snapshots the final
     * repository state.
     *
     * @param rebuilding {@code true} to reset existing matrices before rebuilding, otherwise {@code false}.
     */
    public void build(boolean rebuilding) {
        if (rebuilding) {
            for (ChunkRepository repository : chunks.getAll()) {
                for (CollisionMatrix matrix : repository.getMatrices()) {
                    matrix.reset();
                }
            }
        }

        // Tile collision and map objects (water, borders, bridges, map features).
        LunaContext context = world.getContext();
        MapIndexTable table = context.getCache().getMapIndexTable();
        for (Map.Entry<MapIndex, MapTileGrid> entry : table.getTileSet()) {
            entry.getValue().forEach(tile -> {
                Region region = entry.getKey().getRegion();
                if (tile.isBlocked()) {
                    block(tile.getAbsPosition(region));
                } else if (tile.isBridge()) {
                    markBridged(tile.getAbsPosition(region));
                }
            });
        }

        for (MapObject mapObject : table.getObjectSet().getObjects()) {
            world.getObjects().register(mapObject.toGameObject(context));
        }

        // Apply global blocked tiles.
        CollisionUpdate.Builder tiles = new CollisionUpdate.Builder();
        tiles.type(CollisionUpdateType.ADDING);
        for (Position position : blocked.values()) {
            int x = position.getX();
            int y = position.getY();
            int height = position.getZ();

            // Handle bridged tiles by dropping one level when needed.
            if (bridges.contains(new Position(x, y, 1))) {
                height--;
            }

            if (height >= 0) {
                tiles.tile(new Position(x, y, height), false, Direction.NESW);
            }
        }
        apply(tiles.build(), true);

        // Snapshot final built state.
        for (ChunkRepository repository : chunks.getAll()) {
            repository.snapshotCollisionMap();
        }
    }

    /**
     * Applies or removes collision for a runtime entity.
     * <p>
     * This is used for dynamic world changes such as spawned or removed objects and NPCs. Players are ignored because
     * their blocking behavior is handled through movement and pathing rather than static tile collision.
     *
     * @param entity The entity whose collision should be updated.
     * @param removal {@code true} to remove collision, {@code false} to add it.
     */
    public void updateEntity(Entity entity, boolean removal) {
        if (entity.getType() == EntityType.PLAYER) {
            return;
        }

        CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
        if (!removal) {
            builder.type(CollisionUpdateType.ADDING);
        } else {
            builder.type(CollisionUpdateType.REMOVING);
        }
        if (entity.getType() == EntityType.OBJECT) {
            builder.object((GameObject) entity);
        } else if (entity.getType() == EntityType.NPC) {
            builder.tile(entity.getPosition(), false, Direction.NESW);
        }
        apply(builder.build(), false);
    }

    /**
     * Applies a {@link CollisionUpdate} to the world.
     * <p>
     * Each flagged tile in the update is translated into one or more {@link CollisionFlag}s on the appropriate
     * {@link CollisionMatrix}, with bridge height adjustments applied where necessary. When the world is live, the
     * modified repositories are queued for snapshot refresh.
     *
     * @param update The collision update to apply.
     * @param building {@code true} if this update is part of the initial build process, otherwise {@code false}.
     */
    public void apply(CollisionUpdate update, boolean building) {
        ChunkRepository prev = null;

        CollisionUpdateType type = update.getType();
        Map<Position, Collection<DirectionFlag>> map = update.getFlags().asMap();
        Set<ChunkRepository> snapshots = new HashSet<>();

        for (Map.Entry<Position, Collection<DirectionFlag>> entry : map.entrySet()) {
            Position position = entry.getKey();
            Chunk chunk = position.getChunk();

            int height = position.getZ();
            // Adjust for bridges: some tiles are effectively one level lower.
            if (bridges.contains(new Position(position.getX(), position.getY(), 1))) {
                if (--height < 0) {
                    continue;
                }
            }

            if (prev == null || !prev.getChunk().equals(chunk)) {
                prev = chunks.load(position);
            }

            int localX = position.getX() % Chunk.SIZE;
            int localY = position.getY() % Chunk.SIZE;

            CollisionMatrix matrix = prev.getMatrices()[height];
            ImmutableList<CollisionFlag> mobs = CollisionFlag.MOBS;
            ImmutableList<CollisionFlag> projectiles = CollisionFlag.PROJECTILES;

            for (DirectionFlag flag : entry.getValue()) {
                Direction direction = flag.getDirection();
                if (direction == Direction.NONE) {
                    continue;
                }

                int orientation = direction.getId();
                if (flag.isImpenetrable()) {
                    flag(type, matrix, localX, localY, projectiles.get(orientation));
                }
                flag(type, matrix, localX, localY, mobs.get(orientation));
            }
            snapshots.add(prev);
        }

        if (!building) {
            // Server is live: refresh snapshots only for the repositories that were modified.
            pendingSnapshots.addAll(snapshots);
        }
    }

    /**
     * Casts a projectile-style line-of-sight ray between two positions.
     * <p>
     * This is a convenience overload of {@link #raycast(Position, Position, BiFunction)} that treats projectile
     * collision as the blocking condition.
     *
     * @param start The ray start position.
     * @param end The ray end position.
     * @return {@code true} if the ray reaches {@code end} without hitting an impenetrable obstacle,
     *         otherwise {@code false}.
     */
    public boolean raycast(Position start, Position end) {
        return raycast(start, end, (last, dir) -> !traversable(last, EntityType.PROJECTILE, dir));
    }

    /**
     * Casts a ray between {@code start} and {@code end} using Bresenham's line algorithm.
     * <p>
     * For each step in the line, the direction from the previous tile to the current tile is passed to {@code cond}.
     * If {@code cond} returns {@code true} for any step, the ray is considered blocked and this method returns
     * {@code false}. Otherwise, the ray reaches its endpoint and this method returns {@code true}.
     *
     * @param start The ray start position.
     * @param end The ray end position.
     * @param cond The blocking condition applied to each traversed segment.
     * @return {@code true} if the ray reaches {@code end}, otherwise {@code false}.
     * @throws IllegalArgumentException If the positions are not on the same height level.
     */
    public boolean raycast(Position start,
                           Position end,
                           BiFunction<Position, Direction, Boolean> cond) {
        checkArgument(start.getZ() == end.getZ(), "Positions must be on the same height");
        if (start.equals(end)) {
            return true;
        }

        int x0 = start.getX();
        int y0 = start.getY();
        int x1 = end.getX();
        int y1 = end.getY();

        boolean steep = Math.abs(x0 - x1) < Math.abs(y0 - y1);

        // If the line is steep, swap x/y for both endpoints.
        if (steep) {
            int tmp = x0;
            x0 = y0;
            y0 = tmp;
            tmp = x1;
            x1 = y1;
            y1 = tmp;
        }

        // Ensure we always iterate from left to right.
        if (x0 > x1) {
            int tmp = x0;
            x0 = x1;
            x1 = tmp;
            tmp = y0;
            y0 = y1;
            y1 = tmp;
        }

        int dx = x1 - x0;
        int dy = y1 - y0;

        // Vertical line guard (after swaps).
        if (dx == 0) {
            int stepY = (y1 > y0) ? 1 : -1;

            int lastX = start.getX();
            int lastY = start.getY();
            boolean first = true;

            for (int y = y0; y != y1 + stepY; y += stepY) {
                int currX = steep ? y : x0;
                int currY = steep ? x0 : y;

                if (first) {
                    first = false;
                } else {
                    Direction direction = Direction.between(lastX, lastY, currX, currY);
                    Position last = new Position(lastX, lastY, start.getZ());

                    if (cond.apply(last, direction)) {
                        return false;
                    }
                }

                lastX = currX;
                lastY = currY;
            }
            return true;
        }

        int yStep = (y1 > y0) ? 1 : -1;
        float derror = Math.abs(dy / (float) dx);
        float error = 0.0f;

        int y = y0;
        int lastX = start.getX();
        int lastY = start.getY();
        boolean first = true;

        for (int x = x0; x <= x1; x++) {
            int currX, currY;
            if (steep) {
                currX = y;
                currY = x;
            } else {
                currX = x;
                currY = y;
            }

            error += derror;
            if (error >= 0.5f) {
                y += yStep;
                error -= 1.0f;
            }

            if (first) {
                first = false;
            } else {
                Direction direction = Direction.between(lastX, lastY, currX, currY);
                Position last = new Position(lastX, lastY, start.getZ());

                if (cond.apply(last, direction)) {
                    return false;
                }
            }

            lastX = currX;
            lastY = currY;
        }
        return true;
    }

    /**
     * Applies or clears a single {@link CollisionFlag} on a {@link CollisionMatrix}.
     *
     * @param type The update type.
     * @param matrix The matrix to modify.
     * @param localX The local X coordinate within the chunk.
     * @param localY The local Y coordinate within the chunk.
     * @param flag The collision flag to apply or clear.
     */
    private void flag(CollisionUpdateType type,
                      CollisionMatrix matrix,
                      int localX,
                      int localY,
                      CollisionFlag flag) {

        if (type == CollisionUpdateType.ADDING) {
            matrix.flag(localX, localY, flag);
        } else {
            matrix.clear(localX, localY, flag);
        }
    }

    /**
     * Marks {@code position} as globally blocked.
     * <p>
     * Blocked positions are applied as fully blocked tiles during {@link #build(boolean)}.
     *
     * @param position The tile to block.
     */
    public void block(Position position) {
        blocked.put(position.getChunk(), position);
    }

    /**
     * Marks {@code position} as bridged.
     * <p>
     * Bridged tiles affect effective collision height when updates are applied and when certain traversability
     * checks are performed.
     *
     * @param position The bridged position.
     */
    public void markBridged(Position position) {
        bridges.add(position);
    }

    /**
     * Returns whether an entity of {@code type} may move one step from {@code position} in {@code direction}.
     * <p>
     * This method performs a collision lookup in the appropriate {@link ChunkRepository}. For diagonal movement, both
     * orthogonal components are also checked to prevent corner clipping.
     *
     * @param position The starting position.
     * @param type The entity type attempting the move.
     * @param direction The direction being attempted.
     * @param safe {@code true} to use snapshot matrices, otherwise {@code false} to use live matrices.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position,
                               EntityType type,
                               Direction direction,
                               boolean safe) {

        Position next = position.translate(1, direction);
        ChunkRepository repository = chunks.load(next);

        if (!repository.traversable(next, type, direction, safe)) {
            return false;
        }

        // For diagonals, both orthogonal components must also be traversable.
        if (direction.isDiagonal()) {
            for (Direction component : Direction.diagonalComponents(direction)) {
                next = position.translate(1, component);

                Chunk nextChunk = next.getChunk();
                if (!repository.getChunk().equals(nextChunk)) {
                    repository = chunks.load(nextChunk);
                }

                if (!repository.traversable(next, type, component, safe)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Convenience overload of {@link #traversable(Position, EntityType, Direction, boolean)} that uses live matrices.
     *
     * @param position The starting position.
     * @param type The entity type.
     * @param direction The attempted direction.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position, EntityType type, Direction direction) {
        return traversable(position, type, direction, false);
    }

    /**
     * Returns whether {@code position} is blocked for player movement.
     *
     * @param position The tile to test.
     * @param safe {@code true} to query the snapshot matrix, otherwise {@code false} to query the live matrix.
     * @return {@code true} if the tile is blocked, otherwise {@code false}.
     */
    public boolean isBlocked(Position position, boolean safe) {
        int z = position.getZ();
        ChunkRepository chunk = world.getChunks().load(position);
        CollisionMatrix collisionData = safe ? chunk.getSnapshot()[z] : chunk.getMatrices()[z];
        if (collisionData == null) {
            return true;
        }
        int localX = position.getX() % 8;
        int localY = position.getY() % 8;
        return collisionData.isBlocked(localX, localY, EntityType.PLAYER);
    }

    /**
     * Returns whether {@code start} has satisfied the supplied interaction policy against {@code target}.
     * <p>
     * This is the main high-level reach check used by the interaction system. The exact rule depends on the
     * {@link InteractionType}:
     * <ul>
     *     <li>{@link InteractionType#UNSPECIFIED}: always succeeds</li>
     *     <li>{@link InteractionType#LINE_OF_SIGHT}: requires view range, distance, and a clear
     *     {@link #raycast(Position, Position)}</li>
     *     <li>{@link InteractionType#SIZE}: uses size-aware reach logic for mobs and objects, with
     *     distance-based fallbacks where appropriate</li>
     * </ul>
     *
     * @param start The interacting position.
     * @param target The target entity.
     * @param policy The interaction policy to test.
     * @return {@code true} if {@code start} has reached {@code target} under {@code policy},
     *         otherwise {@code false}.
     * @throws IllegalArgumentException If the policy distance exceeds
     *         {@link Position#VIEWING_DISTANCE}.
     */
    public boolean reached(Position start, Entity target, InteractionPolicy policy) {
        int distance = policy.getDistance();
        checkArgument(distance <= Position.VIEWING_DISTANCE, "Distance must be below max viewable range.");

        Position end = target.getPosition();
        if (policy.getType() == InteractionType.UNSPECIFIED) {
            // No checks.
            return true;
        } else if (distance == 0) {
            // Distance of 0 always requires player to occupy tile.
            return start.equals(end);
        } else if (!start.isViewable(target)) {
            // Can't interact if the entity isn't visible.
            return false;
        }

        CollisionMatrix matrices = target.getChunkRepository().getMatrices()[start.getZ()];
        switch (policy.getType()) {
            case LINE_OF_SIGHT:
                // Line of sight requires raycast and being within the distance.
                return start.isWithinDistance(end, distance) && raycast(start, end);
            case SIZE:
                if (distance == 1) {
                    if (target instanceof Mob) {
                        // Check if we're right beside a mob.
                        return matrices.reachedFacingEntity(start, target, 1, 1, OptionalInt.empty());
                    } else if (target instanceof GameObject) {
                        // Check if we're right beside an object.
                        return matrices.reachedObject(start, (GameObject) target);
                    }
                } else if (target instanceof GameObject) {
                    // Check if we're within box distance of an object (based on its size).
                    return isWithinBoxDistance(start, target, distance);
                }
                // Otherwise, fall back to if we're within distance of the target.
                return start.isWithinDistance(target.getPosition(), distance);
        }
        // Exhaustive code here, realistically should never be reached unless arguments are invalid.
        logger.warn("This section should not be reached! Invalid config [{}, {}, {}].", policy.getType(),
                box(policy.getDistance()), target);
        return start.isWithinDistance(target.getPosition(), distance);
    }

    /**
     * Returns whether {@code start} lies within the expanded size bounds of {@code target}.
     * <p>
     * This performs a box-distance check against the square footprint occupied by {@code target},
     * expanded outward by {@code distance}. It is mainly used for size-aware interaction checks
     * against objects.
     *
     * @param start The position being tested.
     * @param target The target entity whose footprint defines the box.
     * @param distance The allowed distance outside that footprint.
     * @return {@code true} if {@code start} is within the box-distance, otherwise {@code false}.
     */
    private boolean isWithinBoxDistance(Position start, Entity target, int distance) {
        Position end = target.getPosition();

        int minX = end.getX();
        int minY = end.getY();
        int maxX = minX + target.size() - 1;
        int maxY = minY + target.size() - 1;

        int dx = 0;
        if (start.getX() < minX) {
            dx = minX - start.getX();
        } else if (start.getX() > maxX) {
            dx = start.getX() - maxX;
        }

        int dy = 0;
        if (start.getY() < minY) {
            dy = minY - start.getY();
        } else if (start.getY() > maxY) {
            dy = start.getY() - maxY;
        }

        return Math.max(dx, dy) <= distance;
    }
}