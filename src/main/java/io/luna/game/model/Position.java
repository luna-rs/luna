package io.luna.game.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Range;
import io.luna.game.model.chunk.Chunk;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a single tile coordinate on the RuneScape map.
 * <p>
 * A {@code Position} is immutable and uniquely defined by three components:
 * <ul>
 *     <li>{@code x} – The horizontal tile coordinate.</li>
 *     <li>{@code y} – The vertical tile coordinate.</li>
 *     <li>{@code z} – The height level (plane), restricted to {@code 0..3}.</li>
 * </ul>
 *
 * <h2>Partitioning</h2>
 * Positions can be mapped into higher-level partitions:
 * <ul>
 *     <li>{@link Chunk} – 8x8 tiles.</li>
 *     <li>{@link Region} – 64x64 tiles.</li>
 * </ul>
 *
 * @author lare96
 * @see Locatable
 * @see Chunk
 * @see Region
 */
public final class Position implements Locatable {

    /**
     * The maximum number of tiles a player can typically view in any direction.
     */
    public static final int VIEWING_DISTANCE = 15;

    /**
     * Valid height levels for positions.
     */
    public static final Range<Integer> HEIGHT_LEVELS = Range.closedOpen(0, 4);

    /**
     * The x tile coordinate.
     */
    private int x;

    /**
     * The y tile coordinate.
     */
    private int y;

    /**
     * The height level (plane).
     */
    private int z;

