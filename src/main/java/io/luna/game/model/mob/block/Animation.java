package io.luna.game.model.mob.block;

/**
 * A model representing an animation performed by a mob.
 *
 * @author lare96 
 */
public final class Animation {

    /**
     * An enum representing animation priority levels.
     */
    public enum AnimationPriority {
        LOW(-1),

        NORMAL(0),
        HIGH(1);

        /**
         * The priority.
         */
        private final int value;

        /**
         * Creates a new {@link AnimationPriority}.
         *
         * @param value The priority.
         */
        AnimationPriority(int value) {
            this.value = value;
        }
    }

    /**
     * An animation that cancels the current animation.
     */
    public static final Animation CANCEL = new Animation(65535, AnimationPriority.HIGH);

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The delay.
     */
    private final int delay;

    /**
     * The priority.
     */
    private final AnimationPriority priority;

    /**
     * Creates a new {@link Animation}.
     *
     * @param id The identifier.
     * @param delay The delay.
     * @param priority The priority.
     */
    public Animation(int id, int delay, AnimationPriority priority) {
        this.id = id;
        this.delay = delay;
        this.priority = priority;
    }

    /**
     * Creates a new {@link Animation} with a delay of {@code 0}.
     *
     * @param id The identifier.
     * @param priority The priority.
     */
    public Animation(int id, AnimationPriority priority) {
        this(id, 0, priority);
    }

    /**
     * Creates a new {@link Animation} with a delay of {@code 0} and a priority of {@code NORMAL}.
     *
     * @param id The identifier.
     */
    public Animation(int id) {
        this(id, 0, AnimationPriority.NORMAL);
    }

    /**
     * Determines if this animation overrides {@code other}.
     *
     * @param other The other animation.
     * @return {@code true} if this animation overrides {@code other}.
     */
    public final boolean overrides(Animation other) {
        return priority.value >= other.priority.value;
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
     * @return The priority.
     */
    public AnimationPriority getPriority() {
        return priority;
    }
}
