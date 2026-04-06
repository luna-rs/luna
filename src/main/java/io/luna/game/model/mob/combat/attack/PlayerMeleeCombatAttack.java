package io.luna.game.model.mob.combat.attack;

import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.EquipmentPoisonDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

/**
 * A {@link MeleeCombatAttack} implementation for player-performed melee attacks.
 * <p>
 * This type adds player-specific melee behavior on top of the generic melee attack flow, including poisoned weapon
 * checks and combat experience rewards based on the active melee stance.
 * <p>
 * If the equipped weapon has a matching {@link EquipmentPoisonDefinition}, successful melee hits may apply poison to
 * the victim.
 *
 * @author lare96
 */
public class PlayerMeleeCombatAttack extends MeleeCombatAttack<Player> {

    /**
     * The chance for a successful melee hit from a poisonous weapon to apply poison.
     */
    private static final Rational MELEE_POISON_CHANCE = new Rational(1, 4);

    /**
     * Creates a new {@link PlayerMeleeCombatAttack}.
     *
     * @param attacker The player performing the melee attack.
     * @param victim The mob receiving the melee attack.
     * @param style The combat style to use for calculations.
     */
    public PlayerMeleeCombatAttack(Player attacker, Mob victim, CombatStyleDefinition style) {
        super(attacker, victim, style.getAnimation(), style.getRange(), style.getSpeed());
    }

    /**
     * Creates a new {@link PlayerMeleeCombatAttack}.
     *
     * @param attacker The player performing the melee attack.
     * @param victim The mob receiving the melee attack.
     * @param style The combat style to use for calculations.
     */
    public PlayerMeleeCombatAttack(Player attacker, Mob victim, CombatStyle style) {
        this(attacker, victim, style.getDef());
    }

    /**
     * Creates a new {@link MeleeCombatAttack}.
     *
     * @param attacker The player performing the melee attack.
     * @param victim The mob receiving the melee attack.
     * @param animationId The melee attack animation to play.
     * @param range The range of the melee attack.
     * @param delay The attack delay, in ticks, applied after execution.
     */
    public PlayerMeleeCombatAttack(Player attacker, Mob victim, int animationId, int range, int delay) {
        super(attacker, victim, animationId, range, delay);
    }

    @Override
    public CombatDamage onAttack(CombatDamage damage) {

        // Attempt to poison if our equipped weapon is poisonous and we've dealt damage.
        int rawAmount = damage.getRawAmount();
        int weaponId = attacker.getEquipment().computeIdForIndex(Equipment.WEAPON);
        EquipmentPoisonDefinition poisonDef = EquipmentPoisonDefinition.ALL.get(weaponId).orElse(null);
        if (poisonDef != null && rawAmount > 0 && RandomUtils.roll(MELEE_POISON_CHANCE)) {
            victim.getCombat().setPoisonSeverity(poisonDef.getSeverity());
        }

        // Award XP depending on the combat stance.
        double hitpointsXp = rawAmount * 1.33;
        CombatStance stance = attacker.getCombat().getStance();

        Skill attack = attacker.skill(Skill.ATTACK);
        Skill strength = attacker.skill(Skill.STRENGTH);
        Skill defence = attacker.skill(Skill.DEFENCE);
        if (stance == CombatStance.ACCURATE) {
            // ACCURATE, award Attack XP.
            attack.addExperience(rawAmount * 4);
        } else if (stance == CombatStance.AGGRESSIVE) {
            // AGGRESSIVE, award Strength XP.
            strength.addExperience(rawAmount * 4);
        } else if (stance == CombatStance.DEFENSIVE) {
            // DEFENSIVE, award Defence XP.
            defence.addExperience(rawAmount * 4);
        } else if (stance == CombatStance.CONTROLLED) {
            // CONTROLLED, award Attack, Strength, and Defence XP.
            attack.addExperience(hitpointsXp);
            strength.addExperience(hitpointsXp);
            defence.addExperience(hitpointsXp);
        }
        // Award hitpoints XP regardless.
        attacker.skill(Skill.HITPOINTS).addExperience(hitpointsXp);
        return damage;
    }
}