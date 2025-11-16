package io.luna.game.model.mob.block;

/**
 * Represents a graphic (spot animation) performed by a mob.
 * <p>
 * Graphics correspond to temporary visual effects such as spell casts, projectile impacts, teleport effects, and
 * other overlay animations. They are displayed independently of the mob's main animation sequence.
 * </p>
 *
 * @author lare96
 */
public final class Graphic {

    /**
     * A constant graphic representing the absence of any effect.
     */
    public static final Graphic NULL = new Graphic(-1);

    /**
     * Graphic identifier.
     */
    private final int id;

    /**
     * Delay before the graphic starts.
     */
    private final int delay;

    /**
     * Height (or layering) modifier.
     */
    private final int height;

    /**
     * Creates a new graphic.
     *
     * @param id The graphic ID.
     * @param delay Delay before display.
     * @param height The graphic height/layer.
     */
    public Graphic(int id, int delay, int height) {
        this.id = id;
        this.delay = delay;
        this.height = height;
    }

    /**
     * Creates a graphic with no delay.
     *
     * @param id The graphic ID.
     * @param height The graphic height.
     */
    public Graphic(int id, int height) {
        this(id, 0, height);
    }

    /**
     * Creates a graphic with no delay and height 0.
     *
     * @param id The graphic ID.
     */
    public Graphic(int id) {
        this(id, 0);
    }

    /**
     * @return Graphic ID.
     */
    public int getId() {
        return id;
    }

    /**
     * @return Graphic delay.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * @return Graphic height.
     */
    public int getHeight() {
        return height;
    }
}
