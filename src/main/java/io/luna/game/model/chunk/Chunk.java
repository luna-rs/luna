package io.luna.game.model.chunk;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Locatable} representing a single 8x8 tile chunk on the RuneScape map.
 * <p>
 * Chunks are used as the spatial unit for:
 * <ul>
 *     <li>entity partitioning (see {@link ChunkRepository})</li>
 *     <li>grouped per-chunk update messages</li>
 *     <li>collision/pathfinding partitioning</li>
 * </ul>
 * <p>
 * The {@link #x} and {@link #y} fields represent chunk coordinates (not tile coordinates). A {@link Position}
 * can be converted to a chunk via {@link Position#getChunk()} and back to an absolute tile base via
 * {@link #getAbsPosition()}.
 *
 * @author lare96
 */
public final class Chunk implements Locatable {

    /**
     * The dimensions of a chunk in tiles.
     */
    public static final int SIZE = 8;

    /**
     * Chunk-space x coordinate.
     */
    private final int x;

    /**
     * Chunk-space y coordinate.
     */
    private final int y;

    /**
     * Creates a chunk from a tile {@link Position}.
     *
     * <p>
     * The chunk coordinates are derived from the position's "bottom-left chunk" coordinates.
     *
     * @param position The position to derive chunk coordinates from.
     */
    public Chunk(Position position) {
        this(position.getBottomLeftChunkX(), position.getBottomLeftChunkY());
    }

    /**
     * Creates a chunk directly from chunk-space coordinates.
     *
     * @param x The chunk x coordinate.
     * @param y The chunk y coordinate.
     */
    public Chunk(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Checks whether {@code position} belongs to this chunk.
     *
     * @param position The position to test.
     * @return {@code true} if {@code position}'s chunk equals this chunk.
     */
    @Override
    public boolean contains(Position position) {
        return position.getChunk().equals(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", x).add("y", y).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Chunk) {
            Chunk other = (Chunk) obj;
            return x == other.x && y == other.y;
        }
        return false;
    }

    /**
     * Returns the absolute (tile) location representing this chunk's base.
     * <p>
     * This delegates to {@link #getAbsPosition()} to satisfy {@link Locatable}.
     *
     * @return The absolute base position of this chunk.
     */
    @Override
    public Position absLocation() {
        return getAbsPosition();
    }

    /**
     * @return The chunk x coordinate.
     */
    @Override
    public int getX() {
        return x;
    }

    /**
     * @return The chunk y coordinate.
     */
    @Override
    public int getY() {
        return y;
    }

    /**
     * Packs the tile offset of {@code position} within this chunk into a single {@code int}.
     * <p>
     * The packed form is {@code (deltaX << 4) | deltaY}, where {@code deltaX} and {@code deltaY} are the
     * 0..7 tile deltas from this chunk's absolute base.
     * <p>
     * This is used by {@link ChunkUpdatableMessage} implementations when encoding per-chunk placement data.
     *
     * @param position The position to compute an offset for.
     * @return The packed delta offset.
     * @throws IllegalStateException if {@code position} is not within this chunk.
     */
    public int offset(Position position) {
        int deltaX = position.getX() - getAbsX();
        int deltaY = position.getY() - getAbsY();

        checkState(deltaX >= 0 && deltaX < Chunk.SIZE, "Invalid X delta [" + deltaX + "].");
        checkState(deltaY >= 0 && deltaY < Chunk.SIZE, "Invalid Y delta [" + deltaY + "].");

        return deltaX << 4 | deltaY;
    }

    /**
     * Returns a translated chunk.
     * <p>
     * If {@code addX == 0 && addY == 0}, this returns {@code this} to avoid allocation.
     *
     * @param addX Chunk-space delta x.
     * @param addY Chunk-space delta y.
     * @return The translated chunk.
     */
    public Chunk translate(int addX, int addY) {
        if (addX == 0 && addY == 0) {
            return this;
        }
        return new Chunk(x + addX, y + addY);
    }

    /**
     * Returns the absolute (tile) x coordinate of this chunk's base.
     * <p>
     * NOTE: This implementation applies an engine-specific offset ({@code +6}) when converting chunk-space to
     * absolute tile-space.
     *
     * @return The absolute base x coordinate in tiles.
     */
    public int getAbsX() {
        return SIZE * (x + 6);
    }

    /**
     * Returns the absolute (tile) y coordinate of this chunk's base.
     * <p>
     * NOTE: This implementation applies an engine-specific offset ({@code +6}) when converting chunk-space to
     * absolute tile-space.
     *
     * @return The absolute base y coordinate in tiles.
     */
    public int getAbsY() {
        return SIZE * (y + 6);
    }

    /**
     * Returns the absolute (tile) base position of this chunk.
     *
     * @return The absolute base position in tiles.
     */
    public Position getAbsPosition() {
        return new Position(getAbsX(), getAbsY());
    }
}