    /**
     * Creates a new {@link Position}.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The height level (0-3).
     * @throws IllegalArgumentException If {@code z} is not within {@link #HEIGHT_LEVELS}.
     */
    public Position(int x, int y, int z) {
        checkArgument(HEIGHT_LEVELS.contains(z), z + " (z >= 0 && z < 4)");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Creates a new {@link Position} on height level {@code 0}.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    public Position(int x, int y) {
        this(x, y, 0);
    }

    /**
     * A position contains only itself.
     *
     * @param position The position to test.
     * @return {@code true} if the positions are equal.
     */
    @Override
    public boolean contains(Position position) {
        return position.equals(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("x", x)
                .add("y", y)
                .add("z", z)
                .toString();
    }

    @Override
    public int hashCode() {
        return (z << 28) | ((x & 0x3FFF) << 14) | (y & 0x3FFF);
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
     * The absolute location of a position is itself.
     *
     * @return This position.
     */
    @Override
    public Position abs() {
        return this;
    }

    /**
     * Determines whether this position lies within an axis-aligned distance of {@code other}.
     * <p>
     * This check requires both {@code |dx| <= distance} and {@code |dy| <= distance}, and also
     * requires the positions to be on the same height level.
     *
     * @param other The position to compare against.
     * @param distance The maximum allowed distance (non-negative).
     * @return {@code true} if {@code other} is within range and on the same plane.
     * @throws IllegalArgumentException If {@code distance < 0}.
     */
    public boolean isWithinDistance(Position other, int distance) {
        checkArgument(distance >= 0, "Distance must be non-negative.");

        if (z != other.z) {
            return false;
        }
        int deltaX = Math.abs(other.x - x);
        int deltaY = Math.abs(other.y - y);
        return deltaX <= distance && deltaY <= distance;
    }

    /**
     * Determines whether {@code other} is within {@link #VIEWING_DISTANCE} of this position.
     *
     * @param other The other position.
     * @return {@code true} if viewable.
     */
    public boolean isViewable(Position other) {
        return isWithinDistance(other, VIEWING_DISTANCE);
    }

    /**
     * Forwards to {@link #isViewable(Position)} using {@link Entity#getPosition()}.
     *
     * @param other The entity to test.
     * @return {@code true} if the entity's position is viewable.
     */
    public boolean isViewable(Entity other) {
        return isViewable(other.getPosition());
    }

    /**
     * Computes the longest axis distance between this position and {@code other}.
     * <p>
     * This is equivalent to Chebyshev distance: {@code max(|dx|, |dy|)}.
     *
     * @param other The other position.
     * @return The longest distance.
     */
    public int computeLongestDistance(Position other) {
        int deltaX = Math.abs(other.x - x);
        int deltaY = Math.abs(other.y - y);
        return Math.max(deltaX, deltaY);
    }

    /**
     * Allocation safe version of translate. The idea here is that we don't allocate a new object by having the caller
     * supply the object to store the result.
     *
     * @param amountX   amount to translate
     * @param amountY   amount to translate
     * @param dest      Position object to store result in
     * @return          Outputs the result, the same object as dest parameter
     */
    public Position translate(int amountX, int amountY, Position dest) {
        return translate(amountX, amountY, 0, dest);
    }

    /**
     * Allocation safe version of translate. The idea here is that we don't allocate a new object by having the caller
     * supply the object to store the result.
     *
     * @param amount The number of tiles to move.
     * @param direction The direction.
     * @param dest The position to store the result.
     * @return The translated position.
     */
    public Position translate(int amount, Direction direction, Position dest) {
        return translate(amount * direction.getTranslateX(), amount * direction.getTranslateY(), 0, dest);
    }

    /**
     * Allocation safe version of translate. The idea here is that we don't allocate a new object by having the caller
     * supply the object to store the result.
     *
     * @param amountX   amount to translate
     * @param amountY   amount to translate
     * @param amountZ   amount to translate
     * @param dest      Position object to store result in
     * @return          Outputs the result, the same object as dest parameter
     */
    public Position translate(int amountX, int amountY, int amountZ, Position dest) {
        if (amountX == 0 && amountY == 0 && amountZ == 0) {
            return this;
        }

        dest.x = x + amountX;
        dest.y = y + amountY;
        dest.z = z + amountZ;
        return dest;
    }

    /**
     * Returns a new position translated by the specified amounts.
     * <p>
     * If no translation is applied, this instance is returned.
     *
     * @param amountX The x delta.
     * @param amountY The y delta.
     * @param amountZ The z delta.
     * @return The translated position.
     */
    public Position translate(int amountX, int amountY, int amountZ) {
        if (amountX == 0 && amountY == 0 && amountZ == 0) {
            return this;
        }
        return new Position(x + amountX, y + amountY, z + amountZ);
    }

    /**
     * Returns a new position translated by the specified X and Y amounts on the same plane.
     *
     * @param amountX The x delta.
     * @param amountY The y delta.
     * @return The translated position.
     */
    public Position translate(int amountX, int amountY) {
        return translate(amountX, amountY, 0);
    }

    /**
     * Returns a new position translated in a {@link Direction}.
     *
     * @param amount The number of tiles to move.
     * @param direction The direction.
     * @return The translated position.
     */
    public Position translate(int amount, Direction direction) {
        return translate(amount * direction.getTranslateX(), amount * direction.getTranslateY(), 0);
    }

    /**
     * Returns the chunk X coordinate (8x8 tile partition) for this position.
     *
     * @return The chunk X coordinate.
     */
    public int getChunkX() {
        return x / 8;
    }

    /**
     * Returns the chunk Y coordinate (8x8 tile partition) for this position.
     *
     * @return The chunk Y coordinate.
     */
    public int getChunkY() {
        return y / 8;
    }

    /**
     * Returns the bottom-left chunk X coordinate of the local player view (13x13 chunks centered on player).
     *
     * @return The bottom-left view chunk X.
     */
    public int getBottomLeftChunkX() {
        return x / 8 - 6;
    }

    /**
     * Returns the bottom-left chunk Y coordinate of the local player view (13x13 chunks centered on player).
     *
     * @return The bottom-left view chunk Y.
     */
    public int getBottomLeftChunkY() {
        return y / 8 - 6;
    }

    /**
     * Returns the local X coordinate within the view area described by {@code base}.
     *
     * @param base The base position (typically the player position).
     * @return The local X coordinate.
     */
    public int getLocalX(Position base) {
        return x - base.getBottomLeftChunkX() * 8;
    }

    /**
     * Returns the local Y coordinate within the view area described by {@code base}.
     *
     * @param base The base position (typically the player position).
     * @return The local Y coordinate.
     */
    public int getLocalY(Position base) {
        return y - base.getBottomLeftChunkY() * 8;
    }

    /**
     * Returns the {@link Chunk} that contains this position.
     *
     * @return The chunk.
     */
    public Chunk getChunk() {
        return new Chunk(this);
    }

    /**
     * Returns the {@link Region} that contains this position.
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
     * @return The height level.
     */
    public int getZ() {
        return z;
    }

    public Position setZ(int newZ) {
        this.z = newZ;
        return this;
    }

    public Position setX(int x) {
        this.x = x;
        return this;
    }

    public Position setY(int y) {
        this.y = y;
        return this;
    }
}
