package io.luna.game.model.mob.interact;

/**
 * Defines the rule used to determine whether an interaction target is reachable.
 * <p>
 * Each type represents a different validation strategy that may be used when resolving player interactions with
 * entities or objects.
 *
 * @author lare96
 */
public enum InteractionType {

    /**
     * Uses entity or object size-based reach rules.
     * <p>
     * This is the standard interaction mode for adjacent targets and other interactions where target dimensions
     * affect the valid approach distance.
     */
    SIZE,

    /**
     * Requires the target to be reachable through line-of-sight validation.
     * <p>
     * This is typically used for interactions that depend on visibility and obstruction checks in addition to distance.
     */
    LINE_OF_SIGHT,

    /**
     * Indicates that no specific interaction rule has been assigned.
     * <p>
     * This is generally used as a placeholder or sentinel value when interaction reach is determined
     * elsewhere.
     */
    UNSPECIFIED,
}