package io.luna.game.model.mobile;

/**
 * A container for the data that represents a single {@code Graphic}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Graphic {

    /**
     * The identifier for this {@code Graphic}.
     */
    private final int id;

    /**
     * The delay for this {@code Graphic}.
     */
    private final int delay;

    /**
     * The height for this {@code Graphic}.
     */
    private final int height;

    /**
     * Creates a new {@link Graphic}.
     *
     * @param id The identifier for this {@code Graphic}.
     * @param delay The delay for this {@code Graphic}.
     * @param height The height for this {@code Graphic}.
     */
    public Graphic(int id, int delay, int height) {
        this.id = id;
        this.delay = delay;
        this.height = height;
    }

    /**
     * Creates a new {@link Graphic} with a delay of {@code 0}.
     *
     * @param id The identifier for this {@code Graphic}.
     * @param height The height for this {@code Graphic}.
     */
    public Graphic(int id, int height) {
        this(id, 0, height);
    }

    /**
     * Creates a new {@link Graphic} with a delay and height of {@code 0}.
     *
     * @param id The identifier for this {@code Graphic}.
     */
    public Graphic(int id) {
        this(id, 0);
    }

    /**
     * @return The identifier for this {@code Graphic}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The delay for this {@code Graphic}.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * @return The height for this {@code Graphic}.
     */
    public int getHeight() {
        return height;
    }
}
