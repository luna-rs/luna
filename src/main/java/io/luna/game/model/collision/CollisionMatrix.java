package io.luna.game.model.collision;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
 * Each cell in the matrix encodes a set of {@link CollisionFlag} values in a packed {@code short}, describing which
 * movement and projectile directions are blocked for the tile at that local (x, y) coordinate.
 * </p>
 *
 * @author Major
 * @author lare96
 */
public final class CollisionMatrix {

    /**
     * Bit pattern representing a fully open tile (no collision flags set).
     */
    private static final short ALL_ALLOWED = 0b00000000_00000000;

    /**
     * Bit pattern representing a fully blocked tile (all movement and projectile flags set).
     */
    private static final short ALL_BLOCKED = (short) 0b11111111_11111111;

    /**
     * Bit pattern representing a tile where mobs are blocked but projectiles may still pass.
     */
    private static final short ALL_MOBS_BLOCKED = (short) 0b11111111_00000000;

    /**
     * Creates an array of {@link CollisionMatrix} instances, each with identical width and length.
     *
     * @param count The number of matrices to create.
     * @param width The width (X dimension) of each matrix.
     * @param length The length (Y dimension) of each matrix.
     * @return A new array of {@code count} {@link CollisionMatrix} objects.
     */
    public static CollisionMatrix[] createMatrices(int count, int width, int length) {
        CollisionMatrix[] matrices = new CollisionMatrix[count];
        Arrays.setAll(matrices, index -> new CollisionMatrix(width, length));
        return matrices;
    }

    /**
     * The length (Y dimension) of this matrix.
     */
    private final int length;

