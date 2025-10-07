package io.luna.game.model.collision;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
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
 * A 2-dimensional adjacency matrix containing tile collision data.
 *
 * @author Major
 * @author lare96
 */
public final class CollisionMatrix {

    /**
     * Indicates that all types of traversal are allowed.
     */
    private static final short ALL_ALLOWED = 0b00000000_00000000;

    /**
     * Indicates that no types of traversal are allowed.
     */
    private static final short ALL_BLOCKED = (short) 0b11111111_11111111;

    /**
     * Indicates that projectiles may traverse this tile, but mobs may not.
     */
    private static final short ALL_MOBS_BLOCKED = (short) 0b11111111_00000000;

    /**
     * Creates an array of CollisionMatrix objects, all of the specified width and length.
     *
     * @param count The length of the array to create.
     * @param width The width of each CollisionMatrix.
     * @param length The length of each CollisionMatrix.
     * @return The array of CollisionMatrix objects.
     */
    public static CollisionMatrix[] createMatrices(int count, int width, int length) {
        CollisionMatrix[] matrices = new CollisionMatrix[count];
        Arrays.setAll(matrices, index -> new CollisionMatrix(width, length));
        return matrices;
    }

    /**
     * The length of the matrix.
     */
    private final int length;

    /**
     * The collision matrix, as a {@code short} array.
     */
    private final short[] matrix;

    /**
     * The width of the matrix.
     */
    private final int width;

    /**
     * Creates the CollisionMatrix.
     *
     * @param width The width of the matrix.
     * @param length The length of the matrix.
     */
    public CollisionMatrix(int width, int length) {
        this.width = width;
        this.length = length;
        matrix = new short[width * length];
    }

    /**
     * Returns whether or not <strong>all</strong> of the specified {@link CollisionFlag}s are set for the specified
     * coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flags The CollisionFlags.
     * @return {@code true} iff all of the CollisionFlags are set.
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
     * Returns whether or not <strong>any</strong> of the specified {@link CollisionFlag}s are set for the specified
     * coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flags The CollisionFlags.
     * @return {@code true} iff any of the CollisionFlags are set.
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
     * Completely blocks the tile at the specified coordinate pair, while optionally allowing projectiles
     * to pass through.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param impenetrable If projectiles should be permitted to traverse this tile.
     */
    public void block(int x, int y, boolean impenetrable) {
        set(x, y, impenetrable ? ALL_BLOCKED : ALL_MOBS_BLOCKED);
    }

    /**
     * Completely blocks the tile at the specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void block(int x, int y) {
        block(x, y, true);
    }

    /**
     * Clears (i.e. sets to {@code false}) the value of the specified {@link CollisionFlag} for the specified
     * coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flag The CollisionFlag.
     */
    public void clear(int x, int y, CollisionFlag flag) {
        set(x, y, (short) (matrix[indexOf(x, y)] & ~flag.asShort()));
    }

    /**
     * Adds an additional {@link CollisionFlag} for the specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flag The CollisionFlag.
     */
    public void flag(int x, int y, CollisionFlag flag) {
        matrix[indexOf(x, y)] |= flag.asShort();
    }

    /**
     * Returns whether or not the specified {@link CollisionFlag} is set for the specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flag The CollisionFlag.
     * @return {@code true} iff the CollisionFlag is set.
     */
    public boolean flagged(int x, int y, CollisionFlag flag) {
        return (get(x, y) & flag.asShort()) != 0;
    }

    /**
     * Gets the value of the specified tile.
     *
     * @param x The x coordinate of the tile.
     * @param y The y coordinate of the tile.
     * @return The value.
     */
    public int get(int x, int y) {
        return matrix[indexOf(x, y)] & 0xFFFF;
    }

    /**
     * Gets the value of the specified tile.
     *
     * @param position The absolute position.
     * @return The value.
     */
    public int get(Position position) {
        return matrix[indexOf(position.getX() % Chunk.SIZE, position.getY() % Chunk.SIZE)] & 0xFFFF;
    }

