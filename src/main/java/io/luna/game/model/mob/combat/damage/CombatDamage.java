package io.luna.game.model.mob.combat.damage;

import io.luna.game.event.impl.CombatDamageReceivedEvent;
import engine.combat.prayer.CombatPrayerEffects;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
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
 *     <li>{@code 0} means a physical miss or an accurate zero-damage hit; {@link #isAccurate()} distinguishes them.</li>
 *     <li>A value greater than {@code 0} means the hit landed and dealt damage.</li>
 *     <li>{@code -1} is a special non-damaging hit marker used for effects such as standard spellbook curse spells.</li>
 * </ul>
 * Instances are resolved through {@link CombatDamageRequest}. A launch hit may be replaced by defensive hooks before
 * delivery, and {@link #apply(CombatAttack)} creates a separate health-capped hit for events, effects, and history.
 * These immutable values describe different stages of the same hit; the original roll is retained by its attack.
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

    /**
     * Creates a resolved hit whose accuracy is inferred from the presence of a numeric amount.
     * Use the explicit-accuracy constructor for physical misses, whose amount is present but whose accuracy failed.
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
    }

    /**
     * Applies this resolved combat hit to the victim.
     * <p>
     * Splashes and effect-only hits do not deal numeric damage. Other hits are capped to remaining health and recorded
     * before damage is applied. History is then published before events and source callbacks receive the applied hit,
     * including splashes and effect-only hits. A callback can apply another hit without an older application overwriting
     * its newer history, and a callback failure does not undo history for damage already applied.
     * <p>
     * The callback argument identifies this hit even if nested notifications have since changed the combat history.
     * The source's primary victim and launch damage can differ from this recipient and applied amount for secondary
     * hits. Hits against victims that are already dead are discarded without updating history or invoking callbacks.
     * <p>
     * Smite uses delivered damage and launch-time prayer state before the hit can trigger death or Redemption.
     * Lethal combat hits notify Retribution through its separate, non-recursive damage source.
     *
     * @param source The non-null source attack notified after damage and history are applied.
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
                CombatPrayerEffects.INSTANCE.smite(applied, source);
                victim.damage(actualAmount);
                CombatPrayerEffects.INSTANCE.combatDeath(applied, source);
            }
        }
        attacker.getCombat().setLastAttackSent(source);
        victim.getCombat().setLastAttackReceived(source);
        victim.getCombat().setLastDamageReceived(applied);
        if (victim instanceof Npc npc) {
            npc.onDamageApplied(applied);
        }
        if (victim instanceof Player && victim != attacker) {
            victim.getPlugins().post(new CombatDamageReceivedEvent((Player) victim, applied));
        }
        source.onDamageApplied(applied);
    }

    /**
     * Caps numeric damage while retaining its participants, damage type, and original accuracy result.
     * Splashes and effect-only hits are returned unchanged.
     *
     * @param maximum The largest permitted numeric hit; negative values are treated as zero.
     * @return This hit if already within the cap, otherwise a capped copy.
     */
    public CombatDamage cappedAt(int maximum) {
        if (amount.isEmpty() || amount.getAsInt() < 0) {
            return this;
        }
        int capped = Math.min(amount.getAsInt(), Math.max(0, maximum));
        return capped == amount.getAsInt() ? this :
                new CombatDamage(attacker, victim, type, OptionalInt.of(capped), accurate);
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
