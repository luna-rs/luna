package io.luna.game.model.mobile;

/**
 * A container for the data that represents a single {@code Hit} inflicted on a {@link MobileEntity}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Hit {

    /**
     * An enumerated type representing all of the types that a {@link Hit} can take on.
     */
    public enum HitType {
        BLOCKED(0),
        NORMAL(1),
        POISON(2),
        DISEASE(3);

        /**
         * The identifier for this {@code HitType}.
         */
        private final int opcode;

        /**
         * Creates a new {@link HitType}.
         *
         * @param opcode The identifier for this {@code HitType}.
         */
        HitType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * @return The identifier for this {@code HitType}.
         */
        public final int getOpcode() {
            return opcode;
        }
    }

    /**
     * The damage within this {@code Hit}.
     */
    private final int damage;

    /**
     * The {@code HitType} of this {@code Hit}.
     */
    private final HitType type;

    /**
     * Creates a new {@link Hit}.
     *
     * @param damage The damage within this {@code Hit}.
     * @param type The {@code HitType} of this {@code Hit}.
     */
    public Hit(int damage, HitType type) {
        if (damage < 0 || type == HitType.BLOCKED) {
            damage = 0;
        }
        if (damage == 0 && type == HitType.NORMAL) {
            type = HitType.BLOCKED;
        }
        this.damage = damage;
        this.type = type;
    }

    /**
     * Creates a new {@link Hit} with a {@code HitType} of {@code NORMAL}.
     *
     * @param damage The damage within this {@code Hit}.
     */
    public Hit(int damage) {
        this(damage, HitType.NORMAL);
    }

    /**
     * @return The damage within this {@code Hit}.
     */
    public int getDamage() {
        return damage;
    }

    /**
     * @return The {@code HitType} of this {@code Hit}.
     */
    public HitType getType() {
        return type;
    }
}
