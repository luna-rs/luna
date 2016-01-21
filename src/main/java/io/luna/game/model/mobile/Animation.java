package io.luna.game.model.mobile;

/**
 * A container for the data that represents a single {@code Animation}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Animation {

    /**
     * An enumerated type containing all of the different priorities that an {@code Animation} can take on.
     */
    public enum AnimationPriority {
        LOW(-1),
        NORMAL(0),
        HIGH(1);

        /**
         * The {@code int} value of this priority.
         */
        private final int value;

        /**
         * Creates a new {@link AnimationPriority}.
         *
         * @param value The {@code int} value of this priority.
         */
        AnimationPriority(int value) {
            this.value = value;
        }

        /**
         * @return The {@code int} value of this priority.
         */
        public final int getValue() {
            return value;
        }
    }

    /**
     * An {@code Animation} that will cancel any currently playing {@code Animation}. It is assigned a priority of {@code
     * HIGH}.
     */
    public static final Animation CANCEL_ANIMATION = new Animation(65535, AnimationPriority.HIGH);

    /**
     * The identifier for this {@code Animation}.
     */
    private final int id;

    /**
     * The delay of this {@code Animation}.
     */
    private final int delay;

    /**
     * The priority of this {@code Animation}.
     */
    private final AnimationPriority priority;

    /**
     * Creates a new {@link Animation}.
     *
     * @param id The identifier for this {@code Animation}.
     * @param delay The delay of this {@code Animation}.
     * @param priority The priority of this {@code Animation}.
     */
    public Animation(int id, int delay, AnimationPriority priority) {
        this.id = id;
        this.delay = delay;
        this.priority = priority;
    }

    /**
     * Creates a new {@link Animation} with a delay of {@code 0}.
     *
     * @param id The identifier for this {@code Animation}.
     * @param priority The priority of this {@code Animation}.
     */
    public Animation(int id, AnimationPriority priority) {
        this(id, 0, priority);
    }

    /**
     * Creates a new {@link Animation} with a delay of {@code 0} and a priority of {@code NORMAL}.
     *
     * @param id The identifier for this {@code Animation}.
     */
    public Animation(int id) {
        this(id, 0, AnimationPriority.NORMAL);
    }

    /**
     * @return The identifier for this {@code Animation}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The delay of this {@code Animation}.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * @return The priority of this {@code Animation}.
     */
    public AnimationPriority getPriority() {
        return priority;
    }
}
