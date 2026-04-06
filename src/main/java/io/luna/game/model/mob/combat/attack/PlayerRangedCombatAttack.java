package io.luna.game.model.mob.combat.attack;

import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.EquipmentPoisonDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.AmmoType;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.state.PlayerCombatContext;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

import java.util.function.BiFunction;

/**
 * A {@link RangedCombatAttack} implementation for ranged attacks performed by {@link Player}s.
 * <p>
 * This specialization applies player-specific ranged behavior on top of the generic projectile attack flow. Poison
 * handling is performed after the projectile reaches the victim and only after the base ranged-hit behavior has already
 * completed.
 *
 * @author lare96
 */
public class PlayerRangedCombatAttack extends RangedCombatAttack<Player> {

    /**
     * The chance for a successful poisonous ranged hit to apply poison to the victim.
     */
    private static final Rational RANGED_POISON_CHANCE = new Rational(1, 8);

    /**
     * The active ranged combat style definition used for animation and attack speed.
     */
    protected final CombatStyleDefinition style;

    /**
     * The resolved ammunition definition used for graphics, projectile data, and poison source resolution.
     */
    protected final AmmoDefinition ammo;

    /**
     * Creates a new {@link PlayerRangedCombatAttack} using the supplied resolved combat style and ammunition
     * definitions.
     *
     * @param attacker The player performing the ranged attack.
     * @param victim The mob receiving the ranged attack.
     * @param style The active combat style definition that supplies the animation and attack speed.
     * @param ammo The ammo definition that supplies the projectile and graphics data.
     */
    public PlayerRangedCombatAttack(Player attacker, Mob victim, CombatStyleDefinition style, AmmoDefinition ammo) {
        super(attacker, victim, ammo.getType() == AmmoType.BOLT_RACK ? 2075 : style.getAnimation(),
                ammo.getStartGraphic(), ammo.getProjectile(), ammo.getEndGraphic(),
                style.getSpeed(), style.getRange());
        this.style = style;
        this.ammo = ammo;
    }

    /**
     * Creates a new {@link PlayerRangedCombatAttack} using the attacker's currently resolved ranged combat style and
     * ammunition definition.
     *
     * @param attacker The player performing the ranged attack.
     * @param victim The mob receiving the ranged attack.
     */
    public PlayerRangedCombatAttack(Player attacker, Mob victim) {
        this(attacker, victim, attacker.getCombat().getStyleDef(), attacker.getCombat().getAmmoDef());
    }

    /**
     * Creates a new {@link RangedCombatAttack}.
     *
     * @param attacker The player performing the ranged attack.
     * @param victim The mob receiving the ranged attack.
     * @param animationId The firing animation identifier.
     * @param start The graphic displayed on the attacker when the attack begins, or {@code null} if none.
     * @param projectileFunction Creates the projectile to display from attacker to victim, or {@code null} if none.
     * @param end The graphic displayed on the victim when the hit lands, or {@code null} if none.
     * @param speed The attack delay, in ticks, applied after execution.
     * @param range The attack range.
     */
    public PlayerRangedCombatAttack(Player attacker, Mob victim, int animationId, Graphic start,
                                    BiFunction<Mob, Mob, LocalProjectile> projectileFunction, Graphic end,
                                    int speed, int range) {
        super(attacker, victim, animationId, start, projectileFunction, end, speed, range);
        style = null;
        ammo = null;
    }

    @Override
    public CombatDamage onAttack(CombatDamage damage) {
        PlayerCombatContext combat = attacker.getCombat();
        if (!combat.getRanged().removeAmmo()) {
            // Prevent the attack from continuing if we're lacking arrows or have an incompatible weapon.
            return null;
        }

        // TODO Correct weapon sound. Think this can be done with other combat weapons though through JSON.

        int rawAmount = damage.getRawAmount();
        Skill ranged = attacker.skill(Skill.RANGED);
        if (combat.getStyleDef().getStance() == CombatStance.DEFENSIVE) {
            // Defensive ranged shares experience between RANGED and DEFENCE.
            ranged.addExperience(rawAmount * 2);
            attacker.skill(Skill.DEFENCE).addExperience(rawAmount * 2);
        } else {
            // Standard ranged styles award all combat experience to RANGED.
            ranged.addExperience(rawAmount * 4);
        }
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
        return damage;
    }

    @Override
    public void onProjectileReached() {
        super.onProjectileReached(); // Call superclass no matter what.

        int index = -1;
        if (ammo != null) {
            index = ammo.isAmmoless() ? Equipment.WEAPON :
                    ammo.isNeedsWeapon() ? Equipment.AMMUNITION : -1;
        }
        if (index == -1) {
            // No valid poison source could be determined for this ammo configuration.
            return;
        }

        // Attempt to poison if our ammo is poisonous and we've dealt damage.
        int id = attacker.getEquipment().computeIdForIndex(index);
        EquipmentPoisonDefinition poisonDef = EquipmentPoisonDefinition.ALL.get(id).orElse(null);
        if (poisonDef != null && nextDamage.getRawAmount() > 0 && RandomUtils.roll(RANGED_POISON_CHANCE)) {
            victim.getCombat().setPoisonSeverity(poisonDef.getSeverity());
        }
    }

    /**
     * @return The active ranged combat style definition used for animation and attack speed.
     */
    public CombatStyleDefinition getStyle() {
        return style;
    }

    /**
     * @return The resolved ammunition definition used for graphics, projectile data, and poison source resolution.
     */
    public AmmoDefinition getAmmo() {
        return ammo;
    }
}