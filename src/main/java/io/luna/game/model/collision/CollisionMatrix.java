package io.luna.game.model.collision;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import io.luna.Luna;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * A 2D grid of collision data for a single chunk plane.
 * <p>
 * Each cell in the matrix is a 32-bit mask of {@link CollisionFlag} values, describing what blocks movement and
 * projectiles on the tile at that local (x, y) coordinate.
 * </p>
 *
 * @author Major
 * @author lare96
 */
public final class CollisionMatrix {

    /**
     * Bit pattern representing a fully open tile (no collision flags set).
     */
    private static final int ALL_ALLOWED = 0;

    /**
     * Bit pattern representing a fully blocked tile (every collision flag set).
     */
    private static final int ALL_BLOCKED = -1;

    /**
     * Bit pattern representing a tile where mobs are blocked but projectiles may still pass.
     */
    private static final int ALL_MOBS_BLOCKED = ALL_BLOCKED & ~CollisionFlag.PROJECTILE_BLOCKERS;

    /**
     * The flags that stop a mob standing on a tile from stepping off it, regardless of side.
     */
    private static final int SOLID = CollisionFlag.LOC | CollisionFlag.BLOCK_WALK;

    /**
     * The flags that stop a mob from stepping off a tile to the north, used by the reach checks.
     */
    private static final int NORTH_BLOCKED = CollisionFlag.WALL_NORTH | SOLID;

    /**
     * The flags that stop a mob from stepping off a tile to the east, used by the reach checks.
     */
    private static final int EAST_BLOCKED = CollisionFlag.WALL_EAST | SOLID;

    /**
     * The flags that stop a mob from stepping off a tile to the south, used by the reach checks.
     */
    private static final int SOUTH_BLOCKED = CollisionFlag.WALL_SOUTH | SOLID;

    /**
     * The flags that stop a mob from stepping off a tile to the west, used by the reach checks.
     */
    private static final int WEST_BLOCKED = CollisionFlag.WALL_WEST | SOLID;

    /**
     * Creates an array of fully open {@link CollisionMatrix} instances, each with identical width and length.
     *
     * @param count The number of matrices to create.
     * @param width The width (X dimension) of each matrix.
     * @param length The length (Y dimension) of each matrix.
     * @return A new array of {@code count} {@link CollisionMatrix} objects.
     */
    public static CollisionMatrix[] createMatrices(int count, int width, int length) {
        return createMatrices(count, width, length, false);
    }

    /**
     * Creates an array of {@link CollisionMatrix} instances, each with identical width and length.
     *
     * @param count The number of matrices to create.
     * @param width The width (X dimension) of each matrix.
     * @param length The length (Y dimension) of each matrix.
     * @param blocked {@code true} to create every tile fully blocked, {@code false} to create every tile fully open.
     * @return A new array of {@code count} {@link CollisionMatrix} objects.
     */
    public static CollisionMatrix[] createMatrices(int count, int width, int length, boolean blocked) {
        CollisionMatrix[] matrices = new CollisionMatrix[count];
        Arrays.setAll(matrices, index -> {
            CollisionMatrix matrix = new CollisionMatrix(width, length);
            if (blocked) {
                Arrays.fill(matrix.matrix, ALL_BLOCKED);
            }
            return matrix;
        });
        return matrices;
    }

    /**
     * The length (Y dimension) of this matrix.
     */
    private final int length;

    /**
     * The underlying collision data, as a flat array of packed {@link CollisionFlag} masks.
     */
    private final int[] matrix;

    /**
     * The low bit of each flag's overlap counter, or {@code null} until a flag is first set twice.
     */
    private int[] overlapLow;

    /**
     * The high bit of each flag's overlap counter, or {@code null} until a flag is first set twice.
     */
    private int[] overlapHigh;

    /**
     * The width (X dimension) of this matrix.
     */
    private final int width;

    /**
     * Creates a new {@link CollisionMatrix} with the given dimensions.
     *
     * @param width The width (X dimension) of the matrix.
     * @param length The length (Y dimension) of the matrix.
     */
    CollisionMatrix(int width, int length) {
        this.width = width;
        this.length = length;
        matrix = new int[width * length];
    }

    /**
     * Creates a new {@link CollisionMatrix} with the given dimensions and matrix.
     *
     * @param width The width (X dimension) of the matrix.
     * @param length The length (Y dimension) of the matrix.
     * @param matrix The underlying collision data, as a flat array of packed {@link CollisionFlag} masks.
     */
    private CollisionMatrix(int width, int length, int[] matrix) {
        this.width = width;
        this.length = length;
        this.matrix = matrix;
    }

