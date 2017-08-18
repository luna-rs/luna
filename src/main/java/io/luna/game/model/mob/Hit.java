package io.luna.game.model.mob;

/**
 * A model representing a hitsplat on a mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Hit {

    /**
     * An enum representing hitsplat types.
     */
    public enum HitType {
        BLOCKED(0),
        NORMAL(1),
        POISON(2),
        DISEASE(3);

        /**
         * The opcode.
         */
        private final int opcode;

        /**
         * Creates a new {@link HitType}.
         *
         * @param opcode The opcode.
         */
        HitType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * @return The opcode.
         */
        public final int getOpcode() {
            return opcode;
        }
    }

    /**
     * The damage.
     */
    private final int damage;

    /**
     * The type.
     */
    private final HitType type;

    /**
     * Creates a new {@link Hit}.
     *
     * @param damage The damage.
     * @param type The type.
     */
    public Hit(int damage, HitType type) {
        this.damage = damage;
        this.type = type;
    }

    /**
     * Creates a new {@link Hit} with a type of {@code NORMAL}.
     *
     * @param damage The damage.
     */
    public Hit(int damage) {
        this(damage, HitType.NORMAL);
    }

    /**
     * @return The damage.
     */
    public int getDamage() {
        return damage;
    }

    /**
     * @return The type.
     */
    public HitType getType() {
        return type;
    }
}
