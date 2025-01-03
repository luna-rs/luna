package io.luna.game.model.map.builder;

import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.map.DynamicMap;

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
     * The plane on the palette.
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
     * @param plane The plane on the palette.
     * @param rotation The rotation of the base chunk in the palette.
     */
    public DynamicMapChunk(Chunk chunk, int plane, Rotation rotation) {
        this.chunk = chunk;
        this.plane = plane;
        this.rotation = rotation;
    }

    /**
     * Creates a new {@link DynamicMapChunk} with a normal rotation value.
     *
     * @param chunk The real world base chunk.
     */
    public DynamicMapChunk(Chunk chunk) {
        this(chunk, 0, Rotation.NORMAL);
    }

    /**
     * @return The real world base chunk.
     */
    public Chunk getChunk() {
        return chunk;
    }

    /**
     * @return The plane on the palette.
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
}
