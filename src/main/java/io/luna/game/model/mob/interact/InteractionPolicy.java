package io.luna.game.model.mob.interact;

import io.luna.game.model.Entity;
import io.luna.game.model.mob.Player;

import java.util.function.BiFunction;

/**
 * Defines the reach and distance rules used when interacting with an entity.
 * <p>
 * An interaction policy combines an {@link InteractionType} with a distance value. The collision and interaction
 * systems use this pair to determine whether a player or mob has reached a target and may perform the requested
 * interaction.
 * <p>
 * Shared policies are exposed as constants where possible. Factory-style policies are exposed as {@link BiFunction}s
 * for interaction handlers that expect a policy to be resolved from the player and target.
 *
 * @author lare96
 */
public final class InteractionPolicy {

    /**
     * A size-aware policy that requires the source to occupy the same effective tile as the target.
     * <p>
     * This uses {@link InteractionType#SIZE} with a distance of {@code 0}.
     */
    public static final InteractionPolicy EQUAL_POSITION = new InteractionPolicy(InteractionType.SIZE, 0);

    /**
     * The standard adjacent size-aware interaction policy.
     * <p>
     * This uses {@link InteractionType#SIZE} with a distance of {@code 1}. It is suitable for most object and mob
     * interactions where the source only needs to stand next to the target.
     */
    public static final InteractionPolicy STANDARD_SIZE = new InteractionPolicy(InteractionType.SIZE, 1);

    /**
     * The standard line-of-sight interaction policy.
     * <p>
     * This uses {@link InteractionType#LINE_OF_SIGHT} with a distance of {@code 10}. It is suitable for
     * ranged, magic, projectile, or other interactions that require both distance and a clear line of sight.
     */
    public static final InteractionPolicy STANDARD_LINE_OF_SIGHT = new InteractionPolicy(InteractionType.LINE_OF_SIGHT, 10);

    /**
     * An unrestricted interaction policy.
     * <p>
     * This uses {@link InteractionType#UNSPECIFIED} with a distance of {@code -1}. Reach checks using this policy
     * do not apply explicit distance or collision restrictions.
     */
    public static final InteractionPolicy UNSPECIFIED = new InteractionPolicy(InteractionType.UNSPECIFIED, -1);
    /**
     * A reusable factory that returns the standard adjacent size-aware interaction policy.
     * <p>
     * The player and target arguments are ignored because {@link #STANDARD_SIZE} is shared and does not require
     * per-target state.
     */
    public static final BiFunction<Player, Entity, InteractionPolicy> STANDARD_SIZE_BIF =
            (player, target) -> STANDARD_SIZE;

    /**
     * A reusable factory that returns the same-tile size-aware interaction policy.
     * <p>
     * The player and target arguments are ignored because {@link #EQUAL_POSITION} is shared and does not require
     * per-target state.
     */
    public static final BiFunction<Player, Entity, InteractionPolicy> EQUAL_POSITION_BIF =
            (player, target) -> EQUAL_POSITION;

    /**
     * A reusable factory that returns a standard line-of-sight interaction policy.
     * <p>
     * This uses {@link InteractionType#LINE_OF_SIGHT} with a distance of {@code 10}. It is suitable for ranged,
     * magic, projectile, or other interactions that require both distance and a clear raycast.
     */
    public static final BiFunction<Player, Entity, InteractionPolicy> STANDARD_LINE_OF_SIGHT_BIF =
            (player, target) -> STANDARD_LINE_OF_SIGHT;

    /**
     * A reusable factory that returns an unrestricted interaction policy.
     * <p>
     * This uses {@link InteractionType#UNSPECIFIED} with a distance of {@code -1}. Reach checks using this policy
     * do not apply explicit distance or collision restrictions.
     */
    public static final BiFunction<Player, Entity, InteractionPolicy> UNSPECIFIED_BIF =
            (player, target) -> UNSPECIFIED;

    /**
     * The interaction rule type used by this policy.
     */
    private final InteractionType type;

    /**
     * The distance constraint associated with this policy.
     * <p>
     * The meaning of this value depends on {@link #type}. A value of {@code -1} is reserved for {@link InteractionType#UNSPECIFIED}.
     */
    private final int distance;

    /**
     * Creates a new {@link InteractionPolicy}.
     *
     * @param type The interaction rule type.
     * @param distance The distance constraint for the interaction rule.
     * @throws IllegalArgumentException If the distance is not valid for the supplied interaction type.
     */
    public InteractionPolicy(InteractionType type, int distance) {
        checkDistance(type, distance);
        this.type = type;
        this.distance = distance;
    }

    /**
     * Validates that a distance value is legal for an interaction type.
     *
     * @param type The interaction rule type.
     * @param distance The distance value to validate.
     * @throws IllegalArgumentException If the distance is not valid for the supplied interaction type.
     */
    private void checkDistance(InteractionType type, int distance) {
        if (distance < 1 && type == InteractionType.LINE_OF_SIGHT) {
            throw new IllegalArgumentException("LINE_OF_SIGHT interaction type must have a distance >= 1.");
        } else if (distance == 0 && type != InteractionType.SIZE) {
            throw new IllegalArgumentException("Only the SIZE interaction type can have a distance == 0.");
        } else if ((distance == -1 && type != InteractionType.UNSPECIFIED) ||
                (distance != -1 && type == InteractionType.UNSPECIFIED)) {
            throw new IllegalArgumentException(
                    "Only the UNSPECIFIED interaction type can and must have a distance == -1.");
        }
    }

    /**
     * @return The interaction rule type.
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