package io.luna.game.model.collision;

import com.google.common.collect.HashMultimap;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.collision.CollisionUpdate.DirectionFlag;
import io.luna.game.model.object.GameObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Manages applying {@link CollisionUpdate}s to the appropriate {@link CollisionMatrix}, and keeping
 * a record of collision state (i.e., which tiles are bridged).
 */
public final class CollisionManager {

    /**
     * A {@code HashMultimap} of region coordinates mapped to positions where the tile is completely blocked.
     */
    private final HashMultimap<Chunk, Position> blocked = HashMultimap.create();

    /**
     * A {@code HashSet} of positions where the tile is part of a bridged structure.
     */
    private final Set<Position> bridges = new HashSet<>();

    /**
     * The {@link ChunkManager} used to lookup {@link CollisionMatrix} objects.
     */
    private final ChunkManager chunks;

    /**
     * Creates the {@code CollisionManager}.
     *
     * @param world The {@link World} instance.
     */
    public CollisionManager(World world) {
        chunks = world.getChunks();
    }

    /**
     * Applies the initial {@link CollisionUpdate} to the {@link CollisionMatrix}es for all objects and tiles loaded
     * from the cache.
     *
     * @param rebuilding A flag indicating whether or not {@link CollisionMatrix}es are being rebuilt.
     */
    public void build(boolean rebuilding) {
        if (rebuilding) {
            for (ChunkRepository repository : chunks.getAll()) {
                for (CollisionMatrix matrix : repository.getMatrices()) {
                    matrix.reset();
                }
            }
        }

        for (ChunkRepository repository : chunks.getAll()) {
            CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
            builder.type(CollisionUpdateType.ADDING);

            Set<Position> blockedPositions = blocked.get(repository.getChunk());
            for (Position position : blockedPositions) {
                int x = position.getX(), y = position.getY();
                int height = position.getZ();

                if (bridges.contains(new Position(x, y, 1))) {
                    height--;
                }

                if (height >= 0) {
                    builder.tile(new Position(x, y, height), false, Direction.NESW);
                }
            }

            apply(builder.build());

            CollisionUpdate.Builder objects = new CollisionUpdate.Builder();
            objects.type(CollisionUpdateType.ADDING);

            repository.getAll(EntityType.OBJECT)
                    .forEach(entity -> objects.object((GameObject) entity));

            apply(objects.build());
        }
    }

    /**
     * Apply a {@link CollisionUpdate} to the game world.
     *
     * @param update The update to apply.
     */
    public void apply(CollisionUpdate update) {
        ChunkRepository prev = null;

        CollisionUpdateType type = update.getType();
        Map<Position, Collection<DirectionFlag>> map = update.getFlags().asMap();

        for (Map.Entry<Position, Collection<DirectionFlag>> entry : map.entrySet()) {
            Position position = entry.getKey();
            Chunk chunk = position.getChunk();

            int height = position.getZ();
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
            CollisionFlag[] mobs = CollisionFlag.mobs();
            CollisionFlag[] projectiles = CollisionFlag.projectiles();

            for (DirectionFlag flag : entry.getValue()) {
                Direction direction = flag.getDirection();
                if (direction == Direction.NONE) {
                    continue;
                }

                int orientation = direction.getId();
                if (flag.isImpenetrable()) {
                    flag(type, matrix, localX, localY, projectiles[orientation]);
                }

                flag(type, matrix, localX, localY, mobs[orientation]);
            }
        }
    }

    /**
     * Casts a ray into the world to check for impenetrable objects  from the given {@code start} position to the
     * {@code end} position using Bresenham's line algorithm.
     *
     * @param start The start position of the ray.
     * @param end The end position of the ray.
     * @return {@code false} if an impenetrable object was hit, {@code true} otherwise.
     */
    public boolean raycast(Position start, Position end) {
        return raycast(start, end, (last, dir) -> !traversable(last, EntityType.NPC, dir));
    }