    /**
     * The underlying collision data, as a flat array of packed {@code short} flags.
     */
    private final short[] matrix;

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
        matrix = new short[width * length];
    }

    /**
     * Creates a new {@link CollisionMatrix} with the given dimensions and matrix.
     *
     * @param width The width (X dimension) of the matrix.
     * @param length The length (Y dimension) of the matrix.
     * @param matrix The underlying collision data, as a flat array of packed {@code short} flags.
     */
   private CollisionMatrix(int width, int length, short[] matrix) {
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
    public boolean all(int x, int y, CollisionFlag... flags) {
        for (CollisionFlag flag : flags) {
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
    public boolean any(int x, int y, CollisionFlag... flags) {
        for (CollisionFlag flag : flags) {
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
    void clear(int x, int y, CollisionFlag flag) {
        set(x, y, (short) (matrix[indexOf(x, y)] & ~flag.asShort()));
    }

    /**
     * Sets (ORs) the specified {@link CollisionFlag} for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to set.
     */
    void flag(int x, int y, CollisionFlag flag) {
        matrix[indexOf(x, y)] |= flag.asShort();
    }

    /**
     * Returns whether the specified {@link CollisionFlag} is set for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to test.
     * @return {@code true} if the flag is set; otherwise {@code false}.
     */
    public boolean flagged(int x, int y, CollisionFlag flag) {
        return (get(x, y) & flag.asShort()) != 0;
    }

    /**
     * Retrieves the packed collision value for the tile at (x, y).
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @return The packed 16-bit collision value for that tile.
     */
    public int get(int x, int y) {
        return matrix[indexOf(x, y)] & 0xFFFF;
    }

    /**
     * Retrieves the packed collision value for the tile at the given absolute position.
     * <p>
     * The supplied position is converted to local chunk coordinates using {@link Chunk#SIZE}.
     * </p>
     *
     * @param position The absolute world position.
     * @return The packed 16-bit collision value for that tile.
     */
    public int get(Position position) {
        return matrix[indexOf(position.getX() % Chunk.SIZE, position.getY() % Chunk.SIZE)] & 0xFFFF;
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
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                reset(x, y);
            }
        }
    }

    /**
     * Replaces all flags for the tile at (x, y) with the supplied {@link CollisionFlag}.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param flag The collision flag to set (overwriting any existing flags).
     */
    void set(int x, int y, CollisionFlag flag) {
        set(x, y, flag.asShort());
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
     * This method interprets directional collision flags using the same semantics as the original RS2 client,
     * mapping the entity's approach direction to the appropriate directional flag(s) that would prevent movement.
     * </p>
     *
     * @param x The local X coordinate of the tile being entered.
     * @param y The local Y coordinate of the tile being entered.
     * @param entity The entity type (e.g. player, NPC, projectile).
     * @param direction The movement direction from the previous tile into this tile.
     * @return {@code true} if the tile is blocked when approached from {@code direction}; otherwise {@code false}.
     */
    public boolean untraversable(int x, int y, EntityType entity, Direction direction) {
        ImmutableList<CollisionFlag> flags = CollisionFlag.forType(entity);
        int northwest = 0, north = 1, northeast = 2, west = 3, east = 4, southwest = 5, south = 6, southeast = 7;

        switch (direction) {
            case NORTH_WEST:
                return flagged(x, y, flags.get(southeast)) || flagged(x, y, flags.get(south)) ||
                        flagged(x, y, flags.get(east));
            case NORTH:
                return flagged(x, y, flags.get(south));
            case NORTH_EAST:
                return flagged(x, y, flags.get(southwest)) || flagged(x, y, flags.get(south)) ||
                        flagged(x, y, flags.get(west));
            case EAST:
                return flagged(x, y, flags.get(west));
            case SOUTH_EAST:
                return flagged(x, y, flags.get(northwest)) || flagged(x, y, flags.get(north)) ||
                        flagged(x, y, flags.get(west));
            case SOUTH:
                return flagged(x, y, flags.get(north));
            case SOUTH_WEST:
                return flagged(x, y, flags.get(northeast)) || flagged(x, y, flags.get(north)) ||
                        flagged(x, y, flags.get(east));
            case WEST:
                return flagged(x, y, flags.get(east));
            default:
                throw new IllegalArgumentException("Unrecognised direction " + direction + ".");
        }
    }

    /**
     * Returns whether the tile at (x, y) is blocked for the given {@link EntityType}, regardless of direction. This
     * checks all directional flags associated with the entity type and reports {@code true} if any are set.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param entity The entity type to test collision for.
     * @return {@code true} if the tile is blocked for that entity type; otherwise {@code false}.
     */
    public boolean isBlocked(int x, int y, EntityType entity) {
        ImmutableList<CollisionFlag> entityFlags = CollisionFlag.forType(entity);
        for (CollisionFlag flag : entityFlags) {
            if (flagged(x, y, flag)) {
                return true;
            }
        }
        return false;
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
        Preconditions.checkElementIndex(x, width, "X coordinate must be [0, " + width + "), received " + x + ".");
        Preconditions.checkElementIndex(y, length, "Y coordinate must be [0, " + length + "), received " + y + ".");
        return y * width + x;
    }

    /**
     * Writes a raw packed value into the tile at (x, y), overwriting any existing flags.
     *
     * @param x The local X coordinate.
     * @param y The local Y coordinate.
     * @param value The packed 16-bit collision value to set.
     */
    private void set(int x, int y, short value) {
        matrix[indexOf(x, y)] = value;
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
                } else if (startX == endX && startY == endY + 1 && (get(start) & 0x1280120) == 0) {
                    return true;
                }
                return startX == endX && startY == endY - 1 && (get(start) & 0x1280102) == 0;
            } else if (wallObject.getDirection() == ObjectDirection.NORTH) {
                if (startX == endX && startY == endY + 1) {
                    return true;
                } else if (startX == endX - 1 && startY == endY && (get(start) & 0x1280108) == 0) {
                    return true;
                }
                return startX == endX + 1 && startY == endY && (get(start) & 0x1280180) == 0;
            } else if (wallObject.getDirection() == ObjectDirection.EAST) {
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & 0x1280120) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & 0x1280102) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.SOUTH) {
                if (startX == endX && startY == endY - 1)
                    return true;
                if (startX == endX - 1 && startY == endY && (get(start) & 0x1280108) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & 0x1280180) == 0)
                    return true;
            }
        } else if (wallObject.getObjectType() == ObjectType.WALL_CORNER) {
            if (wallObject.getDirection() == ObjectDirection.WEST) {
                if (startX == endX - 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & 0x1280180) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & 0x1280102) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.NORTH) {
                if (startX == endX - 1 && startY == endY && (get(start) & 0x1280108) == 0)
                    return true;
                if (startX == endX && startY == endY + 1)
                    return true;
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & 0x1280102) == 0)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.EAST) {
                if (startX == endX - 1 && startY == endY && (get(start) & 0x1280108) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & 0x1280120) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY - 1)
                    return true;
            } else if (wallObject.getDirection() == ObjectDirection.SOUTH) {
                if (startX == endX - 1 && startY == endY)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & 0x1280120) == 0)
                    return true;
                if (startX == endX + 1 && startY == endY && (get(start) & 0x1280180) == 0)
                    return true;
                if (startX == endX && startY == endY - 1)
                    return true;
            }
        } else if (wallObject.getObjectType() == ObjectType.DIAGONAL_WALL) {
            if (startX == endX && startY == endY + 1 && (get(start) & 0x20) == 0)
                return true;
            if (startX == endX && startY == endY - 1 && (get(start) & 2) == 0)
                return true;
            if (startX == endX - 1 && startY == endY && (get(start) & 8) == 0)
                return true;
            if (startX == endX + 1 && startY == endY && (get(start) & 0x80) == 0)
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
                if (startX == endX + 1 && startY == endY && (get(start) & 0x80) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & 2) == 0)
                    return true;
            } else if (objectRotation == 1) {
                if (startX == endX - 1 && startY == endY && (get(start) & 8) == 0)
                    return true;
                if (startX == endX && startY == endY - 1 && (get(start) & 2) == 0)
                    return true;
            } else if (objectRotation == 2) {
                if (startX == endX - 1 && startY == endY && (get(start) & 8) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & 0x20) == 0)
                    return true;
            } else if (objectRotation == 3) {
                if (startX == endX + 1 && startY == endY && (get(start) & 0x80) == 0)
                    return true;
                if (startX == endX && startY == endY + 1 && (get(start) & 0x20) == 0)
                    return true;
            }
        }
        if (objectType == 8) {
            if (startX == endX && startY == endY + 1 && (get(start) & 0x20) == 0)
                return true;
            if (startX == endX && startY == endY - 1 && (get(start) & 2) == 0)
                return true;
            if (startX == endX - 1 && startY == endY && (get(start) & 8) == 0)
                return true;
            if (startX == endX + 1 && startY == endY && (get(start) & 0x80) == 0)
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
            GameObjectDefinition def = object.getDefinition();
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
            if ((objectTypeId < 5 || objectTypeId == 10)) {
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
     * This is the generalized “reach check” used for entities and objects with arbitrary width/length, and optional
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
        return startX == endX - 1 && startY >= endY && startY <= radiusY && (get(start) & 8) == 0 && (packed & 8) == 0
                || startX == radiusX + 1 && startY >= endY && startY <= radiusY && (get(start) & 0x80) == 0 && (packed & 2) == 0
                || startY == endY - 1 && startX >= endX && startX <= radiusX && (get(start) & 2) == 0 && (packed & 4) == 0
                || startY == radiusY + 1 && startX >= endX && startX <= radiusX && (get(start) & 0x20) == 0 && (packed & 1) == 0;
    }

    /**
     * Creates a thread-safe deep copy of this matrix.
     */
    public CollisionMatrix copy() {
        return new CollisionMatrix(width, length, Arrays.copyOf(matrix, matrix.length));
    }
}
