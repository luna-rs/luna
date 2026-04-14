package io.luna.game.model.mob.combat.damage;

import engine.combat.prayer.CombatPrayer;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatFormula;
import io.luna.util.RandomUtils;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkState;

/**
 * Describes a pending {@link CombatDamage} that should be resolved between an attacker and victim.
 * <p>
 * This class acts as a request object for combat damage calculation. It allows callers to:
 * <ul>
 *     <li>Use the default combat formula accuracy and max-hit calculations.</li>
 *     <li>Override the base accuracy or base max hit used during resolution.</li>
 *     <li>Apply additional flat or percentage-based modifiers.</li>
 *     <li>Bypass protection prayer mitigation.</li>
 *     <li>Resolve effect-only hits that do not deal numeric damage.</li>
 * </ul>
 * <p>
 * Once configured, the request is converted into a final {@link CombatDamage} by calling {@link #resolve()}.
 *
 * @author lare96
 */
public final class CombatDamageRequest {

    /**
     * Creates a standard builder for the supplied combat participants and damage type.
     * <p>
     * This is a small convenience wrapper around the {@link Builder} constructor so callers can begin custom request
     * setup without repeating {@code new Builder(...)} at each call site.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @return A new builder for the supplied combat context.
     */
    public static Builder builder(Mob attacker, Mob victim, CombatDamageType type) {
        return new Builder(attacker, victim, type);
    }

    /**
     * Creates an always-accurate zero-damage melee hit.
     * <p>
     * This is useful for hooks that need to produce a guaranteed hit result without dealing damage, but do not care
     * about using a specific combat style. If style-specific behaviour matters, use
     * {@link #zero(Mob, Mob, CombatDamageType)} instead.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @return A resolved request that always lands for {@code 0} melee damage.
     */
    public static CombatDamageRequest zero(Mob attacker, Mob victim) {
        return zero(attacker, victim, CombatDamageType.MELEE);
    }

