package io.luna.game.model.collision;

import com.google.common.collect.HashMultimap;
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
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.path.route.LineOfSight;
import io.luna.game.model.path.route.RouteStrategy;
import io.luna.game.model.path.route.StepValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

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
     * Positions stored here get {@link CollisionFlag#BLOCK_WALK} when collision is built.
     */
    private final Multimap<Chunk, Position> blocked = Multimaps.synchronizedMultimap(HashMultimap.create());

    /**
     * Tiles that are covered by a roof, keyed by chunk.
     * <p>
     * Positions stored here get {@link CollisionFlag#ROOF} when collision is built.
     */
    private final Multimap<Chunk, Position> roofed = Multimaps.synchronizedMultimap(HashMultimap.create());

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
     * This method optionally clears existing matrices, imports blocked, bridged and roofed tile data from the cache,
     * registers static map objects into the world, applies the blocked and roofed tiles as collision, and then
     * snapshots the final repository state. Chunks that have no map data stay fully blocked when rebuilding.
     *
     * @param rebuilding {@code true} to reset existing matrices before rebuilding, otherwise {@code false}.
     */
    public void build(boolean rebuilding) {
        if (rebuilding) {
            for (ChunkRepository repository : chunks.getAll()) {
                if (repository.isUntraversable()) {
                    continue;
                }
                for (CollisionMatrix matrix : repository.getMatrices()) {
                    matrix.reset();
                }
            }
        }

        // Tile collision and map objects (water, borders, bridges, map features).
        LunaContext context = world.getContext();
        MapIndexTable table = context.getCache().getMapIndexTable();
        for (Map.Entry<MapIndex, MapTileGrid> entry : table.getTileSet()) {
            Region region = entry.getKey().getRegion();
            entry.getValue().forEach(tile -> {
                if (tile.isBlocked()) {
                    block(tile.getAbsPosition(region));
                }
                if (tile.isBridge()) {
                    markBridged(tile.getAbsPosition(region));
                }
                if (tile.isRoof()) {
                    markRoofed(tile.getAbsPosition(region));
                }
            });
        }

        for (MapObject mapObject : table.getObjectSet().getObjects()) {
            world.getObjects().register(mapObject.toGameObject(context));
        }

        // Apply blocked and roofed tiles. Bridged tiles are moved down a level when the update is applied.
        CollisionUpdate.Builder tiles = new CollisionUpdate.Builder();
        tiles.type(CollisionUpdateType.ADDING);
        tiles.mapCoordinates();
        for (Position position : blocked.values()) {
            tiles.flag(position, CollisionFlag.BLOCK_WALK);
        }
        for (Position position : roofed.values()) {
            tiles.flag(position, CollisionFlag.ROOF);
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
     * This is used for dynamic world changes such as spawned or removed objects, NPCs and players. Objects add their
     * walls and solid tiles. NPCs add {@link CollisionFlag#BLOCK_NPCS} to every tile they cover and players add
     * {@link CollisionFlag#BLOCK_PLAYERS} to the tile they stand on. Use {@link #moveEntity(Entity, Position)} to keep
     * NPCs and players up to date as they move.
     *
     * @param entity The entity whose collision should be updated.
     * @param removal {@code true} to remove collision, {@code false} to add it.
     */
    public void updateEntity(Entity entity, boolean removal) {
        EntityType type = entity.getType();
        if (type == EntityType.OBJECT) {
            CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
            builder.type(removal ? CollisionUpdateType.REMOVING : CollisionUpdateType.ADDING);
            builder.mapCoordinates();
            builder.object((GameObject) entity);
            apply(builder.build(), false);
        } else if (type == EntityType.NPC || type == EntityType.PLAYER) {
            occupy(entity.getPosition(), entity.size(), type, !removal);
        }
    }

    /**
     * Moves the {@link CollisionFlag#BLOCK_NPCS} or {@link CollisionFlag#BLOCK_PLAYERS} of an NPC or player from the
     * tiles it covered at {@code from} to the ones it covers now. Other entity types are ignored.
     *
     * @param entity The entity that moved. Its position must already be updated.
     * @param from The position it moved from.
     */
    public void moveEntity(Entity entity, Position from) {
        EntityType type = entity.getType();
        if (type == EntityType.NPC || type == EntityType.PLAYER) {
            int size = entity.size();
            occupy(from, size, type, false);
            occupy(entity.getPosition(), size, type, true);
        }
    }

    /**
     * Adds or removes the flag of an NPC or player on every tile of its footprint.
     *
     * @param position The south west tile of the footprint.
     * @param size The width and length of the footprint.
     * @param type The type of entity, {@link EntityType#NPC} or {@link EntityType#PLAYER}.
     * @param add {@code true} to add the flag, {@code false} to remove it.
     */
    private void occupy(Position position, int size, EntityType type, boolean add) {
        int flag = type == EntityType.NPC ? CollisionFlag.BLOCK_NPCS : CollisionFlag.BLOCK_PLAYERS;
        ChunkRepository repository = null;

        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                Position tile = new Position(position.getX() + dx, position.getY() + dy, position.getZ());
                if (repository == null || !repository.getChunk().equals(tile.getChunk())) {
                    repository = chunks.load(tile);
                }

                CollisionMatrix matrix = repository.getMatrices()[tile.getZ()];
                flag(add ? CollisionUpdateType.ADDING : CollisionUpdateType.REMOVING, matrix,
                        tile.getX() % Chunk.SIZE, tile.getY() % Chunk.SIZE, flag);
                pendingSnapshots.add(repository);
            }
        }
    }

    /**
     * Applies a {@link CollisionUpdate} to the world.
     * <p>
     * The {@link CollisionFlag}s of each tile in the update are added to or removed from the appropriate
     * {@link CollisionMatrix}, with bridge height adjustments applied where necessary. When the world is live, the
     * modified repositories are queued for snapshot refresh.
     *
     * @param update The collision update to apply.
     * @param building {@code true} if this update is part of the initial build process, otherwise {@code false}.
     */
    public void apply(CollisionUpdate update, boolean building) {
        ChunkRepository prev = null;

        CollisionUpdateType type = update.getType();
        Set<ChunkRepository> snapshots = new HashSet<>();

        // An object is lowered by a bridge as a whole, according to its own tile, so a wall is never split in two.
        Position origin = update.getOrigin();
        boolean bridgedObject = update.isMapCoordinates() && origin != null && isBridged(origin);

        for (Map.Entry<Position, Integer> entry : update.getFlags().entrySet()) {
            Position position = entry.getKey();
            Chunk chunk = position.getChunk();

            int height = position.getZ();
            // Adjust for bridges: map coordinates of some tiles are effectively one level lower.
            if (update.isMapCoordinates() && (origin != null ? bridgedObject : isBridged(position))) {
                if (--height < 0) {
                    continue;
                }
            }

            if (prev == null || !prev.getChunk().equals(chunk)) {
                prev = chunks.load(position);
            }

            int localX = position.getX() % Chunk.SIZE;
            int localY = position.getY() % Chunk.SIZE;

            flag(type, prev.getMatrices()[height], localX, localY, entry.getValue());
            snapshots.add(prev);
        }

        if (!building) {
            // Server is live: refresh snapshots only for the repositories that were modified.
            pendingSnapshots.addAll(snapshots);
        }
    }

    /**
     * Determines if there is a clear line of sight between two positions.
     * <p>
     * Walls and objects that stop projectiles also stop sight. Reads the live collision data, so this must only be
     * called from the game thread.
     *
     * @param start The position of the viewer.
     * @param end The position of what is viewed.
     * @return {@code true} if nothing that blocks sight is between {@code start} and {@code end}.
     * @throws IllegalArgumentException If the positions are not on the same height level.
     */
    public boolean raycast(Position start, Position end) {
        checkArgument(start.getZ() == end.getZ(), "Positions must be on the same height");
        return LineOfSight.hasLineOfSight(view(false), start.getZ(), start.getX(), start.getY(), end.getX(),
                end.getY());
    }

    /**
     * Creates a reader of the collision flags of tiles.
     *
     * @param safe {@code true} to read the snapshots, which is safe on any thread, or {@code false} to read the live
     * data, which is only safe on the game thread.
     * @return The new view. Views are cheap and not thread safe: use a new one for every search.
     */
    public CollisionView view(boolean safe) {
        return new CollisionView(chunks, safe);
    }

    /**
     * Applies or clears a mask of {@link CollisionFlag}s on a {@link CollisionMatrix}.
     *
     * @param type The update type.
     * @param matrix The matrix to modify.
     * @param localX The local X coordinate within the chunk.
     * @param localY The local Y coordinate within the chunk.
     * @param flag The collision flags to apply or clear.
     */
    private void flag(CollisionUpdateType type,
                      CollisionMatrix matrix,
                      int localX,
                      int localY,
                      int flag) {

        if (type == CollisionUpdateType.ADDING) {
            matrix.flag(localX, localY, flag);
        } else {
            matrix.clear(localX, localY, flag);
        }
    }

    /**
     * Marks {@code position} as globally blocked.
     * <p>
     * Blocked positions are applied with {@link CollisionFlag#BLOCK_WALK} during {@link #build(boolean)}.
     *
     * @param position The tile to block.
     */
    public void block(Position position) {
        blocked.put(position.getChunk(), position);
    }

    /**
     * Marks {@code position} as covered by a roof.
     * <p>
     * Roofed positions are applied with {@link CollisionFlag#ROOF} during {@link #build(boolean)}.
     *
     * @param position The roofed tile.
     */
    public void markRoofed(Position position) {
        roofed.put(position.getChunk(), position);
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
     * Returns whether the column of tiles at {@code position} is bridged, in map coordinates.
     *
     * @param position The tile to test. Its height is ignored.
     * @return {@code true} if the tile is bridged.
     */
    private boolean isBridged(Position position) {
        return bridges.contains(new Position(position.getX(), position.getY(), 1));
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
     * Returns whether an entity of {@code type} covering {@code size} by {@code size} tiles may move one step in
     * {@code direction}. {@code position} is the south west tile of the entity.
     * <p>
     * Only the tiles the entity moves into are checked, never the ones it already covers, so an entity is not stopped
     * by its own {@link CollisionFlag#BLOCK_NPCS}. The rules are those of {@link StepValidator}.
     *
     * @param position The south west tile the entity covers.
     * @param type The entity type attempting the move.
     * @param direction The direction being attempted.
     * @param size The width and length of the entity, in tiles.
     * @param safe {@code true} to use snapshot matrices, otherwise {@code false} to use live matrices.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position,
                               EntityType type,
                               Direction direction,
                               int size,
                               boolean safe) {
        return traversable(position, type, direction, size, RouteStrategy.NORMAL, safe);
    }

    /**
     * Returns whether an entity of {@code type} covering {@code size} by {@code size} tiles may move one step in
     * {@code direction}, moving onto tiles as {@code strategy} allows. {@code position} is the south west tile of the
     * entity.
     * <p>
     * A size 1 entity walking on ordinary ground is checked against the collision matrix directly. Every other entity
     * is checked by {@link StepValidator}.
     *
     * @param position The south west tile the entity covers.
     * @param type The entity type attempting the move.
     * @param direction The direction being attempted.
     * @param size The width and length of the entity, in tiles.
     * @param strategy The rule for moving onto a tile.
     * @param safe {@code true} to use snapshot matrices, otherwise {@code false} to use live matrices.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position,
                               EntityType type,
                               Direction direction,
                               int size,
                               RouteStrategy strategy,
                               boolean safe) {

        if (direction == Direction.NONE || (size <= 1 && strategy == RouteStrategy.NORMAL)) {
            return traversable(position, type, direction, safe);
        }

        int extraFlag = type == EntityType.NPC ? CollisionFlag.BLOCK_NPCS | CollisionFlag.BLOCK_PLAYERS : 0;
        return StepValidator.canTravel(view(safe), position.getZ(), position.getX(), position.getY(),
                direction.getTranslateX(), direction.getTranslateY(), size, extraFlag, strategy);
    }

    /**
     * Convenience overload of {@link #traversable(Position, EntityType, Direction, int, boolean)} that uses live
     * matrices.
     *
     * @param position The south west tile the entity covers.
     * @param type The entity type.
     * @param direction The attempted direction.
     * @param size The width and length of the entity, in tiles.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position, EntityType type, Direction direction, int size) {
        return traversable(position, type, direction, size, false);
    }

    /**
     * Convenience overload of {@link #traversable(Position, EntityType, Direction, int, RouteStrategy, boolean)} that
     * uses live matrices.
     *
     * @param position The south west tile the entity covers.
     * @param type The entity type.
     * @param direction The attempted direction.
     * @param size The width and length of the entity, in tiles.
     * @param strategy The rule for moving onto a tile.
     * @return {@code true} if the move is traversable, otherwise {@code false}.
     */
    public boolean traversable(Position position, EntityType type, Direction direction, int size,
                               RouteStrategy strategy) {
        return traversable(position, type, direction, size, strategy, false);
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
        return collisionData.isBlocked(localX, localY, EntityType.PROJECTILE) &&
                collisionData.isBlocked(localX, localY, EntityType.PLAYER);
    }

    /**
     * Determines whether a source has reached a target using the supplied interaction policy.
     * <p>
     * This is the main reachability check used by interactions. It supports simple tile occupation, line-of-sight
     * checks, and size-aware checks for mobs and objects.
     * <p>
     * The policy is resolved as follows:
     * <ul>
     *     <li>{@link InteractionType#UNSPECIFIED} always succeeds after the distance sanity check.</li>
     *     <li>A distance of {@code 0} requires the source and target to occupy the exact same tile.</li>
     *     <li>Targets outside {@link Position#VIEWING_DISTANCE} are rejected before type-specific checks run.</li>
     *     <li>{@link InteractionType#LINE_OF_SIGHT} requires the target to be within policy distance and visible
     *     through a successful {@link #raycast(Position, Position)}.</li>
     *     <li>{@link InteractionType#SIZE} uses collision-aware reach checks for adjacent mobs and objects, object
     *     box-distance checks for larger distances, and falls back to line-of-sight for other targets.</li>
     * </ul>
     *
     * @param source The entity or position attempting to interact.
     * @param target The entity or position being interacted with.
     * @param policy The interaction policy that controls distance and reach behavior.
     * @return {@code true} if the source has reached the target under the supplied policy; otherwise {@code false}.
     * @throws IllegalArgumentException If the policy distance is greater than {@link Position#VIEWING_DISTANCE}.
     */
    public boolean reached(Locatable source, Locatable target, InteractionPolicy policy) {
        int distance = policy.getDistance();
        int size = target instanceof Entity ? ((Entity) target).size() : 1;
        checkArgument(distance <= Position.VIEWING_DISTANCE, "Distance must be below max viewable range.");

        Position start = source.abs();
        Position end = target.abs();
        if (policy.getType() == InteractionType.UNSPECIFIED) {
            // No checks.
            return true;
        } else if (start.getZ() != end.getZ()) {
            return false;
        } else if (!start.isWithinDistance(target, Position.VIEWING_DISTANCE)) {
            // Can't interact if the entity isn't visible.
            return false;
        } else if (distance == 0) {
            // Distance of 0 always requires player to occupy tile.
            return start.equals(end);
        }
        // The reach checks read the flags of the start tile, so they need the matrix of the chunk the start is in.
        CollisionMatrix matrices = world.getChunks().load(start.getChunk()).getMatrices()[start.getZ()];
        switch (policy.getType()) {
            case LINE_OF_SIGHT:
                // Line of sight requires raycast and being within the distance.
                return start.isWithinDistance(end, distance) && raycast(start, end);
            case SIZE:
                if (distance == 1) {
                    if (target instanceof Mob) {
                        // Check if we're right beside a mob.
                        return matrices.reachedFacingEntity(start, (Mob) target,
                                ((Mob) target).sizeX(), ((Mob) target).sizeY(), OptionalInt.empty());
                    } else if (target instanceof GameObject) {
                        // Check if we're right beside an object.
                        return matrices.reachedObject(start, (GameObject) target);
                    }
                } else if (target instanceof GameObject) {
                    // Check if we're within box distance of an object (based on its size).
                    return isWithinBoxDistance(start, (GameObject) target, distance);
                }
                // Otherwise, fall back to line of sight.
                return start.isWithinDistance(end, distance) && raycast(start, end);
        }
        // Exhaustive code here, realistically should never be reached unless arguments are invalid.
        logger.warn("This section should not be reached! Invalid config [{}, {}, {}].", policy.getType(),
                box(policy.getDistance()), target);
        return start.isWithinDistance(target, distance);
    }

    /* TODO Test and implement.
     * Determines whether {@code position} is a valid tile to interact with {@code object} from.
     * <p>
     * This uses the object's rotated interaction-direction mask to reject tiles that are beside the object but blocked by
     * the object's directional access rules. For example, some objects can only be interacted with from certain sides.
     *
     * @param object The object being interacted with.
     * @param position The tile the mob/player would stand on.
     * @return {@code true} if {@code position} can interact with {@code object}.

    public boolean canInteractFrom(GameObject object, Position position) {
        int sizeX = object.def().getSizeX();
        int sizeY = object.def().getSizeY();

        if (object.getDirection().getId() == 1 || object.getDirection().getId() == 3) {
            int oldSizeX = sizeX;
            sizeX = sizeY;
            sizeY = oldSizeX;
        }
        **
         * Temporary: returns the packed direction from the live direction.
         *
        @Deprecated
        public int getDirectionPacked(ObjectDirection liveDir) {
            if (liveDir != ObjectDirection.WEST) {
                return (direction << liveDir.getId() & 0xf) +
                        (direction >> 4 - liveDir.getId());
            }
            return direction;
        }

        int packed = object.def().getDirectionPacked(object.getDirection());
        CollisionMatrix matrices = world.getChunks().load(object.getChunk()).getMatrices()[object.getPosition().getZ()];
        int startX = position.getLocalX(position);
        int startY = position.getLocalY(position);

        Position end = object.getPosition();
        int endX = end.getLocalX(position);
        int endY = end.getLocalY(position);

        int radiusX = (endX + sizeX) - 1;
        int radiusY = (endY + sizeY) - 1;
        return startX == endX - 1 && startY >= endY && startY <= radiusY && (matrices.get(position) & 8) == 0 && (packed & 8) == 0
                || startX == radiusX + 1 && startY >= endY && startY <= radiusY && (matrices.get(position) & 0x80) == 0 && (packed & 2) == 0
                || startY == endY - 1 && startX >= endX && startX <= radiusX && (matrices.get(position) & 2) == 0 && (packed & 4) == 0
                || startY == radiusY + 1 && startX >= endX && startX <= radiusX && (matrices.get(position) & 0x20) == 0 && (packed & 1) == 0;
    }*/

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
