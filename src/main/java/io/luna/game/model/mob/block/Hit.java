package io.luna.game.model.mob.block;

/**
 * Represents single damage hit applied to a mob.
 * <p>
 * Hits are displayed to nearby players as "hitsplats"—numeric damage bubbles above the mob. A hit combines:
 * </p>
 * <ul>
 *     <li>The damage amount inflicted.</li>
 *     <li>A visual style that describes how the hit occurred (blocked, normal, poison, disease).</li>
 *     <li>The mob's current and maximum health after the hit is applied, for updating the HP bar.</li>
 * </ul>
 *
 * @author lare96
 */
public final class Hit {

    /**
     * Defines the different hitsplat styles supported by the 317 protocol.
     */
    public enum HitType {

        /**
         * A hit that was successfully blocked or absorbed, usually displayed as a zero
         * with a blue style.
         */
        BLOCKED(0),

        /**
         * Standard red damage hitsplat, shown for normal melee/ranged/magic hits.
         */
        NORMAL(1),

        /**
         * Poison damage over time, typically shown as a green hitsplat.
         */
        POISON(2),

        /**
         * Disease damage over time, yellow hitsplat.
         */
        DISEASE(3);

        /**
         * Internal opcode sent to the client when encoding this hitsplat type.
         */
        private final int opcode;

        HitType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * Returns the numeric opcode used by the protocol for this hitsplat type.
         *
         * @return The type opcode.
         */
        public int getOpcode() {
            return opcode;
        }
    }

    /**
     * The raw damage amount inflicted by this hit.
     * <p>
     * This value is rendered in the hitsplat and is also used to update the mob's health on the server side before
     * encoding {@link #currentHealth}.
     * </p>
     */
    private final int damage;

    /**
     * The visual style of the hitsplat (blocked, normal, poison, disease).
     */
    private final HitType type;

    /**
     * The mob's current health after this hit is applied.
     * <p>
     * This value is used by the client to update the HP bar corresponding to the hitsplat.
     * </p>
     */
    private final int currentHealth;

    /**
     * The mob's maximum (total) health.
     * <p>
     * Together with {@link #currentHealth}, this lets the client render the correct proportion of the HP bar.
     * </p>
     */
    private final int totalHealth;

    /**
     * Creates a new {@link Hit} with an explicit type.
     *
     * @param damage The damage amount.
     * @param type The hitsplat type.
     * @param currentHealth The mob's health after this hit.
     * @param totalHealth The mob's maximum health.
     */
    public Hit(int damage, HitType type, int currentHealth, int totalHealth) {
        this.damage = damage;
        this.type = type;
        this.currentHealth = currentHealth;
        this.totalHealth = totalHealth;
    }

    /**
     * Creates a new {@link Hit} with {@link HitType#NORMAL} as the hitsplat type.
     *
     * @param damage The damage amount.
     * @param currentHealth The mob's health after this hit.
     * @param totalHealth The mob's maximum health.
     */
    public Hit(int damage, int currentHealth, int totalHealth) {
        this(damage, HitType.NORMAL, totalHealth, currentHealth);
    }

    /**
     * Returns the damage amount inflicted by this hit.
     *
     * @return The raw damage value.
     */
    public int getDamage() {
        return damage;
    }

    /**
     * Returns the visual hitsplat type for this hit.
     *
     * @return The {@link HitType}.
     */
    public HitType getType() {
        return type;
    }

    /**
     * Returns the mob's current health after applying this hit.
     *
     * @return The mob's post-hit health.
     */
    public int getCurrentHealth() {
        return currentHealth;
    }

    /**
     * Returns the mob's maximum (total) health.
     *
     * @return The mob's maximum health.
     */
    public int getTotalHealth() {
        return totalHealth;
    }
}
