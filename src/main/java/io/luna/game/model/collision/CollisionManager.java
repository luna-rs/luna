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
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Central manager for all collision data in the game world, responsible for
 * <ul>
 *     <li>Applying {@link CollisionUpdate}s to the appropriate {@link CollisionMatrix} instances.</li>
 *     <li>Tracking globally blocked tiles (e.g., pre-blocked regions or runtime modifications).</li>
 *     <li>Tracking tiles that participate in “bridged” structures (where height handling differs).</li>
 *     <li>Providing high-level APIs for traversability checks and “reached” queries.</li>
 * </ul>
 *
 * <p>
 * Collision data is stored per chunk in one or more {@link CollisionMatrix} layers (one per plane), and are
 * snapshotted for safe reads during asynchronous operations.
 * </p>
 *
 * @author Major
 * @author lare96
 */
public final class CollisionManager {

    /**
     * A thread-safe multimap of {@link Chunk} → {@link Position} for tiles that are completely blocked (untraversable
     * in all directions).
     */
    private final Multimap<Chunk, Position> blocked = Multimaps.synchronizedMultimap(HashMultimap.create());

    /**
     * A thread-safe set of positions that belong to “bridged” structures (e.g., bridges or raised tiles).
     * <p>
     * These tiles require special handling when computing the effective height used for collision.
     * </p>
     */
    private final Set<Position> bridges = Sets.newConcurrentHashSet();

    /**
     * A set of chunks that require a snapshot this ticks.
     */
    private final Set<ChunkRepository> pendingSnapshots = new HashSet<>();

    /**
     * The game world this manager belongs to.
     */
    private final World world;

    /**
     * The chunk manager used to locate {@link CollisionMatrix} instances.
     */
    private final ChunkManager chunks;

    /**
     * Creates a new {@code CollisionManager}.
     *
     * @param world The backing {@link World} instance.
     */
    public CollisionManager(World world) {
        this.world = world;
        this.chunks = world.getChunks();
    }

    /**
     * Applies all pending collision snapshots in a single, batched pass.
     * <p>
     * Whenever {@link #apply(CollisionUpdate, boolean)} is invoked with {@code building = false}, any affected
     * {@link ChunkRepository}s are queued in {@link #pendingSnapshots}. This method iterates that set, calling
     * {@link ChunkRepository#snapshotCollisionMap()} on each one and then clearing the queue.
     * </p>
     */
    public void handleSnapshots() {
        Iterator<ChunkRepository> it = pendingSnapshots.iterator();
        while (it.hasNext()) {
            it.next().snapshotCollisionMap();
            it.remove();
        }
    }

    /**
     * Performs the initial construction (or reconstruction) of all collision matrices.
     * <p>
     * This method:
     * </p>
     *
     * <ol>
     *     <li>Optionally clears all existing {@link CollisionMatrix}es if {@code rebuilding} is {@code true}.</li>
     *     <li>Applies collision from global pre-blocked tiles (see {@link #block(Position)}).</li>
     *     <li>Applies collision from all static {@link GameObject}s loaded from the cache.</li>
     *     <li>Snapshots each {@link ChunkRepository}'s collision state for subsequent safe reads.</li>
     * </ol>
     *
     * @param rebuilding {@code true} if matrices should be reset before rebuilding, otherwise {@code false}.
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
     * Applies or removes collision for a dynamic {@link Entity} that affects movement.
     *
     * <p>
     * This is a convenience helper for runtime changes (e.g. spawned/despawned objects or
     * NPCs that occupy tiles). Players are explicitly ignored because their collision is
     * handled via movement and pathing rules rather than static tiles.
     * </p>
     *
     * <ul>
     *     <li>If {@code removal} is {@code false}, a {@link CollisionUpdateType#ADDING} update is built.</li>
     *     <li>If {@code removal} is {@code true}, a {@link CollisionUpdateType#REMOVING} update is built.</li>
     *     <li>{@link GameObject}s delegate to {@link CollisionUpdate.Builder#object(GameObject)}.</li>
     *     <li>{@link Npc}s simply block their current tile in all directions.</li>
     * </ul>
     *
     * @param entity The entity whose collision should be updated.
     * @param removal {@code true} to remove collision for this entity, {@code false} to add it.
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
     * Applies a {@link CollisionUpdate} to the game world.
     * <p>
     * This method translates the per-tile, per-direction flags in {@code update} into {@link CollisionFlag}s on
     * the appropriate {@link CollisionMatrix} instances, taking into account bridged structures and chunk boundaries.
     * </p>
     *
     * @param update The collision update to apply.
     * @param building {@code true} if this is part of the initial build process (no snapshots needed per-update),
     * {@code false} if the server is live and per-repository snapshots should be refreshed for the affected matrices.
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
     * Casts a ray between two tiles, returning {@code false} if an impenetrable obstacle is found. This is a
     * convenience overload that uses projectile traversability ({@link EntityType#PROJECTILE}) as the blocking
     * condition.
     *
     * @param start The start position of the ray.
     * @param end The end position of the ray.
     * @return {@code true} if there is a clear line-of-sight between the two tiles, {@code false} if a blocking
     * tile is encountered.
     */
    public boolean raycast(Position start, Position end) {
        return raycast(start, end, (last, dir) -> !traversable(last, EntityType.PROJECTILE, dir));
    }

