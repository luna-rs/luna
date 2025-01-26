package io.luna.game.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Range;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.mob.WalkingQueue.Step;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link Location} made up of a single tile on the Runescape map.
 *
 * @author lare96
 */
public final class Position implements Location {

    /**
     * The maximum amount of tiles a player can view.
     */
    public static final int VIEWING_DISTANCE = 15;

    /**
     * A {@link Range} of all height levels.
     */
    public static final Range<Integer> HEIGHT_LEVELS = Range.closedOpen(0, 4);

    /**
     * The x coordinate.
     */
    private final int x;

    /**
     * The y coordinate.
     */
    private final int y;

    /**
     * The z coordinate.
     */
    private final int z;

    /**
     * Creates a new {@link Position}, where all {@code x, y, and z} are non-negative.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @throws IllegalArgumentException If either x, y, or z are negative.
     * @throws IllegalArgumentException If z is not in the range [0-3], inclusively.
     */
    public Position(int x, int y, int z) {
        checkArgument(x >= 0, "x < 0");
        checkArgument(y >= 0, "y < 0");
        checkArgument(HEIGHT_LEVELS.contains(z), z + " (z >= 0 && z < 4)");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new {@link Position} with {@code 0} as the z coordinate.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public Position(int x, int y) {
        this(x, y, 0);
    }

    @Override
    public boolean contains(Position position) {
        return position.equals(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", x).add("y", y).add("z", z).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Position) {
            Position other = (Position) obj;
            return x == other.x && y == other.y && z == other.z;
        }
        return false;
    }

    /**
     * Gets the euclidean distance between this position and another position. Only x and y are considered
     * (i.e. 2 dimensions).
     *
     * @param other The other position.
     * @return The distance.
     */
    public int getEuclideanDistance(Position other) {
        int deltaX = getX() - other.getX();
        int deltaY = getY() - other.getY();
        return (int) Math.ceil(Math.sqrt(deltaX * deltaX + deltaY * deltaY));
    }

    /**
     * Determines if this position is within the given distance of another position.
     *
     * @param other The position to compare.
     * @param distance The distance from {@code other} to compare. This parameter must be non-negative.
     * @return {@code true} if {@code other} is within {@code distance}, and on the same plane.
     * @throws IllegalArgumentException If distance < 0.
     */
    public boolean isWithinDistance(Position other, int distance) {
        checkArgument(distance >= 0, "Distance must be non-negative.");

        if (z != other.z) { // check if position is on the same plane.
            return false;
        }
        int deltaX = Math.abs(other.x - x);
        int deltaY = Math.abs(other.y - y);
        return deltaX <= distance && deltaY <= distance;
    }

    /**
     * Determines if this position is viewable from another position.
     *
     * @param other The position to compare.
     * @return {@code true} if {@code other} is within {@link  #VIEWING_DISTANCE}.
     */
    public boolean isViewable(Position other) {
        return isWithinDistance(other, VIEWING_DISTANCE);
    }

    /**
     * Forwards to {@link #isViewable(Position)} with {@link Entity#getPosition()} as the argument.
     */
    public boolean isViewable(Entity other) {
        return isViewable(other.getPosition());
    }

    /**
     * Returns the longest distance between this position and {@code other}.
     *
     * @param other The other position.
     * @return The longest distance between this and {@code other}.
     */
    public int computeLongestDistance(Position other) {
        int deltaX = Math.abs(other.x - x);
        int deltaY = Math.abs(other.y - y);
        return Math.max(deltaX, deltaY);
    }

    /**
     * Returns a new position translated by the specified amounts.
     *
     * @param amountX The x amount.
     * @param amountY The y amount
     * @param amountZ The z amount.
     * @return The translated position.
     */
    public Position translate(int amountX, int amountY, int amountZ) {
        if (amountX == 0 && amountY == 0 && amountZ == 0) {
            return this;
        }
        return new Position(x + amountX, y + amountY, z + amountZ);
    }

    /**
     * Returns a new position translated by the specified amounts. The z coordinate will remain
     * unchanged.
     *
     * @param amountX The x amount.
     * @param amountY The y amount
     * @return The translated position.
     */
    public Position translate(int amountX, int amountY) {
        return translate(amountX, amountY, 0);
    }

    /**
     * Returns a new position translated by the specified amount. The z coordinate will remain
     * unchanged.
     *
     * @param amount The amount.
     * @param direction The direction.
     * @return The translated position.
     */
    public Position translate(int amount, Direction direction) {
        Step translation = direction.getTranslation();
        return translate(amount * translation.getX(), amount * translation.getY(), 0);
    }

    /**
     * Returns a new {@link Position} identical to this one in values, except with {@code newZ}.
     *
     * @param newZ The new {@code z}.
     * @return
     */
    public Position setZ(int newZ) {
        return new Position(x, y, newZ);
    }

    /**
     * Gets the central x coordinate of this position's chunk.
     */
    public int getChunkX() {
        return x / 8;
    }

    /**
     * Gets the central y coordinate of this position's chunk.
     */
    public int getChunkY() {
        return y / 8;
    }

    /**
     * Returns the formatted x coordinate of this position's chunk.
     *
     * @return The bottom-left chunk x.
     */
    public int getBottomLeftChunkX() {
        return x / 8 - 6;
    }

    /**
     * Returns the formatted y coordinate of this position's chunk.
     *
     * @return The bottom-left chunk y.
     */
    public int getBottomLeftChunkY() {
        return y / 8 - 6;
    }

    /**
     * Returns the local x coordinate within the chunk of {@code base}.
     *
     * @param base The base chunk.
     */
    public int getLocalX(Position base) {
        return x - base.getBottomLeftChunkX() * 8;
    }

    /**
     * Returns the local y coordinate within the chunk of {@code base}.
     *
     * @param base The base chunk.
     */
    public int getLocalY(Position base) {
        return y - base.getBottomLeftChunkY() * 8;
    }

    /**
     * Returns the {@link Chunk} that this position is contained in.
     *
     * @return The chunk.
     */
    public Chunk getChunk() {
        return new Chunk(this);
    }

    /**
     * Returns the {@link Region} that this position is contained in.
     *
     * @return The region.
     */
    public Region getRegion() {
        return new Region(this);
    }

    /**
     * @return The x coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The y coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The z coordinate.
     */
    public int getZ() {
        return z;
    }
}
