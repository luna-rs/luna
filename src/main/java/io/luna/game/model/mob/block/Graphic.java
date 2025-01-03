package io.luna.game.model.mob.block;

/**
 * A model representing a graphic performed by a mob.
 *
 * @author lare96
 */
public final class Graphic {

    /**
     * A constant representing a {@code null} graphic.
     */
    public static final Graphic NULL = new Graphic(-1);

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The delay.
     */
    private final int delay;

    /**
     * The height.
     */
    private final int height;

    /**
     * Creates a new {@link Graphic}.
     *
     * @param id The identifier.
     * @param delay The delay.
     * @param height The height.
     */
    public Graphic(int id, int delay, int height) {
        this.id = id;
        this.delay = delay;
        this.height = height;
    }

    /**
     * Creates a new {@link Graphic} with a delay of {@code 0}.
     *
     * @param id The identifier.
     * @param height The height.
     */
    public Graphic(int id, int height) {
        this(id, 0, height);
    }

    /**
     * Creates a new {@link Graphic} with a delay and height of {@code 0}.
     *
     * @param id The identifier.
     */
    public Graphic(int id) {
        this(id, 0);
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The delay.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * @return The height.
     */
    public int getHeight() {
        return height;
    }
}
