package io.luna.game.model.mob.combat.damage;

import io.luna.game.model.mob.Mob;

import java.util.OptionalInt;

/**
 * Represents a fully resolved combat hit between an attacker and a victim.
 * <p>
 * A {@link CombatDamage} instance is the final product of hit resolution after accuracy and damage have already been
 * determined. The stored {@code amount} uses the following semantics:
 * <ul>
 *     <li>{@link OptionalInt#empty()} means the hit splashed.</li>
 *     <li>{@code 0} means the hit landed but dealt zero damage.</li>
 *     <li>A value greater than {@code 0} means the hit landed and dealt damage.</li>
 *     <li>{@code -1} is a special non-damaging hit marker used for effects such as standard spellbook curse spells.</li>
 * </ul>
 * Instances are created through the factory methods on this class, either by supplying custom accuracy or damage data
 * or by delegating to the combat formula system.
 *
 * @author lare96
 */
public final class CombatDamage {

    /**
     * The mob attempting to deal damage.
     */
    private final Mob attacker;

    /**
     * The mob receiving the hit.
     */
    private final Mob victim;

    /**
     * The combat style used for this resolved hit.
     */
    private final CombatDamageType type;

    /**
     * The resolved damage amount.
     * <p>
     * An empty value represents a magic splash, {@code 0} represents a non-splash zero-damage hit, positive values
     * represent real damage, and {@code -1} represents a special non-damaging combat effect hit.
     */
    private final OptionalInt amount;

    /**
     * Creates a new resolved combat hit.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for the hit.
     * @param amount The resolved damage amount.
     */
    CombatDamage(Mob attacker, Mob victim, CombatDamageType type, OptionalInt amount) {
        this.attacker = attacker;
        this.victim = victim;
        this.type = type;
        this.amount = amount;
    }

    /**
     * Applies this resolved combat hit to the victim.
     * <p>
     * If this hit is a splash, no numeric damage is dealt. If the amount is {@code -1}, the hit represents a special
     * non-damaging combat effect and exits without applying damage or pushing to the damage stack. Otherwise, the
     * numeric damage is applied and this hit is pushed onto the victim's damage stack.
     */
    public void apply() {
        if (amount.isPresent()) {
            int rawAmount = amount.getAsInt();
            if (rawAmount == -1) {
                // Normal spellbook curse spell like weaken, etc.
                return;
            }
            victim.damage(rawAmount);
        }
        victim.getCombat().getDamageStack().push(this);
    }

    /**
     * @return The attacker.
     */
    public Mob getAttacker() {
        return attacker;
    }

    /**
     * @return The victim.
     */
    public Mob getVictim() {
        return victim;
    }

    /**
     * @return The combat damage type.
     */
    public CombatDamageType getType() {
        return type;
    }

    /**
     * @return The damage amount, where an empty value represents a magic splash.
     */
    public OptionalInt getAmount() {
        return amount;
    }

    /**
     * @return The resolved damage amount, or {@code 0} if this hit splashed.
     */
    public int getRawAmount() {
        return amount.orElse(0);
    }
}