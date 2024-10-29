package io.luna.game.model.chunk;

import com.google.common.base.MoreObjects;
import io.luna.game.model.Location;
import io.luna.game.model.Position;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Location} made up of 8x8 tiles on the Runescape map.
 *
 * @author lare96
 */
public final class Chunk implements Location {

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
     * Creates a new {@link Chunk}.
     *
     * @param position The position to get the chunk coordinates of.
     */
    public Chunk(Position position) {
        this(position.getTopLeftChunkX(), position.getTopLeftChunkY());
    }

    /**
     * Creates a new {@link Chunk}.
     *
     * @param x The center x coordinate of this region.
     * @param y The center y coordinate of this region.
     */
    public Chunk(int x, int y) {
        this.x = x;
        this.y = y;
    }

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
     * Returns the offset of {@code position} from this chunk.
     *
     * @param position The position.
     * @return The offset.
     */
    public int offset(Position position) {
        int deltaX = position.getX() - getBaseX();
        int deltaY = position.getY() - getBaseY();
        checkState(deltaX >= 0 && deltaX < Chunk.SIZE, "Invalid X delta [" + deltaX + "].");
        checkState(deltaY >= 0 && deltaY < Chunk.SIZE, "Invalid Y delta [" + deltaY + "].");
        return deltaX << 4 | deltaY;
    }

    /**
     * Returns a new {@link Chunk} translated by {@code addX} and {@code addY}.
     *
     * @param addX The x translation.
     * @param addY The y translation.
     * @return The new chunk position.
     */
    public Chunk translate(int addX, int addY) {
        if (addX == 0 && addY == 0) {
            return this;
        }
        return new Chunk(x + addX, y + addY);
    }

    /**
     * @return The absolute {@code x} coordinate.
     */
    public int getBaseX() {
        return SIZE * (x + 6);
    }

    /**
     * @return The absolute {@code y} coordinate.
     */
    public int getBaseY() {
        return SIZE * (y + 6);
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

    /**
     * @return The absolute position of this chunk.
     */
    public Position getBasePosition() {
        return new Position(getBaseX(), getBaseY());
    }
}