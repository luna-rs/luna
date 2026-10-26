package io.luna.game.model.mob.combat.attack;

import engine.combat.status.hooks.PoisonStatusEffect;
import game.combat.BarrowsEquipmentSet;
import game.player.Sound;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.Position;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.EquipmentPoisonDefinition;
import io.luna.game.model.def.WeaponSoundDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatFormula;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.combat.state.PlayerCombatContext;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

import java.util.function.BiFunction;

/**
 * A {@link RangedCombatAttack} implementation for ranged attacks performed by {@link Player}s.
 * <p>
 * This specialization selects a verified launch sound and captures the stance and poison source before ammunition
 * is consumed. Experience and poison are applied when damage is delivered, using the damage capped to the victim's
 * remaining health. Equipment changes during projectile flight therefore do not change the captured poison source.
 *
 * @author lare96
 */
public class PlayerRangedCombatAttack extends RangedCombatAttack<Player> {

    /**
     * The stance captured at launch for allocating experience when damage is delivered.
     */
    private CombatStance attackStance;

    /**
     * The ammunition or thrown weapon's poison definition captured before consumption, or {@code null} if unpoisoned.
     */
    private EquipmentPoisonDefinition poison;

    /**
     * The complete Barrows set captured before consuming ammunition.
     */
    private BarrowsEquipmentSet barrowsSet;

    /**
     * The ammunition definition captured before consumption, retained for special attacks and delayed secondary hits.
     */
    private AmmoDefinition launchedAmmo;

    /**
     * The visible Ranged level captured before consumption for secondary-hit damage formulas.
     */
    private int launchedRangedLevel;

    /**
     * The ranged offensive roll captured before consumption, including prayer, stance, and equipment bonuses.
     */
    private int launchedAttackRoll;

    /**
     * The primary target's immutable position captured at launch for secondary-hit searches.
     */
    private Position launchedTargetPosition;

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
        super(attacker, victim, attacker.getCombat().getAttackAnimation(CombatDamageType.RANGED),
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
    protected boolean usesProjectileTiming() {
        return ammo != null;
    }

    /**
     * Resolves the verified ordinary ranged sound from equipment and style before attack processing.
     * Special attack builders override this selection with their configured launch sound.
     *
     * @return The ordinary launch sound, or {@code null} when this weapon or style is unverified.
     */
    @Override
    protected Sound getAttackSound() {
        return WeaponSoundDefinition.resolve(attacker.getEquipment().computeIdForIndex(Equipment.WEAPON),
                attacker.getCombat().getStyleDef(), CombatDamageType.RANGED);
    }

    @Override
    public CombatDamage onAttack(CombatDamage damage) {
        barrowsSet = BarrowsEquipmentSet.find(attacker);
        PlayerCombatContext combat = attacker.getCombat();
        attackStance = combat.getStance();
        AmmoDefinition loadedAmmo = combat.getAmmoDef();
        launchedAmmo = loadedAmmo;
        launchedRangedLevel = attacker.skill(Skill.RANGED).getLevel();
        launchedAttackRoll = CombatFormula.calculateRangedAttackRoll(attacker);
        launchedTargetPosition = victim.getPosition();
        int poisonSlot = loadedAmmo.isAmmoless() ? Equipment.WEAPON : Equipment.AMMUNITION;
        int poisonId = attacker.getEquipment().computeIdForIndex(poisonSlot);
        poison = EquipmentPoisonDefinition.ALL.get(poisonId).orElse(null);
        if (!combat.getRanged().removeAmmo()) {
            // Prevent the attack from continuing if we're lacking arrows or have an incompatible weapon.
            return null;
        }

        return damage;
    }

    @Override
    public void onDamageApplied(CombatDamage damage) {
        super.onDamageApplied(damage);
        if (barrowsSet != null) {
            barrowsSet.onDamageApplied(damage);
        }
        awardDamageExperience(damage);
        int rawAmount = Math.max(0, damage.getRawAmount());
        if (poison != null && rawAmount > 0 && RandomUtils.roll(RANGED_POISON_CHANCE)) {
            damage.getVictim().getStatus().add(new PoisonStatusEffect(damage.getVictim(), poison.getSeverity()), false);
        }
    }

    /**
     * Awards experience for delivered damage using the stance captured when this attack launched. Secondary hits can
     * reuse this allocation without repeating poison rolls or special attack arrival callbacks.
     *
     * @param damage The applied damage, already capped to the victim's remaining health.
     */
    public final void awardDamageExperience(CombatDamage damage) {
        int rawAmount = Math.max(0, damage.getRawAmount());
        Skill ranged = attacker.skill(Skill.RANGED);
        if (attackStance == CombatStance.DEFENSIVE) {
            // Defensive ranged shares experience between RANGED and DEFENCE.
            ranged.addExperience(rawAmount * 2);
            attacker.skill(Skill.DEFENCE).addExperience(rawAmount * 2);
        } else {
            // Standard ranged styles award all combat experience to RANGED.
            ranged.addExperience(rawAmount * 4);
        }
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
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

    /**
     * Gets the ammunition used by this attack before its stack was decremented. Unlike live equipment state, this
     * snapshot survives firing the last thrown weapon and equipment changes during projectile flight.
     *
     * @return The launched ammunition definition, or {@code null} before attack processing begins.
     */
    public AmmoDefinition getLaunchedAmmo() {
        return launchedAmmo;
    }

    /**
     * @return The visible Ranged level captured during attack processing before ammunition consumption.
     */
    public int getLaunchedRangedLevel() {
        return launchedRangedLevel;
    }

    /**
     * @return The ranged offensive roll captured during attack processing before ammunition consumption.
     */
    public int getLaunchedAttackRoll() {
        return launchedAttackRoll;
    }

    /**
     * Gets the primary target's position before projectile flight. Movement by the target does not move this snapshot.
     *
     * @return The target position captured during attack processing, or {@code null} before processing begins.
     */
    public Position getLaunchedTargetPosition() {
        return launchedTargetPosition;
    }
}
