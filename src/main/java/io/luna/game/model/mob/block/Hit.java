package io.luna.game.model.mob.block;

/**
 * Represents a damage hit applied to a mob.
 * <p>
 * Hits are displayed to nearby players as "hitsplats"—numeric damage bubbles above the mob. A hit includes both
 * the damage value and a visual type indicating how it occurred (blocked, normal, poison, disease).
 * </p>
 *
 * @author lare96
 */
public final class Hit {

    /**
     * Defines the different hitsplat styles supported by the 317 protocol.
     */
    public enum HitType {

        /**
         * A hit that was successfully blocked or absorbed.
         */
        BLOCKED(0),

        /**
         * Standard damage hitsplat.
         */
        NORMAL(1),

        /**
         * Poison damage over time.
         */
        POISON(2),

        /**
         * Disease damage.
         */
        DISEASE(3);

        /**
         * Internal opcode sent during encoding.
         */
        private final int opcode;

        HitType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * @return The opcode for this hitsplat type.
         */
        public int getOpcode() {
            return opcode;
        }
    }

    /**
     * The amount of damage inflicted.
     */
    private final int damage;

    /**
     * The visual type of hitsplat.
     */
    private final HitType type;

    /**
     * Creates a new hit.
     *
     * @param damage The damage amount.
     * @param type The hitsplat type.
     */
    public Hit(int damage, HitType type) {
        this.damage = damage;
        this.type = type;
    }

    /**
     * Creates a normal-damage hit.
     *
     * @param damage The damage.
     */
    public Hit(int damage) {
        this(damage, HitType.NORMAL);
    }

    /**
     * @return Damage amount.
     */
    public int getDamage() {
        return damage;
    }

    /**
     * @return The hitsplat type.
     */
    public HitType getType() {
        return type;
    }
}
