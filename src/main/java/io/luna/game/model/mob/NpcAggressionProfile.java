package io.luna.game.model.mob;

/**
 * Defines how an {@link Npc} determines whether a potential target should be considered aggressive toward.
 * <p>
 * Each profile combines:
 * <ul>
 *     <li>An {@link NpcAggressionPolicy} describing the aggression rule to apply.</li>
 *     <li>A tolerance window, in minutes, that can be used when evaluating player aggression immunity.</li>
 * </ul>
 *
 * @author lare96
 */
public final class NpcAggressionProfile {

    /**
     * Enumerates the supported aggression rules for an NPC.
     */
    public enum NpcAggressionPolicy {

        /**
         * The NPC is always aggressive when other aggression checks pass.
         */
        ALWAYS,

        /**
         * The NPC is aggressive only when combat-level-based aggression rules allow it.
         */
        COMBAT_LEVEL,

        /**
         * The NPC is aggressive unless the target is protected by wearing a Saradomin item.
         */
        WEARING_SARADOMIN,

        /**
         * The NPC is aggressive unless the target is protected by wearing a Guthix item.
         */
        WEARING_GUTHIX,

        /**
         * The NPC is aggressive unless the target is protected by wearing a Zamorak item.
         */
        WEARING_ZAMORAK
    }

    /**
     * The aggression rule used by this profile.
     */
    private final NpcAggressionPolicy policy;

    /**
     * The tolerance duration, in minutes, associated with this profile.
     * <p>
     * This is typically used for aggression timeout behavior, such as the period after which an NPC may stop
     * automatically aggressing a player who has remained in the area.
     */
    private final int toleranceMinutes;

    /**
     * Creates a new NPC aggression profile.
     *
     * @param policy The aggression rule to apply.
     * @param toleranceMinutes The aggression tolerance duration, in minutes.
     */
    public NpcAggressionProfile(NpcAggressionPolicy policy, int toleranceMinutes) {
        this.policy = policy;
        this.toleranceMinutes = toleranceMinutes;
    }

    /**
     * Returns the aggression rule used by this profile.
     *
     * @return The aggression policy.
     */
    public NpcAggressionPolicy getPolicy() {
        return policy;
    }

    /**
     * Returns the aggression tolerance duration, in minutes.
     *
     * @return The tolerance duration.
     */
    public int getToleranceMinutes() {
        return toleranceMinutes;
    }
}