    /**
     * Returns whether <strong>all</strong> of the specified {@link CollisionFlag}s are set for the given tile.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flags The collision flags to test.
     * @return {@code true} if every flag in {@code flags} is set; otherwise {@code false}.
     */
    public boolean all(int x, int y, int... flags) {
        for (int flag : flags) {
            if (!flagged(x, y, flag)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether <strong>any</strong> of the specified {@link CollisionFlag}s are set for the given tile.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flags The collision flags to test.
     * @return {@code true} if at least one flag in {@code flags} is set; otherwise {@code false}.
     */
    public boolean any(int x, int y, int... flags) {
        for (int flag : flags) {
            if (flagged(x, y, flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks the tile at (x, y) as fully blocked for mob movement, and optionally for projectiles.
     * <p>
     * When {@code impenetrable} is {@code true}, both mobs and projectiles are blocked. When {@code false}, mobs are
     * blocked but projectiles may still traverse the tile.
     * </p>
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param impenetrable {@code true} to block projectiles as well as mobs; {@code false} to block mobs only.
     */
    void block(int x, int y, boolean impenetrable) {
        set(x, y, impenetrable ? ALL_BLOCKED : ALL_MOBS_BLOCKED);
    }

    /**
     * Marks the tile at (x, y) as fully blocked for both mobs and projectiles.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     */
    void block(int x, int y) {
        block(x, y, true);
    }

    /**
     * Clears the specified {@link CollisionFlag} for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to clear.
     */
    void clear(int x, int y, int flag) {
        int index = indexOf(x, y);
        int held = releaseOverlap(index, flag);
        matrix[index] &= ~(flag & ~held);
    }

    /**
     * Sets (ORs) the specified {@link CollisionFlag} for the tile at (x, y).
     * <p>
     * A flag that is already set is counted, so that it stays set until every {@link #flag(int, int, int)} call that
     * set it has been matched by a {@link #clear(int, int, int)} call. This lets overlapping objects, or several
     * entities standing on one tile, share a flag without the first one to leave clearing it for the rest.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to set.
     */
    void flag(int x, int y, int flag) {
        int index = indexOf(x, y);
        int shared = matrix[index] & flag;
        if (shared != 0) {
            retainOverlap(index, shared);
        }
        matrix[index] |= flag;
    }

    /**
     * Counts one more holder of each flag in {@code flags}, which are already set on the tile at {@code index}.
     * <p>
     * Each flag has a two bit counter spread over {@link #overlapLow} and {@link #overlapHigh}, so a flag can be held
     * by up to four callers at once. A flag held by more callers than that stays at the maximum.
     *
     * @param index The index of the tile.
     * @param flags The flags that are already set.
     */
    private void retainOverlap(int index, int flags) {
        if (overlapLow == null) {
            overlapLow = new int[matrix.length];
            overlapHigh = new int[matrix.length];
        }
        int low = overlapLow[index];
        int high = overlapHigh[index];

        int counted = flags & ~(low & high);
        int carry = low & counted;
        overlapLow[index] = low ^ counted;
        overlapHigh[index] = high ^ carry;
    }

    /**
     * Releases one holder of each flag in {@code flags} on the tile at {@code index}.
     *
     * @param index The index of the tile.
     * @param flags The flags being cleared.
     * @return The flags that other callers still hold, and so must stay set.
     */
    private int releaseOverlap(int index, int flags) {
        if (overlapLow == null) {
            return 0;
        }
        int low = overlapLow[index];
        int high = overlapHigh[index];

        int held = (low | high) & flags;
        int lowOnly = low & held;
        int highOnly = high & held & ~low;
        overlapLow[index] = (low & ~lowOnly) | highOnly;
        overlapHigh[index] = high & ~highOnly;
        return held;
    }

    /**
     * Returns whether the specified {@link CollisionFlag} is set for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to test.
     * @return {@code true} if the flag is set; otherwise {@code false}.
     */
    public boolean flagged(int x, int y, int flag) {
        return (get(x, y) & flag) != 0;
    }

    /**
     * Retrieves the packed collision value for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @return The packed 32-bit collision value for that tile.
     */
    public int get(int x, int y) {
        return matrix[indexOf(x, y)];
    }

    /**
     * Retrieves the packed collision value for the tile at the given absolute position.
     * <p>
     * The supplied position is converted to local chunk coordinates using {@link Chunk#SIZE}.
     * </p>
     *
     * @param position The absolute world position.
     * @return The packed 32-bit collision value for that tile.
     */
    public int get(Position position) {
        return matrix[indexOf(position.getX() % Chunk.SIZE, position.getY() % Chunk.SIZE)];
    }

    /**
     * Resets the tile at (x, y) to a fully open state (no collision flags).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     */
    void reset(int x, int y) {
        set(x, y, ALL_ALLOWED);
    }

    /**
     * Resets all tiles in this matrix to a fully open state.
     */
    void reset() {
        Arrays.fill(matrix, ALL_ALLOWED);
        overlapLow = null;
        overlapHigh = null;
    }

    /**
     * Replaces all flags for the tile at (x, y) with the supplied {@link CollisionFlag} mask.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag mask to set (overwriting any existing flags).
     */
    void set(int x, int y, int flag) {
        matrix[indexOf(x, y)] = flag;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("width", width)
                .add("length", length)
                .add("matrix", Arrays.toString(matrix))
                .toString();
    }

    /**
     * Determines whether an entity of the given {@link EntityType} is blocked from entering the tile at (x, y)
     * when attempting to move in the given {@link Direction}.
     * <p>
     * Walls, solid objects and blocked floors on the tile stop the movement according to the {@code BLOCK_*} masks
     * in {@link CollisionFlag}. Projectiles are only stopped by walls and objects that also block projectiles.
     *
     * @param x The local X coordinate of the tile being entered.
     * @param y The local Y coordinate of the tile being entered.
     * @param entity The entity type (e.g. player, NPC, projectile).
     * @param direction The movement direction from the previous tile into this tile.
     * @return {@code true} if the tile is blocked when approached from {@code direction}; otherwise {@code false}.
     */
    public boolean untraversable(int x, int y, EntityType entity, Direction direction) {
        if (direction == Direction.NONE) {
            throw new IllegalArgumentException("Unrecognised direction " + direction + ".");
        }
        return flagged(x, y, CollisionFlag.blocks(entity, direction.getId()));
    }

    /**
     * Returns whether the tile at (x, y) is blocked for the given {@link EntityType}, regardless of direction. This
     * reports {@code true} if any flag that stops the entity type from entering the tile is set.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param entity The entity type to test collision for.
     * @return {@code true} if the tile is blocked for that entity type; otherwise {@code false}.
     */
    public boolean isBlocked(int x, int y, EntityType entity) {
        return flagged(x, y, CollisionFlag.anyBlock(entity));
    }

    /**
     * Computes the internal array index corresponding to the tile at (x, y).
     *
     * @param x The local X coordinate (0 ≤ x &lt; width).
     * @param y The local Y coordinate (0 ≤ y &lt; length).
     * @return The flat array index for that tile.
     * @throws ArrayIndexOutOfBoundsException If (x, y) is out of range for this matrix.
     */
    private int indexOf(int x, int y) {
        if (Luna.settings().game().betaMode()) {
            // Construct debug bounds descriptions only when validation fails.
            if (x < 0 || x >= width) {
                Preconditions.checkElementIndex(x, width, "X coordinate must be [0, " + width + "), received " + x + ".");
            }
            if (y < 0 || y >= length) {
                Preconditions.checkElementIndex(y, length, "Y coordinate must be [0, " + length + "), received " + y + ".");
            }
        }
        return y * width + x;
    }

    /**
     * Determines if the given {@code start} position has reached a wall object. This only applies when
     * {@code wallObject} is a wall-type object; for other object types, this method always returns {@code false}.
     * <p>
     * Logic is a direct refactor of the #377 client object reachability rules for wall objects.
     * </p>
     *
     * @param start The starting world position.
     * @param wallObject The wall object being tested.
     * @return {@code true} if {@code start} has reached {@code wallObject}; otherwise {@code false}.
     */
    public boolean reachedWall(Position start,  GameObject wallObject) {
        int startX = start.getLocalX(start);
        int startY = start.getLocalY(start);

        Position end = wallObject.getPosition();
        int endX = end.getLocalX(start);
        int endY = end.getLocalY(start);

        if (startX == endX && startY == endY) {
            return true;
        }
        if (wallObject.getObjectType() == ObjectType.STRAIGHT_WALL) {
            if (wallObject.getDirection() == ObjectDirection.WEST) {
                if (startX == endX - 1 && startY == endY) {
                    return true;
                } else if (startX == endX && startY == endY + 1 && (get(start) & SOUTH_BLOCKED) == 0) {
                    return true;
                }
                return startX == endX && startY == endY - 1 && (get(start) & NORTH_BLOCKED) == 0;
            } else if (wallObject.getDirection() == ObjectDirection.NORTH) {
                if (startX == endX && startY == endY + 1) {
                    return true;
                } else if (startX == endX - 1 && startY == endY && (get(start) & EAST_BLOCKED) == 0) {
                    return true;
                }
                return startX == endX + 1 && startY == endY && (get(start) & WEST_BLOCKED) == 0;
            } else if (wallObject.getDirection() == ObjectDirection.EAST) {
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & SOUTH_BLOCKED) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & NORTH_BLOCKED) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.SOUTH) {
                if (startX == endX && startY == endY - 1)
                    return true;
                if (startX == endX - 1 && startY == endY && (get(start) & EAST_BLOCKED) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & WEST_BLOCKED) == 0)
                    return true;
            }
        } else if (wallObject.getObjectType() == ObjectType.WALL_CORNER) {
            if (wallObject.getDirection() == ObjectDirection.WEST) {
                if (startX == endX - 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & WEST_BLOCKED) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & NORTH_BLOCKED) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.NORTH) {
                if (startX == endX - 1 && startY == endY && (get(start) & EAST_BLOCKED) == 0)
                    return true;
                if (startX == endX && startY == endY + 1)
                    return true;
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & NORTH_BLOCKED) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.EAST) {
                if (startX == endX - 1 && startY == endY && (get(start) & EAST_BLOCKED) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & SOUTH_BLOCKED) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY - 1)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.SOUTH) {
                if (startX == endX - 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & SOUTH_BLOCKED) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & WEST_BLOCKED) == 0)
                    return true;
                if (startX == endX && startY == endY - 1)
                    return true;
            }
        } else if (wallObject.getObjectType() == ObjectType.DIAGONAL_WALL) {
            if (startX == endX && startY == endY + 1 && (get(start) & CollisionFlag.WALL_SOUTH) == 0)
                return true;
            if (startX == endX && startY == endY - 1 && (get(start) & CollisionFlag.WALL_NORTH) == 0)
                return true;
            if (startX == endX - 1 && startY == endY && (get(start) & CollisionFlag.WALL_EAST) == 0)
                return true;
            if (startX == endX + 1 && startY == endY && (get(start) & CollisionFlag.WALL_WEST) == 0)
                return true;
        }
        return false;
    }

    /**
     * Determines if the given {@code start} position has reached a decorative object. Only applies when
     * {@code decorationObject} is a decoration-type object; otherwise this method returns {@code false}.
     * <p>
     * Logic is a direct refactor of the #377 client decoration reachability rules.
     * </p>
     *
     * @param start The starting world position.
     * @param decorationObject The decorative object being tested.
     * @return {@code true} if {@code start} has reached {@code decorationObject}; otherwise {@code false}.
     */
    public boolean reachedDecoration(Position start, GameObject decorationObject) {
        int startX = start.getLocalX(start);
        int startY = start.getLocalY(start);

        Position end = decorationObject.getPosition();
        int endX = end.getLocalX(start);
        int endY = end.getLocalY(start);

        if (startX == endX && startY == endY)
            return true;
        int objectType = decorationObject.getObjectType().getId();
        int objectRotation = decorationObject.getDirection().getId();
        if (objectType == 6 || objectType == 7) {
            if (objectType == 7)
                objectRotation = objectRotation + 2 & 3;
            if (objectRotation == 0) {
                if (startX == endX + 1 && startY == endY && (get(start) & CollisionFlag.WALL_WEST) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & CollisionFlag.WALL_NORTH) == 0)
                    return true;
            } else if (objectRotation == 1) {
                if (startX == endX - 1 && startY == endY && (get(start) & CollisionFlag.WALL_EAST) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & CollisionFlag.WALL_NORTH) == 0)
                    return true;
            } else if (objectRotation == 2) {
                if (startX == endX - 1 && startY == endY && (get(start) & CollisionFlag.WALL_EAST) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & CollisionFlag.WALL_SOUTH) == 0)
                    return true;
            } else if (objectRotation == 3) {
                if (startX == endX + 1 && startY == endY && (get(start) & CollisionFlag.WALL_WEST) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & CollisionFlag.WALL_SOUTH) == 0)
                    return true;
            }
        }
        if (objectType == 8) {
            if (startX == endX && startY == endY + 1 && (get(start) & CollisionFlag.WALL_SOUTH) == 0)
                return true;
            if (startX == endX && startY == endY - 1 && (get(start) & CollisionFlag.WALL_NORTH) == 0)
                return true;
            if (startX == endX - 1 && startY == endY && (get(start) & CollisionFlag.WALL_EAST) == 0)
                return true;
            if (startX == endX + 1 && startY == endY && (get(start) & CollisionFlag.WALL_WEST) == 0)
                return true;
        }
        return false;
    }

    /**
     * Determines if the given {@code start} position has reached a general object.
     * <p>
     * This method dispatches to one of:
     * </p>
     * <ul>
     *     <li>{@link #reachedFacingEntity(Position, Entity, int, int, OptionalInt)} for
     *     default/diagonal/ground decoration objects, using object size and direction flags.</li>
     *     <li>{@link #reachedWall(Position, GameObject)} for wall-like objects.</li>
     *     <li>{@link #reachedDecoration(Position, GameObject)} for other decorative objects.</li>
     * </ul>
     *
     * @param start The starting world position.
     * @param object The object being tested.
     * @return {@code true} if {@code start} has reached {@code object}; otherwise {@code false}.
     */
    public boolean reachedObject(Position start,GameObject object) {
        ObjectType objectType = object.getObjectType();
        if (objectType == ObjectType.DEFAULT ||
                objectType == ObjectType.DIAGONAL_DEFAULT ||
                objectType == ObjectType.GROUND_DECORATION) {
            GameObjectDefinition def = object.def();
            ObjectDirection objectDirection = object.getDirection();
            int sizeX;
            int sizeY;
            if (objectDirection == ObjectDirection.WEST ||
                    objectDirection == ObjectDirection.EAST) {
                sizeX = def.getSizeX();
                sizeY = def.getSizeY();
            } else {
                sizeX = def.getSizeY();
                sizeY = def.getSizeX();
            }
            int packedDirections = def.getDirection();
            if (object.getDirection() != ObjectDirection.WEST) {
                packedDirections = (packedDirections << objectDirection.getId() & 0xf) +
                        (packedDirections >> 4 - objectDirection.getId());
            }
            return sizeX != 0 && sizeY != 0 &&
                    reachedFacingEntity(start, object, sizeX, sizeY, OptionalInt.of(packedDirections));
        } else {
            int objectTypeId = object.getObjectType().getId();
            if ((objectTypeId < 5 || objectTypeId == 9 || objectTypeId == 10)) {
                return reachedWall(start, object);
            }
            if (objectTypeId < 10) {
                return reachedDecoration(start,  object);
            }
            return false;
        }
    }

    /**
     * Determines if the given {@code start} position has reached an entity occupying a rectangular area with optional
     * direction-based reach constraints.
     * <p>
     * This is the generalized "reach check" used for entities and objects with arbitrary width/length, and optional
     * directional reachability (via {@code packedDirections}). Logic is refactored from the #377 client.
     * </p>
     *
     * @param start The starting world position.
     * @param target The entity being tested.
     * @param sizeX The width (X size) of the entity in tiles.
     * @param sizeY The length (Y size) of the entity in tiles.
     * @param packedDirections Packed direction bits indicating which sides can be reached (objects only).
     * @return {@code true} if {@code start} has reached the entity according to these rules; otherwise {@code false}.
     */
    public boolean reachedFacingEntity(Position start,
                                       Entity target,
                                       int sizeX,
                                       int sizeY,
                                       OptionalInt packedDirections) {

        int packed = packedDirections.orElse(0);

        int startX = start.getLocalX(start);
        int startY = start.getLocalY(start);

        Position end = target.getPosition();
        int endX = end.getLocalX(start);
        int endY = end.getLocalY(start);

        int radiusX = (endX + sizeX) - 1;
        int radiusY = (endY + sizeY) - 1;
        if (startX >= endX && startX <= radiusX && startY >= endY && startY <= radiusY)
            return true;
        return startX == endX - 1 && startY >= endY && startY <= radiusY && (get(start) & CollisionFlag.WALL_EAST) == 0 && (packed & 8) == 0
                || startX == radiusX + 1 && startY >= endY && startY <= radiusY && (get(start) & CollisionFlag.WALL_WEST) == 0 && (packed & 2) == 0
                || startY == endY - 1 && startX >= endX && startX <= radiusX && (get(start) & CollisionFlag.WALL_NORTH) == 0 && (packed & 4) == 0
                || startY == radiusY + 1 && startX >= endX && startX <= radiusX && (get(start) & CollisionFlag.WALL_SOUTH) == 0 && (packed & 1) == 0;
    }

    /**
     * Creates a thread-safe deep copy of this matrix. Only the flags are copied; the overlap counters are only needed
     * to apply changes, which a read-only snapshot never does.
     */
    public CollisionMatrix copy() {
        return new CollisionMatrix(width, length, Arrays.copyOf(matrix, matrix.length));
    }
}
