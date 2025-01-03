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
     * The dimensions of a chunk.
     */
    public static final int SIZE = 8;

    /**
     * The {@code x} coordinate of this chunk.
     */
    private final int x;

    /**
     * The {@code y} coordinate of this chunk.
     */
    private final int y;

    /**
     * Creates a new {@link Chunk}.
     *
     * @param position The position to get the chunk coordinates of.
     */
    public Chunk(Position position) {
        this(position.getBottomLeftChunkX(), position.getBottomLeftChunkY());
    }

    /**
     * Creates a new {@link Chunk}.
     *
     * @param x The {@code x} coordinate of this chunk.
     * @param y The {@code y} coordinate of this chunk.
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
     * Returns the packed deltas of {@code position} from the base position of this chunk. Used by
     * {@link ChunkUpdatableMessage} types to place entities on the map.
     *
     * @param position The position.
     * @return The offset.
     */
    public int offset(Position position) {
        int deltaX = position.getX() - getAbsX();
        int deltaY = position.getY() - getAbsY();
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
    public int getAbsX() {
        return SIZE * (x + 6);
    }

    /**
     * @return The absolute {@code y} coordinate.
     */
    public int getAbsY() {
        return SIZE * (y + 6);
    }

    /**
     * @return The {@code x} coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The {@code y} coordinate.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The absolute position of this chunk.
     */
    public Position getAbsPosition() {
        return new Position(getAbsX(), getAbsY());
    }
}