package io.luna.game.model.chunk;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Position;

import java.util.Objects;

/**
 * A model representing the coordinates of a Chunk (8x8 tiles) on the Runescape map.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ChunkPosition {

    /**
     * The chunk length and width.
     */
    public static final int SIZE = 8;

    /**
     * The center x coordinate of this region.
     */
    private final int x;

    /**
     * The center y coordinate of this region.
     */
    private final int y;

    /**
     * Creates a new {@link ChunkPosition}.
     *
     * @param position The position to get the region coordinates of.
     */
    public ChunkPosition(Position position) {
        this(position.getBottomLeftChunkX(), position.getBottomLeftChunkY());
    }

    /**
     * Creates a new {@link ChunkPosition}.
     *
     * @param x The center x coordinate of this region.
     * @param y The center y coordinate of this region.
     */
    private ChunkPosition(int x, int y) {
        this.x = x;
        this.y = y;
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
        if (obj instanceof ChunkPosition) {
            ChunkPosition other = (ChunkPosition) obj;
            return x == other.x && y == other.y;
        }
        return false;
    }

    /**
     * Returns the offset of {@code position} from this chunk.
     *
     * @param position The position.
     * @return The offset.
     */
    public int offset(Position position) {
        // TODO Do it the proper way, through offsets.
        int deltaX = position.getX() - getAbsX();
        int deltaY = position.getY() - getAbsY();
        return 0;// deltaX << 4 | deltaY;
    }

    /**
     * Returns a new {@link ChunkPosition} translated by {@code addX} and {@code addY}.
     *
     * @param addX The x translation.
     * @param addY The y translation.
     * @return The new chunk position.
     */
    public ChunkPosition translate(int addX, int addY) {
        if (addX == 0 && addY == 0) {
            return this;
        }
        return new ChunkPosition(x + addX, y + addY);
    }

    /**
     * @return The absolute {@code x} coordinate.
     */
    public int getAbsX() {
        return (x + 6) * 8;
    }

    /**
     * @return The absolute {@code y} coordinate.
     */
    public int getAbsY() {
        return (y + 6) * 8;
    }

    /**
     * Returns the local x coordinate of {@code position} in this chunk.
     *
     * @param position The position.
     * @return The local x coordinate.
     */
    public int getLocalX(Position position) {
        return position.getX() % ChunkPosition.SIZE;
    }

    /**
     * Returns the local y coordinate of {@code position} in this chunk.
     *
     * @param position The position.
     * @return The local y coordinate.
     */
    public int getLocalY(Position position) {
        return position.getY() % ChunkPosition.SIZE;
    }

    /**
     * @return The top-left x coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The top-left center y coordinate.
     */
    public int getY() {
        return y;
    }
}