    /**
     * Resets the cell of the specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public void reset(int x, int y) {
        set(x, y, ALL_ALLOWED);
    }

    /**
     * Resets all cells in this matrix.
     */
    public void reset() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < width; y++) {
                reset(x, y);
            }
        }
    }

    /**
     * Sets (i.e. sets to {@code true}) the value of the specified {@link CollisionFlag} for the specified coordinate
     * pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param flag The CollisionFlag.
     */
    public void set(int x, int y, CollisionFlag flag) {
        set(x, y, flag.asShort());
    }

    /**
     * Replaces the data in this collision matrix with the data in {@code other}.
     *
     * @param other The matrix to replace this one with.
     */
    public void replace(CollisionMatrix other) {
        System.arraycopy(other.matrix, 0, matrix, 0, matrix.length);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("width", width).add("length", length)
                .add("matrix", Arrays.toString(matrix)).toString();
    }

    /**
     * Returns whether or not an Entity of the specified {@link EntityType type} cannot traverse the tile at the
     * specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param entity The {@link EntityType}.
     * @param direction The {@link Direction} the Entity is approaching from.
     * @return {@code true} iff the tile at the specified coordinate pair is not traversable.
     */
    public boolean untraversable(int x, int y, EntityType entity, Direction direction) {
        CollisionFlag[] flags = CollisionFlag.forType(entity);
        int northwest = 0, north = 1, northeast = 2, west = 3, east = 4, southwest = 5, south = 6, southeast = 7;

        switch (direction) {
            case NORTH_WEST:
                return flagged(x, y, flags[southeast]) || flagged(x, y, flags[south]) || flagged(x, y, flags[east]);
            case NORTH:
                return flagged(x, y, flags[south]);
            case NORTH_EAST:
                return flagged(x, y, flags[southwest]) || flagged(x, y, flags[south]) || flagged(x, y, flags[west]);
            case EAST:
                return flagged(x, y, flags[west]);
            case SOUTH_EAST:
                return flagged(x, y, flags[northwest]) || flagged(x, y, flags[north]) || flagged(x, y, flags[west]);
            case SOUTH:
                return flagged(x, y, flags[north]);
            case SOUTH_WEST:
                return flagged(x, y, flags[northeast]) || flagged(x, y, flags[north]) || flagged(x, y, flags[east]);
            case WEST:
                return flagged(x, y, flags[east]);
            default:
                throw new IllegalArgumentException("Unrecognised direction " + direction + ".");
        }
    }

    /**
     * Gets the index in the matrix for the specified coordinate pair.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return The index.
     * @throws ArrayIndexOutOfBoundsException If the specified coordinate pair does not fit in this matrix.
     */
    private int indexOf(int x, int y) {
        Preconditions.checkElementIndex(x, width, "X coordinate must be [0, " + width + "), received " + x + ".");
        Preconditions.checkElementIndex(y, length, "Y coordinate must be [0, " + length + "), received " + y + ".");
        return y * width + x;
    }

    /**
     * Sets the appropriate index for the specified coordinate pair to the specified value.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param value The value.
     */
    private void set(int x, int y, short value) {
        matrix[indexOf(x, y)] = value;
    }

    /**
     * If position {@code start} has reached a {@code wallObject}. Will always return {@code false} if the object is
     * not a wall.
     *
     * <p><p><strong>Refactored from the #377 client.</strong>
     *
     * @param start The start position.
     * @param lastRegion The position to get local coordinates from.
     * @param wallObject The wall to determine if reached.
     * @return {@code true} if the position has reached the wall, {@code false} otherwise.
     */
    public boolean reachedWall(Position start, Position lastRegion, GameObject wallObject) {
        int startX = start.getLocalX(lastRegion);
        int startY = start.getLocalY(lastRegion);

        Position end = wallObject.getPosition();
        int endX = end.getLocalX(lastRegion); // end
        int endY = end.getLocalY(lastRegion); // end

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
     * If position {@code start} has reached a {@code decorationObject}. Will always return {@code false} if the
     * object is not a decoration.
     *
     * <p><p><strong>Refactored from the #377 client.</strong>
     *
     * @param start The start position.
     * @param lastRegion The position to get local coordinates from.
     * @param decorationObject The decoration to determine if reached.
     * @return {@code true} if the position has reached the decoration, {@code false} otherwise.
     */
    public boolean reachedDecoration(Position start, Position lastRegion, GameObject decorationObject) {
        int startX = start.getLocalX(lastRegion);
        int startY = start.getLocalY(lastRegion);

        Position end = decorationObject.getPosition();
        int endX = end.getLocalX(end);
        int endY = end.getLocalY(end);

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
     * If position {@code start} has reached {@code object}.
     *
     * <p><p><strong>Refactored from the #377 client.</strong>
     *
     * @param start The start position.
     * @param lastRegion The position to get local coordinates from.
     * @param object The object to determine if reached.
     * @return {@code true} if the position has reached the object, {@code false} otherwise.
     */
    public boolean reachedObject(Position start, Position lastRegion, GameObject object) {
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
            return sizeX != 0 && sizeY != 0 && reachedFacingEntity(start, lastRegion, object, sizeX, sizeY, OptionalInt.of(packedDirections));
        } else {
            int objectTypeId = object.getObjectType().getId();
            if ((objectTypeId < 5 || objectTypeId == 10)) {
                return reachedWall(start, lastRegion, object);
            }
            if (objectTypeId < 10) {
                return reachedDecoration(start, lastRegion, object);
            }
            return false;
        }
    }

    /**
     * Determines if position {@code start} has reached {@code target}, based on {@code sizeX}, {@code sizeY}, and
     * {@code directionHash}.
     *
     * <p><p><strong>Refactored from the #377 client.</strong>
     *
     * @param start The start position.
     * @param lastRegion The position to get local coordinates from.
     * @param target The entity to determine if reached.
     * @param sizeX The width of the entity.
     * @param sizeY The length of the entity.
     * @param packedDirections Packed value for which directions the entity can be reached from. Only used for objects.
     * @return {@code true} if the player has reached the entity, {@code false} otherwise.
     */
    public boolean reachedFacingEntity(Position start, Position lastRegion, Entity target, int sizeX, int sizeY, OptionalInt packedDirections) {
        int packed = packedDirections.orElse(0);

        int startX = start.getLocalX(lastRegion);
        int startY = start.getLocalY(lastRegion);

        Position end = target.getPosition();
        int endX = end.getLocalX(lastRegion);
        int endY = end.getLocalY(lastRegion);

        int radiusX = (endX + sizeX) - 1;
        int radiusY = (endY + sizeY) - 1;
        if (startX >= endX && startX <= radiusX && startY >= endY && startY <= radiusY)
            return true;
        return startX == endX - 1 && startY >= endY && startY <= radiusY && (get(start) & 8) == 0 && (packed & 8) == 0
                || startX == radiusX + 1 && startY >= endY && startY <= radiusY && (get(start) & 0x80) == 0 && (packed & 2) == 0
                || startY == endY - 1 && startX >= endX && startX <= radiusX && (get(start) & 2) == 0 && (packed & 4) == 0
                || startY == radiusY + 1 && startX >= endX && startX <= radiusX && (get(start) & 0x20) == 0 && (packed & 1) == 0;
    }
}