    /**
     * Casts a ray into the world to check for impenetrable objects  from the given {@code start} position to the
     * {@code end} position using Bresenham's line algorithm.
     *
     * @param start The start position of the ray.
     * @param end The end position of the ray.
     * @return {@code false} if an impenetrable object was hit, {@code true} otherwise.
     */
    public boolean raycast(Position start, Position end, BiFunction<Position, Direction, Boolean> conditionFunction) {
        //todo doesnt work
        checkArgument(start.getZ() == end.getZ(), "Positions must be on the same height");
        if (start.equals(end)) {
            return true;
        }

        int x0 = start.getX();
        int x1 = end.getX();
        int y0 = start.getY();
        int y1 = end.getY();

        boolean steep = false;
        if (Math.abs(x0 - x1) < Math.abs(y0 - y1)) {
            int tmp = y0;
            x0 = y0;
            y0 = tmp;

            tmp = x1;
            x1 = y1;
            y1 = tmp;
            steep = true;
        }

        if (x0 > x1) {
            int tmp = x0;
            x0 = y1;
            y1 = tmp;

            tmp = y0;
            y0 = y1;
            y1 = tmp;
        }

        int dx = x1 - x0;
        int dy = y1 - y0;

        float derror = Math.abs(dy / (float) dx);
        float error = 0;

        int y = y0;
        int currX, currY;

        int lastX = start.getX(), lastY = start.getY();
        boolean first = true;

        for (int x = x0; x <= x1; x++) {
            if (steep) {
                currX = y;
                currY = x;
            } else {
                currX = x;
                currY = y;
            }

            error += derror;
            if (error > 0.5) {
                y += (y1 > y0 ? 1 : -1);
                error -= 1.0F;
            }

            if (first) {
                first = false;
                continue;
            }

            Direction direction = Direction.between(lastX, lastY, currX, currY);
            Position last = new Position(lastX, lastY, start.getZ());

            if (conditionFunction.apply(last, direction)) {
                return false;
            }

            lastX = currX;
            lastY = currY;
        }
        return true;
    }

    /**
     * Apply a {@link CollisionUpdate} flag to a {@link CollisionMatrix}.
     *
     * @param type The type of update to apply.
     * @param matrix The matrix the update is being applied to.
     * @param localX The local X position of the tile the flag represents.
     * @param localY The local Y position of the tile the flag represents.
     * @param flag The {@link CollisionFlag} to update.
     */
    private void flag(CollisionUpdateType type, CollisionMatrix matrix, int localX, int localY, CollisionFlag flag) {
        if (type == CollisionUpdateType.ADDING) {
            matrix.flag(localX, localY, flag);
        } else {
            matrix.clear(localX, localY, flag);
        }
    }

    /**
     * Marks a tile as completely untraversable from all directions.
     *
     * @param position The {@link Position} of the tile.
     */
    public void block(Position position) {
        blocked.put(position.getChunk(), position);
    }

    /**
     * Marks a tile as part of a bridge.
     *
     * @param position The {@link Position} of the tile.
     */
    public void markBridged(Position position) {
        bridges.add(position);
    }

    /**
     * Checks if the given {@link EntityType} can traverse to the next tile from {@code position} in the given
     * {@code direction}.
     *
     * @param position The current position of the entity.
     * @param type The type of the entity.
     * @param direction The direction the entity is travelling.
     * @return {@code true} if next tile is traversable, {@code false} otherwise.
     */
    public boolean traversable(Position position, EntityType type, Direction direction) { // TODO cahnge to boolean projectile
        Position next = position.translate(1, direction);
        ChunkRepository repository = chunks.load(next);

        if (!repository.traversable(next, type, direction)) {
            return false;
        }

        if (direction.isDiagonal()) {
            for (Direction component : Direction.diagonalComponents(direction)) {
                next = position.translate(1, component);

                Chunk nextChunk = next.getChunk();
                if (!repository.getChunk().equals(nextChunk)) {
                    repository = chunks.load(nextChunk);
                }

                if (!repository.traversable(next, type, component)) {
                    return false;
                }
            }
        }

        return true;
    }

}