    /**
     * Casts a ray into the world between {@code start} and {@code end} using Bresenham's line algorithm.
     * <p>
     * At each step, a {@link Direction} from the previous tile to the current one is computed and passed to
     * {@code cond}. If {@code cond} returns {@code true} for any segment, the raycast is considered blocked and
     * this method returns {@code false}. If the end is reached without {@code cond} being satisfied, the raycast
     * is considered clear and this method returns {@code true}.
     * </p>
     *
     * @param start The start position of the ray.
     * @param end The end position of the ray.
     * @param cond A condition that tests whether the step between two tiles is considered blocked.
     * @return {@code true} if the ray reaches {@code end} without the condition being satisfied, {@code false} if
     * the condition is satisfied at any intermediate step.
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
     * Applies a single {@link CollisionFlag} to the given {@link CollisionMatrix}. This helper respects the
     * {@link CollisionUpdateType}: flags are either added or cleared.
     *
     * @param type The type of update (adding or removing).
     * @param matrix The matrix to modify.
     * @param localX The local X coordinate within the chunk (0–{@link Chunk#SIZE}).
     * @param localY The local Y coordinate within the chunk (0–{@link Chunk#SIZE}).
     * @param flag The collision flag to modify.
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
     * Marks a tile as fully blocked from all directions at its current height. These tiles are applied during
     * {@link #build(boolean)} via a collision update for all pre-blocked positions.
     *
     * @param position The position of the tile to block.
     */
    public void block(Position position) {
        blocked.put(position.getChunk(), position);
    }

    /**
     * Marks a tile as part of a bridged structure.
     * <p>
     * Bridges affect how collision height is resolved when applying updates, typically causing collision to be
     * applied one level below the visually raised tile.
     * </p>
     *
     * @param position The position that is part of a bridge.
     */
    public void markBridged(Position position) {
        bridges.add(position);
    }

    /**
     * Checks if an entity of the given {@link EntityType} can move from {@code position} one step in the
     * given {@code direction}.
     * <p>
     * This method performs a collision lookup in the appropriate {@link ChunkRepository} and, for diagonal movement,
     * validates both cardinal components of the diagonal to prevent corner-clipping.
     * </p>
     *
     * @param position The starting position.
     * @param type The type of entity attempting to move.
     * @param direction The direction to move in.
     * @param safe If {@code true}, uses snapshot matrices instead of live matrices, which is safer
     * for concurrent reads but may be slightly stale.
     * @return {@code true} if the destination tile is traversable, otherwise {@code false}.
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
     * Convenience overload of {@link #traversable(Position, EntityType, Direction, boolean)} that always uses
     * live matrices (safe mode off).
     *
     * @param position The starting position.
     * @param type The entity type.
     * @param direction The direction to move in.
     * @return {@code true} if the next tile is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position, EntityType type, Direction direction) {
        return traversable(position, type, direction, false);
    }

    /**
     * Returns whether a tile is blocked for player movement, optionally using the snapshot matrix.
     *
     * @param position The tile position to test.
     * @param safe {@code true} to query the snapshot (safe, read-only), {@code false} to query the live matrix.
     * @return {@code true} if the tile is blocked, {@code false} otherwise.
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
     * Determines if {@code start} is within the requested interaction {@code distance} of the target and that the
     * path between them is valid according to the type of {@link Entity}.
     * <p>
     * This method delegates to specialized reach checks based on the runtime type of {@code target}
     * (e.g., {@link GameObject}, {@link Mob}, {@link GroundItem}), and may also perform a
     * {@link #raycast(Position, Position)} for larger distances.
     * </p>
     *
     * @param start The starting position.
     * @param target The target entity to interact with.
     * @param distance The interaction distance (must not exceed {@link Position#VIEWING_DISTANCE}).
     * @return {@code true} if the start position has successfully “reached” the target for interaction,
     * otherwise {@code false}.
     */
    public boolean reached(Position start,
                           Entity target,
                           int distance) {

        checkArgument(distance <= Position.VIEWING_DISTANCE, "distance must be below max viewable range");
        if (!start.isViewable(target)) {
            // Can't interact if the entity isn't visible.
            return false;
        }

        Position end = target.getPosition();
        CollisionMatrix matrices = target.getChunkRepository().getMatrices()[start.getZ()];

        if (distance > 1) {
            // For extended range, as long as we have LOS and are in distance, we can interact.
            return start.isWithinDistance(end, distance) && raycast(start, end);
        } else if (target instanceof Mob) {
            // In the client, NPC sizes are all assumed 1x1.
            return matrices.reachedFacingEntity(start, target, 1, 1, OptionalInt.empty());
        } else if (target instanceof GameObject) {
            // Normal object, wall, or decoration.
            return matrices.reachedObject(start, (GameObject) target);
        } else if (target instanceof GroundItem) {
            // Items are only “reached” when standing directly on them.
            return start.equals(end);
        } else {
            // Fallback: reach within the target's size radius.
            return start.isWithinDistance(target.getPosition(), target.size());
        }
    }

    /**
     * Convenience overload of {@link #reached(Mob, Entity, int)} with an interaction distance of {@code 1}.
     *
     * @param player The player attempting to reach the target.
     * @param target The target entity.
     * @return {@code true} if the player has reached the target, otherwise {@code false}.
     */
    public boolean reached(Player player, Entity target) {
        return reached(player, target, 1);
    }

    /**
     * Determines if a {@link Mob} has reached an {@link Entity} at the specified interaction distance.
     * <p>
     * This method uses the mob's current position and, if the mob is a player, its last region position to compute
     * local coordinates for collision checks.
     * </p>
     *
     * @param mob The mob attempting to reach the target.
     * @param target The target entity.
     * @param distance The interaction distance.
     * @return {@code true} if the mob has reached the target, otherwise {@code false}.
     */
    public boolean reached(Mob mob, Entity target, int distance) {
        return reached(mob.getPosition(),
                target,
                distance);
    }
}
