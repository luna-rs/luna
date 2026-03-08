package io.luna.game.model.mob.combat;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Hit.HitType;
import io.luna.util.RandomUtils;

public final class CombatDamage {
    // apply specfic damage
    public static CombatDamage simple(int damage, HitType hitType, CombatDamageType damageType) {
        return new CombatDamage(damage, hitType, damageType);
    }

    // apply damage based on target
    public static CombatDamage calculated(Mob target, HitType hitType, CombatDamageType damageType) {
        int maxHit = -1;
        return new CombatDamage(RandomUtils.inclusive(maxHit), hitType, damageType);
    }

    // all hit formulas and shit go here


    // ^^^^

    private final int amount;
    private final HitType hitType;
    private final CombatDamageType damageType;

    private CombatDamage(int amount, HitType hitType, CombatDamageType damageType) {
        this.amount = amount;
        this.hitType = hitType;
        this.damageType = damageType;
    }

    public int getAmount() {
        // perform calcs here and cache result in amount afterwards?
        // but we also need to ensure its not computed until exactly the right time. so add some implicit function to lock
        // or unlock?
        // maybe need one type for pending hit on a player and another for resolved hits?
        return amount;
    }

    public HitType getHitType() {
        return hitType;
    }

    public CombatDamageType getDamageType() {
        return damageType;
    }
}
