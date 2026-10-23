package io.luna.game.model.mob.combat.damage;

import io.luna.game.event.impl.CombatDamageReceivedEvent;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.attack.CombatAttack;

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
     * Whether the accuracy roll succeeded, independently of the resolved damage amount.
     */
    private final boolean accurate;

    // todo docs, the source, can be null
   // private final CombatAttack<?> source;

    /**
     * Creates a new resolved combat hit.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat style used for the hit.
     * @param amount The resolved damage amount.
     */
    CombatDamage(Mob attacker, Mob victim, CombatDamageType type, OptionalInt amount) {
        this(attacker, victim, type, amount, amount.isPresent());
    }

    /**
     * Creates a resolved hit with an explicit accuracy result, allowing accurate zero hits to differ from misses.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat damage type.
     * @param amount The damage amount, {@code -1} for an effect-only hit, or empty for a magic splash.
     * @param accurate Whether the accuracy roll succeeded.
     */
    CombatDamage(Mob attacker, Mob victim, CombatDamageType type, OptionalInt amount, boolean accurate) {
        this.attacker = attacker;
        this.victim = victim;
        this.type = type;
        this.amount = amount;
        this.accurate = accurate;
       // this.source = source;
    }

    /**
     * Applies this resolved combat hit to the victim.
     * <p>
     * Splashes and effect-only hits do not deal numeric damage. Other hits are capped to remaining health and recorded
     * before damage is applied. Events, callbacks and history receive the applied hit, including effect-only hits.
     * Hits against victims that are already dead are discarded without invoking callbacks.
     *
     * @param source The attack notified after damage is applied.
     */
    public void apply(CombatAttack<?> source) {
        if (!victim.isAlive()) {
            return;
        }
        CombatDamage applied = this;
        if (amount.isPresent()) {
            int rawAmount = amount.getAsInt();
            if (rawAmount >= 0) {
                int actualAmount = Math.min(rawAmount, victim.getHealth());
                applied = new CombatDamage(attacker, victim, type, OptionalInt.of(actualAmount), accurate);
                // Record the killing hit before reducing health.
                victim.getCombat().getDamageStack().push(applied);
                victim.damage(actualAmount);
            }
        }
        // todo redesign this, combatattack damage, combatdamageaction, and this should all be in sync
        if (victim instanceof Player && victim != attacker) {
            victim.getPlugins().post(new CombatDamageReceivedEvent((Player) victim, applied));
        }
        source.onDamageApplied(applied);
        attacker.getCombat().setLastAttackSent(source);
        victim.getCombat().setLastAttackReceived(source);
        victim.getCombat().setLastDamageReceived(applied);
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

    /**
     * Distinguishes a successful zero-damage roll from a physical miss.
     *
     * @return Whether the accuracy check succeeded.
     */
    public boolean isAccurate() {
        return accurate;
    }
}
