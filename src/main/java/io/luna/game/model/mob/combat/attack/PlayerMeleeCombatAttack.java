package io.luna.game.model.mob.combat.attack;

import engine.combat.status.hooks.PoisonStatusEffect;
import game.combat.BarrowsEquipmentSet;
import game.player.Sound;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.EquipmentPoisonDefinition;
import io.luna.game.model.def.WeaponSoundDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageRequest;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

/**
 * A {@link MeleeCombatAttack} implementation for player-performed melee attacks.
 * <p>
 * This type adds player-specific melee behavior on top of the generic melee attack flow, including verified launch
 * sound selection, poisoned weapon checks, and combat experience rewards based on the stance captured when the
 * attack begins. Rewards and poison are applied only after damage is delivered and capped to the victim's
 * remaining health.
 * <p>
 * If the equipped weapon has a matching {@link EquipmentPoisonDefinition}, successful melee hits may apply poison to
 * the victim.
 *
 * @author lare96
 */
public class PlayerMeleeCombatAttack extends MeleeCombatAttack<Player> {

    /**
     * The stance captured at attack time for allocating experience when damage is delivered.
     */
    private CombatStance attackStance;

    /**
     * The weapon item id captured when this swing commits, or {@code -1} before commitment or when unarmed.
     * Impact-time equipment restrictions use this value so switching weapons cannot change a launched hit.
     */
    private int launchedWeaponId = -1;

    /**
     * The weapon's poison definition captured at attack time, or {@code null} for an unpoisoned weapon.
     */
    private EquipmentPoisonDefinition poison;

    /**
     * The complete Barrows set captured before resolving this swing.
     */
    private BarrowsEquipmentSet barrowsSet;

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
        super(attacker, victim, attacker.getCombat().getAttackAnimation(CombatDamageType.MELEE),
                style.getRange(), style.getSpeed());
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

    /**
     * Resolves the verified ordinary melee sound from equipment and style before attack processing.
     * Special attack builders override this selection with their configured launch sound.
     *
     * @return The ordinary launch sound, or {@code null} when this weapon or style is unverified.
     */
    @Override
    protected Sound getAttackSound() {
        return WeaponSoundDefinition.resolve(attacker.getEquipment().computeIdForIndex(Equipment.WEAPON),
                attacker.getCombat().getStyleDef(), CombatDamageType.MELEE);
    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        barrowsSet = BarrowsEquipmentSet.find(attacker);
        CombatDamageRequest.Builder request = CombatDamageRequest.builder(attacker, other, CombatDamageType.MELEE);
        if (barrowsSet != null) {
            barrowsSet.modifyMeleeRequest(attacker, request);
        }
        return request.build().resolve();
    }

    @Override
    public CombatDamage onAttack(CombatDamage damage) {
        attackStance = attacker.getCombat().getStance();
        launchedWeaponId = attacker.getEquipment().computeIdForIndex(Equipment.WEAPON);
        poison = EquipmentPoisonDefinition.ALL.get(launchedWeaponId).orElse(null);
        return damage;
    }

    /**
     * Returns the weapon used for the committed swing, independent of the attacker's current equipment.
     *
     * @return The launched weapon item id, or {@code -1} for an unarmed or uncommitted swing.
     */
    public int getLaunchedWeaponId() {
        return launchedWeaponId;
    }

    @Override
    public void onDamageApplied(CombatDamage damage) {
        if (barrowsSet != null) {
            barrowsSet.onDamageApplied(damage);
        }

        // Attempt to poison if our equipped weapon is poisonous and we've dealt damage.
        int rawAmount = Math.max(0, damage.getRawAmount());
        if (poison != null && rawAmount > 0 && RandomUtils.roll(MELEE_POISON_CHANCE)) {
            damage.getVictim().getStatus().add(new PoisonStatusEffect(damage.getVictim(), poison.getSeverity()), false);
        }

        // Award XP depending on the combat stance.
        double experienceDamage = experienceDamage(damage);
        double hitpointsXp = experienceDamage * 1.33;
        CombatStance stance = attackStance;

        Skill attack = attacker.skill(Skill.ATTACK);
        Skill strength = attacker.skill(Skill.STRENGTH);
        Skill defence = attacker.skill(Skill.DEFENCE);
        if (stance == CombatStance.ACCURATE) {
            // ACCURATE, award Attack XP.
            attack.addExperience(experienceDamage * 4);
        } else if (stance == CombatStance.AGGRESSIVE) {
            // AGGRESSIVE, award Strength XP.
            strength.addExperience(experienceDamage * 4);
        } else if (stance == CombatStance.DEFENSIVE) {
            // DEFENSIVE, award Defence XP.
            defence.addExperience(experienceDamage * 4);
        } else if (stance == CombatStance.CONTROLLED) {
            // CONTROLLED, award Attack, Strength, and Defence XP.
            attack.addExperience(hitpointsXp);
            strength.addExperience(hitpointsXp);
            defence.addExperience(hitpointsXp);
        }
        // Award hitpoints XP regardless.
        attacker.skill(Skill.HITPOINTS).addExperience(hitpointsXp);
    }
}
