package io.luna.game.model.map.builder;

import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.map.DynamicMap;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a real world map chunk that can be copied into a {@link DynamicMapPalette}. Used to build
 * {@link DynamicMap} types.
 *
 * @author lare96
 */
public final class DynamicMapChunk {

    /**
     * All possible rotation values for chunks within a palette.
     */
    public enum Rotation {
        NORMAL(0),
        CW_90_DEGREES(1),
        CW_180_DEGREES(2),
        CW_270_DEGREES(3);

        /**
         * The rotation value.
         */
        private final int value;

        /**
         * Creates a new {@link Rotation}.
         *
         * @param value The rotation value.
         */
        Rotation(int value) {
            this.value = value;
        }

        /**
         * @return The rotation value.
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * The real world base chunk.
     */
    private final Chunk chunk;

    /**
     * The real chunk plane.
     */
    private final int plane;

    /**
     * The rotation of the base chunk in the palette.
     */
    private final Rotation rotation;

    /**
     * Creates a new {@link DynamicMapChunk}.
     *
     * @param chunk The real world base chunk.
     * @param plane The real chunk plane.
     * @param rotation The rotation of the base chunk in the palette.
     */
    public DynamicMapChunk(Chunk chunk, int plane, Rotation rotation) {
        java.util.Objects.requireNonNull(chunk, "chunk");
        java.util.Objects.requireNonNull(rotation, "rotation");
        checkArgument(plane >= 0 && plane < 4, "Plane must be between 0 and 3.");
        // Luna stores chunk keys six below the native map-square coordinate.
        long nativeX = (long) chunk.getX() + 6;
        long nativeY = (long) chunk.getY() + 6;
        checkArgument(nativeX >= 0 && nativeX < 1024 &&
                nativeY >= 0 && nativeY < 2048, "Source chunk exceeds the revision 377 palette format.");
        this.chunk = chunk;
        this.plane = plane;
        this.rotation = rotation;
    }


    /**
     * Creates a new {@link DynamicMapChunk} with a normal rotation value.
     *
     * @param chunk The real world base chunk.
     */
    public DynamicMapChunk(Chunk chunk, int plane) {
        this(chunk, plane, Rotation.NORMAL);
    }

    /**
     * Creates a new {@link DynamicMapChunk}.
     *
     * @param x The real world base chunk.
     * @param y The real world base chunk.
     * @param plane The real chunk plane.
     */
    public DynamicMapChunk(int x, int y, int plane) {
        this(new Chunk(x, y), plane);
    }

    /**
     * @return The real world base chunk.
     */
    public Chunk getChunk() {
        return chunk;
    }

    /**
     * @return The real chunk plane.
     */
    public int getPlane() {
        return plane;
    }

    /**
     * @return The formatted chunk {@code x} coordinate.
     */
    public int getX() {
        return chunk.getAbsX() / 8;
    }

    /**
     * @return The formatted chunk {@code y} coordinate.
     */
    public int getY() {
        return chunk.getAbsY() / 8;
    }

    /**
     * @return The rotation value.
     */
    public int getRotation() {
        return rotation.getValue();
    }
    /**
     * Rotates a tile or oriented object footprint within its eight-tile source chunk. Object dimensions
     * must already reflect the object's source direction. This matches the revision 377 map decoder.
     *
     * @param x The source tile's local X coordinate.
     * @param y The source tile's local Y coordinate.
     * @param width The oriented footprint width; use 1 for a coordinate.
     * @param length The oriented footprint length; use 1 for a coordinate.
     * @return The rotated local X coordinate.
     */
    public int rotateX(int x, int y, int width, int length) {
        return switch (rotation) {
            case NORMAL -> x;
            case CW_90_DEGREES -> y;
            case CW_180_DEGREES -> 8 - x - width;
            case CW_270_DEGREES -> 8 - y - length;
        };
    }

    /**
     * Returns the local Y coordinate for the same footprint transformation as {@link #rotateX}.
     *
     * @param x The source local X coordinate.
     * @param y The source local Y coordinate.
     * @param width The oriented footprint width.
     * @param length The oriented footprint length.
     * @return The rotated local Y coordinate.
     */
    public int rotateY(int x, int y, int width, int length) {
        return switch (rotation) {
            case NORMAL -> y;
            case CW_90_DEGREES -> 8 - x - width;
            case CW_180_DEGREES -> 8 - y - length;
            case CW_270_DEGREES -> x;
        };
    }

}