    /**
     * Creates an always-accurate zero-damage hit for the supplied combat style.
     * <p>
     * The hit lands with perfect accuracy, uses the supplied combat damage type, and always resolves to numeric
     * {@code 0} damage.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @return A request that always lands for {@code 0} damage.
     */
    public static CombatDamageRequest zero(Mob attacker, Mob victim, CombatDamageType type) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .setBaseMaxHit(0)
                .build();
    }

    /**
     * Creates an always-accurate hit that uses the default max-hit calculation for the supplied combat style.
     * <p>
     * This is the standard "land the hit, then let normal damage rules apply" helper. It preserves the normal max-hit
     * calculation, protection prayer handling, and any other standard request behaviour.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @return An accurate hit using standard damage calculation for the supplied type.
     */
    public static CombatDamageRequest damage(Mob attacker, Mob victim, CombatDamageType type) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .build();
    }

    /**
     * Creates a forced miss for the supplied combat style.
     * <p>
     * This helper guarantees that the hit check fails. For magic, this resolves as a splash. For all other combat
     * styles, it resolves as a numeric zero hit.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @return A request that always misses.
     */
    public static CombatDamageRequest inaccurate(Mob attacker, Mob victim, CombatDamageType type) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(0.0)
                .setFlatBonusAccuracy(-1.0)
                .build();
    }

    /**
     * Creates an always-accurate hit with a custom base max hit.
     * <p>
     * This helper is useful when a hook needs to preserve the standard damage resolution flow while replacing the
     * formula-derived max hit with a fixed base cap.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @param baseMaxHit The base max hit to use before rolling damage.
     * @return An accurate hit with a custom base max hit.
     */
    public static CombatDamageRequest damage(Mob attacker, Mob victim, CombatDamageType type, int baseMaxHit) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .setBaseMaxHit(baseMaxHit)
                .build();
    }

    /**
     * Creates an always-accurate magic effect hit that does not deal numeric damage.
     * <p>
     * This is intended for spells or combat effects that should land successfully while only applying side effects.
     * The request resolves using the non-damaging hit marker handled by {@link Builder#disableDamage()}.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @return An accurate magic effect hit with damage disabled.
     */
    public static CombatDamageRequest effect(Mob attacker, Mob victim) {
        return builder(attacker, victim, CombatDamageType.MAGIC)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .disableDamage()
                .build();
    }

    /**
     * Creates an always-accurate hit that ignores protection prayers.
     * <p>
     * This helper keeps standard damage calculation for the supplied combat style, but bypasses protection prayer
     * mitigation during final damage resolution.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @return An accurate hit that ignores protection prayers.
     */
    public static CombatDamageRequest damageIgnoringPrayers(Mob attacker, Mob victim, CombatDamageType type) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .ignoreProtectionPrayers()
                .build();
    }

    /**
     * Creates an always-accurate hit with a custom base max hit that also ignores protection prayers.
     *
     * @param attacker The attacking mob.
     * @param victim The victim mob.
     * @param type The combat damage type.
     * @param baseMaxHit The base max hit to use before rolling damage.
     * @return An accurate custom-damage hit that ignores protection prayers.
     */
    public static CombatDamageRequest damageIgnoringPrayers(Mob attacker, Mob victim, CombatDamageType type,
                                                            int baseMaxHit) {
        return builder(attacker, victim, type)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .setBaseMaxHit(baseMaxHit)
                .ignoreProtectionPrayers()
                .build();
    }

    /**
     * Builds immutable {@link CombatDamageRequest} instances.
     * <p>
     * The builder begins with formula-driven defaults and allows callers to selectively replace base values or add
     * modifiers before the final request is created.
     */
    public static final class Builder {

        private final Mob attacker;
        private final Mob victim;
        private final CombatDamageType type;
        private OptionalDouble accuracy = OptionalDouble.empty();
        private OptionalInt maxHit = OptionalInt.empty();
        private boolean ignoreProtectionPrayers;
        private double flatBonusAccuracy;
        private double percentBonusDamage;
        private int flatBonusDamage;
        private int flatBonusMaxHit;
        private boolean damageDisabled;

        public Builder(Mob attacker, Mob victim, CombatDamageType type) {
            this.attacker = attacker;
            this.victim = victim;
            this.type = type;
        }

        /**
         * Marks this request as a non-damaging hit.
         * <p>
         * If the hit is accurate, it resolves using the special {@code -1} combat marker rather than dealing numeric
         * damage. This is useful for combat effects that should land but not damage the victim directly.
         *
         * @return This builder.
         */
        public Builder disableDamage() {
            checkState(type == CombatDamageType.MAGIC, "Damage can only be disabled for magic based attacks.");
            damageDisabled = true;
            return this;
        }

        /**
         * Replaces the formula-derived base accuracy used for this request.
         * <p>
         * This value is still subject to any additional flat accuracy modifiers configured on this builder.
         *
         * @param baseAccuracy The base accuracy to use during hit chance resolution.
         * @return This builder.
         */
        public Builder setBaseAccuracy(double baseAccuracy) {
            accuracy = OptionalDouble.of(baseAccuracy);
            return this;
        }

        /**
         * Restores formula-driven base accuracy calculation.
         *
         * @return This builder.
         */
        public Builder useDefaultAccuracy() {
            accuracy = OptionalDouble.empty();
            return this;
        }

        /**
         * Sets a flat bonus or penalty that will be added to the resolved base accuracy.
         *
         * @param flatAmount The flat accuracy modifier to apply.
         * @return This builder.
         */
        public Builder setFlatBonusAccuracy(double flatAmount) {
            flatBonusAccuracy = flatAmount;
            return this;
        }

        /**
         * Replaces the formula-derived base max hit used for this request.
         * <p>
         * This value is still subject to any flat max-hit modifiers configured on this builder.
         *
         * @param baseMaxHit The base max hit to use before rolling damage.
         * @return This builder.
         */
        public Builder setBaseMaxHit(int baseMaxHit) {
            maxHit = OptionalInt.of(baseMaxHit);
            return this;
        }

        /**
         * Restores formula-driven base max-hit calculation.
         *
         * @return This builder.
         */
        public Builder useDefaultMaxHit() {
            maxHit = OptionalInt.empty();
            return this;
        }

        /**
         * Sets a percentage-based damage modifier.
         * <p>
         * Positive values increase rolled damage, while negative values reduce it. This modifier is applied after
         * damage is rolled, and after protection prayer mitigation.
         *
         * @param percentAmount The percentage-based damage modifier to apply.
         * @return This builder.
         */
        public Builder setPercentBonusDamage(double percentAmount) {
            percentBonusDamage = percentAmount;
            return this;
        }

        /**
         * Sets a flat damage modifier.
         * <p>
         * This value is applied after damage is rolled, after any percentage-based adjustment, and after protection
         * prayer mitigation.
         *
         * @param flatAmount The flat damage modifier to apply.
         * @return This builder.
         */
        public Builder setFlatBonusDamage(int flatAmount) {
            flatBonusDamage = flatAmount;
            return this;
        }

        /**
         * Sets a flat modifier to the base max hit.
         * <p>
         * This value is applied after the base max hit is resolved or overridden, but before damage is rolled.
         *
         * @param flatAmount The flat max-hit modifier to apply.
         * @return This builder.
         */
        public Builder setFlatBonusMaxHit(int flatAmount) {
            flatBonusMaxHit = flatAmount;
            return this;
        }

        /**
         * Marks this request so protection prayer mitigation is ignored.
         *
         * @return This builder.
         */
        public Builder ignoreProtectionPrayers() {
            ignoreProtectionPrayers = true;
            return this;
        }

        /**
         * Builds an immutable combat damage request from the current builder state.
         *
         * @return The completed combat damage request.
         */
        public CombatDamageRequest build() {
            return new CombatDamageRequest(attacker, victim, type, accuracy, maxHit, ignoreProtectionPrayers,
                    flatBonusAccuracy, percentBonusDamage, flatBonusDamage, flatBonusMaxHit, damageDisabled);
        }
    }

    final Mob attacker;
    final Mob victim;
    final CombatDamageType type;
    final OptionalDouble accuracy;
    final OptionalInt maxHit;
    final boolean ignoreProtectionPrayers;
    final double flatBonusAccuracy;
    final double percentBonusDamage;
    final int flatBonusDamage;
    final int flatBonusMaxHit;
    final boolean damageDisabled;

    /**
     * Creates a new {@link CombatDamageRequest}.
     */
    private CombatDamageRequest(Mob attacker, Mob victim, CombatDamageType type, OptionalDouble accuracy,
                                OptionalInt maxHit, boolean ignoreProtectionPrayers, double flatBonusAccuracy,
                                double percentBonusDamage, int flatBonusDamage, int flatBonusMaxHit, boolean damageDisabled) {
        this.attacker = attacker;
        this.victim = victim;
        this.type = type;
        this.accuracy = accuracy;
        this.maxHit = maxHit;
        this.ignoreProtectionPrayers = ignoreProtectionPrayers;
        this.flatBonusAccuracy = flatBonusAccuracy;
        this.percentBonusDamage = percentBonusDamage;
        this.flatBonusDamage = flatBonusDamage;
        this.flatBonusMaxHit = flatBonusMaxHit;
        this.damageDisabled = damageDisabled;
    }

    /**
     * Resolves this request into a final {@link CombatDamage} instance.
     * <p>
     * Resolution proceeds as follows:
     * <ul>
     *     <li>Base accuracy is resolved from either the supplied override or the combat formula.</li>
     *     <li>Flat bonus accuracy is applied.</li>
     *     <li>If the hit is inaccurate, magic splashes and all other styles resolve as zero damage.</li>
     *     <li>If the hit is accurate and damage is disabled, a special non-damaging hit marker is produced.</li>
     *     <li>Otherwise, base max hit is resolved, flat max-hit bonuses are applied, and damage is rolled.</li>
     *     <li>Protection prayers are applied first, followed by percentage and flat damage bonuses.</li>
     * </ul>
     *
     * @return The fully resolved combat damage.
     */
    public CombatDamage resolve() {
        double baseAccuracy = accuracy.orElse(CombatFormula.calculateHitChance(attacker, victim, type));
        baseAccuracy +=  flatBonusAccuracy;
        OptionalInt computedDamage;
        if (!damageDisabled) {
            if (RandomUtils.roll(baseAccuracy)) {
                int baseMaxHit = maxHit.orElse(attacker.getCombat().getDefaultMaxHit(type));
                baseMaxHit += flatBonusMaxHit;

                int damage = baseMaxHit < 1 ? 0 : RandomUtils.inclusive(baseMaxHit);
                damage = applyPrayer(damage);
                int damageMod = percentBonusDamage != 0.0 ? (int) Math.floor(damage * Math.abs(percentBonusDamage)) : 0;
                if (percentBonusDamage > 0.0) {
                    damage += damageMod;
                } else if (percentBonusDamage < 0.0) {
                    damage -= damageMod;
                }
                damage += flatBonusDamage;
                computedDamage = OptionalInt.of(Math.max(0, damage));
            } else if (type == CombatDamageType.MAGIC) {
                computedDamage = OptionalInt.empty();
            } else {
                computedDamage = OptionalInt.of(0);
            }
        } else {
            computedDamage = OptionalInt.of(-1);
        }
        return new CombatDamage(attacker, victim, type, computedDamage);
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
    private int applyPrayer(int oldDamage) {
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