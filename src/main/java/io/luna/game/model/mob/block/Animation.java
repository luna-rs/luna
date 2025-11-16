package io.luna.game.model.mob.block;

/**
 * Represents an animation performed by a mob (player or NPC), including its priority and optional delay.
 * Animations are submitted to mobs during the server tick and later encoded into the update mask during
 * synchronization. An animation consists of:
 *
 * <ul>
 *     <li>An animation identifier (model sequence ID).</li>
 *     <li>An optional delay, expressed in ticks, before the animation begins.</li>
 *     <li>A priority level that determines whether it may override an existing animation.</li>
 * </ul>
 *
 * <p>
 * The server always treats higher-priority animations as capable of replacing lower-priority ones. This is used
 * heavily in combat where weapon animations must override emotes, and death animations.
 * </p>
 *
 * @author lare96
 */
public final class Animation {

    /**
     * Defines priority levels used to determine whether one animation can replace another.
     * <p>
     * Higher-priority animations override lower-priority ones when submitted during an update cycle. For example, a
     * high-priority combat animation should interrupt a low-priority emote.
     * </p>
     */
    public enum AnimationPriority {

        /**
         * Standard priority. Used by most animations in the game.
         */
        NORMAL(0),

        /**
         * High priority. Reserved for animations that must override others immediately.
         */
        HIGH(1),

        /**
         * Immutable priority. Once an animation has this priority it can't be overridden by anything.
         */
        IMMUTABLE(2);

        /**
         * Internal numeric value used when comparing priorities.
         */
        private final int value;

        /**
         * Creates a new {@link AnimationPriority}.
         *
         * @param value The numeric priority used for comparisons.
         */
        AnimationPriority(int value) {
            this.value = value;
        }
    }

    /**
     * A special synthetic animation that forces the client to stop any active animation.
     * <p>
     * This corresponds to the RuneScape protocol’s "reset animation" value (65535). It is typically used when a
     * player dies, teleports, or when an animation must be forcibly cleared before starting a new one.
     * </p>
     * <p>
     * This animation always uses {@link AnimationPriority#HIGH} so it overrides any existing animation.
     * </p>
     */
    public static final Animation CANCEL = new Animation(65535, AnimationPriority.HIGH);

    /**
     * The animation identifier (sequence ID).
     */
    private final int id;

    /**
     * Optional delay before the animation begins, measured in client ticks.
     */
    private final int delay;

    /**
     * The animation’s priority, used when determining override behavior.
     */
    private final AnimationPriority priority;

    /**
     * Creates a new {@link Animation} with the given identifier, delay, and priority.
     *
     * @param id The animation identifier.
     * @param delay The delay before the animation begins.
     * @param priority The animation priority.
     */
    public Animation(int id, int delay, AnimationPriority priority) {
        this.id = id;
        this.delay = delay;
        this.priority = priority;
    }

    /**
     * Creates a new {@link Animation} with no delay.
     *
     * @param id The animation identifier.
     * @param priority The animation priority.
     */
    public Animation(int id, AnimationPriority priority) {
        this(id, 0, priority);
    }

    /**
     * Creates a new {@link Animation} with {@link AnimationPriority#NORMAL} and no delay.
     *
     * @param id The animation identifier.
     */
    public Animation(int id) {
        this(id, 0, AnimationPriority.NORMAL);
    }

    /**
     * Determines whether this animation overrides another based on priority.
     *
     * <p>
     * Override rules:
     * </p>
     * <ul>
     *     <li>If this animation's priority is higher than or equal to that of {@code other}, it overrides.</li>
     *     <li>If lower, the current animation will be ignored.</li>
     * </ul>
     *
     * <p>
     * This is used by the mob’s animation update logic to ensure consistent priority handling.
     * </p>
     *
     * @param other The animation being compared against.
     * @return {@code true} if this animation can replace {@code other}.
     */
    public boolean overrides(Animation other) {
        if(priority == AnimationPriority.IMMUTABLE) {
            return false;
        }
        return priority.value >= other.priority.value;
    }

    /**
     * @return The animation identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The delay before the animation begins, in ticks.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * @return The priority level of this animation.
     */
    public AnimationPriority getPriority() {
        return priority;
    }
}
