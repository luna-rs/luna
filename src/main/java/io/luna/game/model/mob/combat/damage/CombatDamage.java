package io.luna.game.model.mob.combat.damage;

import engine.combat.prayer.CombatPrayer;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatFormula;
import io.luna.util.RandomUtils;

import java.util.OptionalDouble;
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
     * Represents an intermediate combat state where hit chance has been resolved, but final damage has not yet been \
     * computed.
     * <p>
     * This wrapper allows callers to:
     * <ul>
     *     <li>Use the default combat formula hit chance.</li>
     *     <li>Force a specific accuracy value.</li>
     *     <li>Adjust accuracy before rolling.</li>
     *     <li>Ignore protection prayers for the eventual hit.</li>
     *     <li>Compute either automatic or fixed damage afterward.</li>
     * </ul>
     */
    public static final class CombatAccuracy {

        /**
         * The mob attempting to deal damage.
         */
        private final Mob attacker;

        /**
         * The mob receiving the hit.
         */
        private final Mob victim;

        /**
         * The combat style used for this interaction.
         */
        private final CombatDamageType type;

        /**
         * The final hit chance percentage used when rolling accuracy.
         */
        private double accuracy;

        /**
         * Whether protection prayer mitigation should be ignored when damage is finalized.
         */
        private boolean ignoreProtectionPrayers;

        /**
         * Creates a new intermediate accuracy result.
         *
         * @param attacker The attacking mob.
         * @param victim The defending mob.
         * @param type The combat style used for this hit.
         * @param accuracyOptional A forced hit chance, or an empty optional to calculate hit chance from the combat
         * formula system.
         */
        private CombatAccuracy(Mob attacker, Mob victim, CombatDamageType type, OptionalDouble accuracyOptional) {
            this.attacker = attacker;
            this.victim = victim;
            this.type = type;
            accuracy = accuracyOptional.orElse(CombatFormula.calculateHitChance(attacker, victim, type));
        }

        /**
         * Adds a flat amount to this hit's accuracy before it is rolled.
         *
         * @param value The accuracy value to add.
         * @return This accuracy wrapper.
         */
        public CombatAccuracy addAccuracy(double value) {
            accuracy += value;
            return this;
        }

        /**
         * Marks this hit so protection prayer mitigation is skipped when final damage is computed.
         *
         * @return This accuracy wrapper.
         */
        public CombatAccuracy ignoreProtectionPrayers() {
            ignoreProtectionPrayers = true;
            return this;
        }

        /**
         * Rolls and computes damage using the supplied maximum hit.
         * <p>
         * A random hit is generated in the inclusive range of {@code 0} to {@code maxHit}, then forwarded to
         * {@link #computeDamage(OptionalInt)} so the normal damage pipeline can apply any additional processing.
         *
         * @param maxHit The maximum possible hit that can be rolled.
         * @return The computed damage result for the rolled hit
         */
        public CombatDamage computeDamage(int maxHit) {
            maxHit = Math.max(1, maxHit);
            int roll = RandomUtils.inclusive(maxHit);
            return computeDamage(OptionalInt.of(roll));
        }

        /**
         * Computes the final {@link CombatDamage} result for this accuracy state.
         * <p>
         * Accuracy is rolled using the current {@link #accuracy} value. If the hit fails:
         * <ul>
         *     <li>Magic returns an empty damage value to represent a splash.</li>
         *     <li>Non-magic styles return {@code 0}.</li>
         * </ul>
         * If the hit succeeds, the supplied damage is used when present. Otherwise, damage is rolled from the
         * attacker's maximum hit for the current combat style.
         * <p>
         * Non-positive max-hit values are preserved directly rather than being rolled.
         *
         * @param damage A forced damage amount, or an empty optional to roll damage automatically.
         * @return The fully resolved combat hit.
         */
        public CombatDamage computeDamage(OptionalInt damage) {
            boolean success = RandomUtils.rollPercent(accuracy);
            OptionalInt computed;
            if (!success) {
                if (type == CombatDamageType.MAGIC) {
                    computed = OptionalInt.empty();
                } else {
                    computed = OptionalInt.of(0);
                }
            } else if (damage.isPresent()) {
                int rawAmount = damage.orElse(0);
                computed = OptionalInt.of(applyProtectionPrayers(rawAmount));
            } else {
                int maxHit = attacker.getCombat().getDefaultMaxHit(type);
                int rolledDamage = maxHit < 1 ? 0 : RandomUtils.inclusive(maxHit);
                int normalized = Math.max(0, applyProtectionPrayers(rolledDamage));
                computed = OptionalInt.of(normalized);
            }
            return new CombatDamage(attacker, victim, type, computed);
        }

        /**
         * Computes the final {@link CombatDamage} result, rolling damage automatically when needed.
         *
         * @return The fully resolved combat hit.
         */
        public CombatDamage computeDamage() {
            return computeDamage(OptionalInt.empty());
        }

        /**
         * Applies protection prayer mitigation to a resolved non-splash damage value.
         * <p>
         * Protection prayers only affect {@link Player} victims and are only applied when they are not being ignored
         * for this hit. If the victim has the matching protection prayer active for this combat style:
         * <ul>
         *     <li>{@link Npc} attackers are reduced to {@code 0} damage.</li>
         *     <li>Non-NPC attackers are reduced to {@code 60%} of the original damage.</li>
         * </ul>
         *
         * @param oldDamage The previously resolved damage amount.
         * @return The final damage amount after any prayer mitigation.
         */
        private int applyProtectionPrayers(int oldDamage) {
            if (!ignoreProtectionPrayers && victim instanceof Player) {
                Player victimPlr = victim.asPlr();
                CombatPrayer prayer = getPrayerForType(type);
                if (prayer != null && victimPlr.getCombat().getPrayers().isActive(prayer)) {
                    return attacker instanceof Npc ? 0 : (int) Math.floor(oldDamage * 0.6);
                }
            }
            return oldDamage;
        }

        /**
         * Gets the protection prayer associated with a combat damage type.
         *
         * @param type The combat damage type.
         * @return The matching protection prayer, or {@code null} if none applies.
         */
        private CombatPrayer getPrayerForType(CombatDamageType type) {
            switch (type) {
                case MELEE:
                    return CombatPrayer.PROTECT_FROM_MELEE;
                case MAGIC:
                    return CombatPrayer.PROTECT_FROM_MAGIC;
                case RANGED:
                    return CombatPrayer.PROTECT_FROM_MISSILES;
            }
            return null;
        }
    }

    /**
     * Creates an intermediate accuracy result using a forced success outcome.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this hit.
     * @param success {@code true} to force a guaranteed hit chance of {@code 1.0}, {@code false} to force {@code 0.0}.
     * @return The intermediate accuracy wrapper.
     */
    public static CombatAccuracy computeAccuracy(Mob attacker, Mob victim, CombatDamageType type, boolean success) {
        return new CombatAccuracy(attacker, victim, type, OptionalDouble.of(success ? 1.0 : 0.0));
    }

    /**
     * Creates an intermediate accuracy result using a supplied hit chance.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this hit.
     * @param accuracy The hit chance to roll against instead of using the standard combat formula.
     * @return The intermediate accuracy wrapper.
     */
    public static CombatAccuracy computeAccuracy(Mob attacker, Mob victim, CombatDamageType type, double accuracy) {
        return new CombatAccuracy(attacker, victim, type, OptionalDouble.of(accuracy));
    }

    /**
     * Creates an intermediate accuracy result using automatically calculated hit chance.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this hit.
     * @return The intermediate accuracy wrapper.
     */
    public static CombatAccuracy computeAccuracy(Mob attacker, Mob victim, CombatDamageType type) {
        return new CombatAccuracy(attacker, victim, type, OptionalDouble.empty());
    }

    /**
     * Creates a guaranteed melee hit with a fixed damage amount.
     * <p>
     * This is useful for scripted damage or special effects that should not use the normal combat accuracy roll.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param damage The fixed damage amount.
     * @return The resolved combat hit.
     */
    public static CombatDamage simple(Mob attacker, Mob victim, int damage) {
        return computeAccuracy(attacker, victim, CombatDamageType.MELEE, true).computeDamage(OptionalInt.of(damage));
    }

    /**
     * Creates a fully formula-driven combat hit by rolling both accuracy and damage.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this hit.
     * @return The resolved combat hit.
     */
    public static CombatDamage computed(Mob attacker, Mob victim, CombatDamageType type) {
        return computeAccuracy(attacker, victim, type).computeDamage();
    }

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
    private CombatDamage(Mob attacker, Mob victim, CombatDamageType type, OptionalInt amount) {
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