package io.luna.game.model.mob.combat;

import game.player.Sounds;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Graphic;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a single resolved combat damage outcome between an attacker and a victim.
 * <p>
 * A {@link CombatDamage} instance contains the final damage result after accuracy and hit calculation have already
 * been determined. The stored amount uses special semantics:
 * <ul>
 *     <li>{@link OptionalInt#empty()} represents a magic splash</li>
 *     <li>{@code 0} represents a non-splash zero-damage hit, such as an inaccurate or zero-rolled physical attack</li>
 *     <li>a value greater than {@code 0} represents successful damage</li>
 * </ul>
 * <p>
 * Instances are created through the provided factory methods, which support either precomputed values or full
 * formula-driven combat rolls.
 */
public final class CombatDamage {

    /**
     * Holds the intermediate accuracy result for a combat interaction before a
     * final {@link CombatDamage} is produced.
     * <p>
     * This type allows callers to either:
     * <ul>
     *     <li>roll accuracy now and compute damage afterward</li>
     *     <li>force a specific accuracy result</li>
     *     <li>supply a specific damage value after accuracy has been decided</li>
     * </ul>
     */
    public static final class CombatAccuracy {

        /**
         * The mob attempting to deal damage.
         */
        private final Mob attacker;

        /**
         * The mob receiving the combat interaction.
         */
        private final Mob victim;

        /**
         * The combat style used for this attack.
         */
        private final CombatDamageType type;

        /**
         * The resolved accuracy result.
         * <p>
         * If a value is not supplied explicitly, it is computed from the combat
         * formulas during construction.
         */
        private final Optional<Boolean> success;

        /**
         * Creates a new intermediate accuracy result.
         *
         * @param attacker The attacking mob.
         * @param victim The defending mob.
         * @param type The combat style used for this attack.
         * @param successOptional The forced accuracy result, or an empty optional to compute it
         * automatically.
         */
        private CombatAccuracy(Mob attacker, Mob victim, CombatDamageType type, Optional<Boolean> successOptional) {
            this.attacker = attacker;
            this.victim = victim;
            this.type = type;
            success = successOptional.isPresent() ? successOptional :
                    Optional.of(CombatFormula.rollAccuracy(attacker, victim, type));
        }

        /**
         * Computes the final {@link CombatDamage} result for this accuracy state.
         * <p>
         * If the attack is inaccurate:
         * <ul>
         *     <li>magic produces an empty damage value to represent a splash</li>
         *     <li>non-magic styles produce {@code 0}</li>
         * </ul>
         * <p>
         * If the attack is accurate, the supplied damage is used when present;
         * otherwise a hit is rolled from the combat formulas.
         *
         * @param damage The forced damage value, or an empty optional to roll damage
         * automatically.
         * @return The fully resolved combat damage result.
         */
        public CombatDamage computeDamage(OptionalInt damage) {
            boolean successValue = success.orElseThrow(() ->
                    new IllegalStateException("Success value should be computed by now."));
            OptionalInt computed;
            if (!successValue) {
                if (type == CombatDamageType.MAGIC) {
                    computed = OptionalInt.empty();
                } else {
                    computed = OptionalInt.of(0);
                }
            } else {
                computed = damage.isPresent() ? damage : OptionalInt.of(CombatFormula.rollHit(attacker, type));
            }
            return new CombatDamage(attacker, victim, type, computed);
        }

        /**
         * Computes the final {@link CombatDamage} result using an automatically
         * rolled hit when needed.
         *
         * @return The fully resolved combat damage result.
         */
        public CombatDamage computeDamage() {
            return computeDamage(OptionalInt.empty());
        }
    }

    /**
     * Creates an intermediate accuracy result using a forced success value.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this attack.
     * @param success The forced accuracy result.
     * @return The intermediate combat accuracy wrapper.
     */
    public static CombatAccuracy computeAccuracy(Mob attacker, Mob victim, CombatDamageType type, boolean success) {
        return new CombatAccuracy(attacker, victim, type, Optional.of(success));
    }

    /**
     * Creates an intermediate accuracy result with automatically rolled accuracy.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this attack.
     * @return The intermediate combat accuracy wrapper.
     */
    public static CombatAccuracy computeAccuracy(Mob attacker, Mob victim, CombatDamageType type) {
        return new CombatAccuracy(attacker, victim, type, Optional.empty());
    }

    /**
     * Creates a simple melee damage instance with a fixed damage amount and forced
     * accuracy success.
     * <p>
     * This is typically useful for scripted or guaranteed-damage effects that do
     * not need a normal accuracy roll.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param damage The fixed damage amount.
     * @return The resolved combat damage result.
     */
    public static CombatDamage simple(Mob attacker, Mob victim, int damage) {
        return computeAccuracy(attacker, victim, CombatDamageType.MELEE, true).computeDamage(OptionalInt.of(damage));
    }

    /**
     * Creates a fully formula-driven combat damage result by rolling both
     * accuracy and damage for the supplied combat style.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this attack.
     * @return The resolved combat damage result.
     */
    public static CombatDamage computed(Mob attacker, Mob victim, CombatDamageType type) {
        return computeAccuracy(attacker, victim, type).computeDamage();
    }

    /**
     * The mob attempting to deal damage.
     */
    private final Mob attacker;

    /**
     * The mob receiving the hit result.
     */
    private final Mob victim;

    /**
     * The combat style used for this damage result.
     */
    private final CombatDamageType type;

    /**
     * The resolved damage amount.
     * <p>
     * An empty value represents a magic splash, {@code 0} represents a zero-damage non-splash hit, and values greater
     * than {@code 0} represent successful damage.
     */
    private final OptionalInt amount;

    /**
     * Creates a new resolved combat damage result.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for this attack.
     * @param amount The resolved damage amount.
     */
    private CombatDamage(Mob attacker, Mob victim, CombatDamageType type, OptionalInt amount) {
        this.attacker = attacker;
        this.victim = victim;
        this.type = type;
        this.amount = amount;
    }

    /**
     * Applies this combat damage result to the victim.
     * <p>
     * Magic splashes trigger the splash graphic and, for player victims, the splash sound effect before the hit
     * is processed by the victim.
     * <p>
     * The actual damage application is delegated to {@link Mob#damage(int)}.
     */
    public void apply() {
        if (amount.isPresent()) {
            victim.damage(amount.getAsInt());
        } else if (type == CombatDamageType.MAGIC) {
            victim.graphic(new Graphic(85));
            if (victim instanceof Player) {
                ((Player) victim).playSound(Sounds.MAGIC_SPLASH);
            }
        }
        victim.getCombat().getDamageStack().push(this);
        // TODO Any on-hit effects can go here as well?
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
}