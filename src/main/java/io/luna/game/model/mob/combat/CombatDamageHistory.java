package io.luna.game.model.mob.combat;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

/**
 * Describes an applied hit using scalar damage data and non-owning participant identities.
 * Executable attacks, callbacks, and participant object graphs belong to the delivery operation.
 *
 * @author lare96
 */
public final class CombatDamageHistory {

    private final MobReference attacker;
    private final MobReference victim;
    private final CombatDamageType type;
    private final int rawAmount;
    private final boolean accurate;

    /**
     * Copies the delivered hit's capped amount and participant identities.
     *
     * @param damage The applied hit being described.
     */
    public CombatDamageHistory(CombatDamage damage) {
        attacker = MobReference.capture(damage.getAttacker());
        victim = MobReference.capture(damage.getVictim());
        type = damage.getType();
        rawAmount = damage.getRawAmount();
        accurate = damage.isAccurate();
    }

    public Mob getAttacker() {
        return attacker == null ? null : attacker.resolve();
    }

    public Mob getVictim() {
        return victim == null ? null : victim.resolve();
    }

    public CombatDamageType getType() {
        return type;
    }

    public int getRawAmount() {
        return rawAmount;
    }

    public boolean isAccurate() {
        return accurate;
    }
}
