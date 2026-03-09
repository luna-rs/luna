package io.luna.game.model.mob.interact;

import io.luna.game.model.mob.Player;

import java.util.function.Function;

/**
 * Defines how a player must reach and validate an interaction target.
 * <p>
 * An {@link InteractionPolicy} pairs an {@link InteractionType} with a distance rule that is used by interaction
 * handling to determine whether the target can be reached or acted upon.
 * <p>
 * Common policy factories are exposed as {@link Function}s so a policy can be created per-player when interaction
 * setup requires it.
 *
 * @author lare96
 */
public final class InteractionPolicy {

    /**
     * Standard size-based interaction policy.
     * <p>
     * Uses {@link InteractionType#SIZE} with a distance of {@code 1}.
     */
    public static final Function<Player, InteractionPolicy> STANDARD_SIZE =
            player -> new InteractionPolicy(InteractionType.SIZE, 1);

    /**
     * Size-based interaction policy that requires occupying the same effective position.
     * <p>
     * Uses {@link InteractionType#SIZE} with a distance of {@code 0}.
     */
    public static final Function<Player, InteractionPolicy> EQUAL_POSITION_SIZE =
            player -> new InteractionPolicy(InteractionType.SIZE, 0);

    /**
     * Standard line-of-sight interaction policy.
     * <p>
     * Uses {@link InteractionType#LINE_OF_SIGHT} with a default distance of {@code 10}.
     */
    public static final Function<Player, InteractionPolicy> STANDARD_LINE_OF_SIGHT =
            player -> new InteractionPolicy(InteractionType.LINE_OF_SIGHT, 10);

    /**
     * Unspecified interaction policy.
     * <p>
     * Uses {@link InteractionType#UNSPECIFIED} with a distance of {@code -1}, indicating that no explicit distance
     * rule is defined by this policy itself.
     */
    public static final Function<Player, InteractionPolicy> UNSPECIFIED =
            player -> new InteractionPolicy(InteractionType.UNSPECIFIED, -1);

    /**
     * The interaction rule type used by this policy.
     */
    private final InteractionType type;

    /**
     * The distance constraint associated with {@link #type}.
     * <p>
     * The meaning of this value depends on the interaction type. A value of {@code -1} is reserved for
     * {@link InteractionType#UNSPECIFIED}.
     */
    private final int distance;

    /**
     * Creates a new {@link InteractionPolicy}.
     *
     * @param type The interaction rule type.
     * @param distance The distance constraint for that type.
     * @throws IllegalArgumentException If {@code distance} is not valid for {@code type}.
     */
    public InteractionPolicy(InteractionType type, int distance) {
        checkDistance(distance);
        this.type = type;
        this.distance = distance;
    }

    /**
     * Validates that {@code distance} is legal for this policy's {@link #type}.
     *
     * @param distance The distance value to validate.
     * @throws IllegalArgumentException If the value is not valid for the current interaction type.
     */
    private void checkDistance(int distance) {
        if (distance < 1 && type == InteractionType.LINE_OF_SIGHT) {
            throw new IllegalArgumentException("LINE_OF_SIGHT interaction type must have a distance >= 1.");
        } else if (distance == 0 && type != InteractionType.SIZE) {
            throw new IllegalArgumentException("Only the SIZE interaction type can have a distance == 0.");
        } else if ((distance == -1 && type != InteractionType.UNSPECIFIED) ||
                (distance != -1 && type == InteractionType.UNSPECIFIED)) {
            throw new IllegalArgumentException("Only the UNSPECIFIED interaction type can and must have a distance == -1.");
        }
    }

    /**
     * @return The interaction type.
     */
    public InteractionType getType() {
        return type;
    }

    /**
     * @return The interaction distance.
     */
    public int getDistance() {
        return distance;
    }
}