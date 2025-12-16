package io.luna.game.model.mob;

/**
 * Represents prayer icons rendered above the player's head.
 *
 * @author lare96
 */
public enum PrayerIcon {

    /**
     * No active prayer icon.
     */
    NONE(-1),

    /**
     * Protect from melee.
     */
    PROTECT_FROM_MELEE(0),

    /**
     * Protect from missiles (ranged).
     */
    PROTECT_FROM_MISSILES(1),

    /**
     * Protect from magic.
     */
    PROTECT_FROM_MAGIC(2),

    /**
     * Retribution (damages nearby enemies on death).
     */
    RETRIBUTION(3),

    /**
     * Smite (drains enemy prayer on hit).
     */
    SMITE(4),

    /**
     * Redemption (heals when health falls below a threshold).
     */
    REDEMPTION(5);

    /**
     * The protocol identifier used when encoding this icon.
     */
    private final int id;

    /**
     * Creates a new {@link PrayerIcon}.
     *
     * @param id The protocol identifier for this icon.
     */
    PrayerIcon(int id) {
        this.id = id;
    }

    /**
     * Returns the numeric identifier used by the appearance block encoding.
     *
     * @return The prayer icon identifier.
     */
    public int getId() {
        return id;
    }
}