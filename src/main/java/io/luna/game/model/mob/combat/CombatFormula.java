package io.luna.game.model.mob.combat;

import engine.combat.prayer.CombatPrayer;
import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.combat.state.PlayerCombatContext;
import io.luna.util.RandomUtils;

/**
 * Utility class containing shared combat formula helpers.
 * <p>
 * This class centralizes the common roll, stance, prayer, and equipment-bonus calculations used by combat accuracy and
 * physical max-hit logic.
 *
 * @author lare96
 * @see <a href="https://rune-server.org/threads/archive-combat-formulas.688072/">
 * OSRS combat formulas reference
 * </a>
 */
public final class CombatFormula {

    /**
     * Represents the supported physical combat families that use the shared physical max-hit formula.
     */
    public enum PhysicalType {

        /**
         * Ranged combat.
         */
        RANGED,

        /**
         * Melee combat.
         */
        MELEE
    }

    /**
     * Creates a new {@link CombatFormula}.
     * <p>
     * This constructor is private because this is a static utility class.
     */
    private CombatFormula() {

    }

    /**
     * Rolls whether an attack successfully lands using {@link #calculateHitChance(Mob, Mob, CombatDamageType)}.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat damage type being checked.
     * @return {@code true} if the attack lands successfully, otherwise {@code false}.
     */
    public static boolean isAccurateHit(Mob attacker, Mob victim, CombatDamageType type) {
        return RandomUtils.nextDouble() <= calculateHitChance(attacker, victim, type);
    }

    /**
     * Calculates the percentage that an attack successfully lands.
     * <p>
     * This method computes the attacker's offensive roll, computes the defender's relevant defensive roll, and converts
     * the two rolls into a hit chance.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat damage type being checked.
     * @return The hit chance percentage.
     */
    public static double calculateHitChance(Mob attacker, Mob victim, CombatDamageType type) {
        EquipmentBonus attackStyleBonus = attacker.getCombat().getAttackStyleBonus();
        double attackRoll = calculateAttackRoll(attacker, attackStyleBonus, type);
        double defenceRoll = type == CombatDamageType.MAGIC ?
                calculateMagicDefenceRoll(victim) :
                calculatePhysicalDefenceRoll(victim, attackStyleBonus);
        double hitChance;
        if (attackRoll > defenceRoll) {
            hitChance = 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
        } else {
            hitChance = attackRoll / (2.0 * (defenceRoll + 1.0));
        }
        return hitChance;
    }

    /**
     * Calculates a player's maximum physical hit.
     * <p>
     * This applies the standard physical max-hit calculation using the player's effective strength level and the
     * relevant physical damage bonus.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The maximum physical hit that can be dealt.
     */
    public static int calculatePhysicalMaxHit(Player player, PhysicalType type) {
        double effectiveStrength = calculateEffectivePhysicalStrength(player, type);
        double strengthBonus = getPhysicalDamageBonus(player, type);
        int maxHit = (int) Math.floor(0.5 + effectiveStrength * (strengthBonus + 64.0) / 640.0);
        if (maxHit < 1) {
            maxHit = 1;
        }
        return maxHit;
    }

    /**
     * Calculates an attacker's offensive roll.
     * <p>
     * NPCs use their relevant visible combat level and offensive equipment bonus directly. Players additionally
     * apply prayer and stance modifiers before the final offensive bonus multiplier is applied.
     *
     * @param attacker The attacking mob.
     * @param attackStyleBonus The offensive bonus associated with the current attack style.
     * @param type The combat damage type being used.
     * @return The calculated offensive roll.
     */
    private static int calculateAttackRoll(Mob attacker, EquipmentBonus attackStyleBonus, CombatDamageType type) {
        int skill;
        if (type == CombatDamageType.MAGIC) {
            skill = Skill.MAGIC;
        } else if (type == CombatDamageType.RANGED) {
            skill = Skill.RANGED;
        } else {
            skill = Skill.ATTACK;
        }

        int level = attacker.getSkills().getSkill(skill).getLevel();
        if (attacker instanceof Npc) {
            return (level + 8) * (getOffensiveAccuracyBonus(attacker, attackStyleBonus) + 64);
        }

        Player player = attacker.asPlr();
        return (int) (Math.floor(level * getAttackRollPrayerMultiplier(player, type))
                + getAttackRollStanceBonus(player) + 8)
                * (getOffensiveAccuracyBonus(player, attackStyleBonus) + 64);
    }

    /**
     * Calculates a defender's physical defence roll against a non-magic attack.
     * <p>
     * NPCs use their visible Defence level directly. Players additionally apply defence prayer and stance modifiers
     * before the matching defensive equipment bonus is applied.
     *
     * @param victim The defending mob.
     * @param attackStyleBonus The attacker's offensive style bonus, used to determine the opposing defensive bonus.
     * @return The calculated physical defence roll.
     */
    private static int calculatePhysicalDefenceRoll(Mob victim, EquipmentBonus attackStyleBonus) {
        int level = victim.getSkills().getSkill(Skill.DEFENCE).getLevel();
        if (victim instanceof Npc) {
            return level * (getMatchingPhysicalDefenceBonus(victim, attackStyleBonus) + 64);
        }

        Player victimPlr = victim.asPlr();
        return (int) (Math.floor(level * getDefenceRollPrayerMultiplier(victimPlr))
                + getDefenceRollStanceBonus(victimPlr) + 8)
                * (getMatchingPhysicalDefenceBonus(victimPlr, attackStyleBonus) + 64);
    }

    /**
     * Calculates a defender's magic defence roll.
     * <p>
     * Player magic defence is derived from both Defence and Magic, using a weighted split of 30% Defence and
     * 70% Magic, before applying the magic defence equipment bonus. NPC magic defence is derived from Magic and
     * magic-defence bonus only.
     *
     * @param victim The defending mob.
     * @return The calculated magic defence roll.
     */
    private static int calculateMagicDefenceRoll(Mob victim) {
        if (victim instanceof Player) {
            Player player = victim.asPlr();
            int effectiveDefence = (int) Math.floor(
                    Math.floor(player.getSkills().getSkill(Skill.DEFENCE).getLevel() *
                            getDefenceRollPrayerMultiplier(player)) * 0.3
            );
            int effectiveMagic = (int) Math.floor(victim.getSkills().getSkill(Skill.MAGIC).getLevel() * 0.7);
            int magicDefenceBonus = player.getEquipment().getBonus(EquipmentBonus.MAGIC_DEFENCE);
            return (effectiveDefence + effectiveMagic + 8) * (magicDefenceBonus + 64);
        }

        Npc npc = victim.asNpc();
        return (npc.getSkills().getSkill(Skill.MAGIC).getLevel() + 8) *
                (npc.getCombatDef().getBonus(EquipmentBonus.MAGIC_DEFENCE) + 64);
    }

    /**
     * Calculates the effective physical strength level used by the physical max-hit formula.
     * <p>
     * This value is derived from the relevant visible skill level, the applicable prayer multiplier, and the
     * stance-based strength bonus.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The effective physical strength level.
     */
    private static double calculateEffectivePhysicalStrength(Player player, PhysicalType type) {
        int skill = type == PhysicalType.RANGED ? Skill.RANGED : Skill.STRENGTH;
        double prayerMultiplier = getPhysicalStrengthPrayerMultiplier(player, type == PhysicalType.RANGED ?
                CombatDamageType.RANGED : CombatDamageType.MELEE);
        int stanceBonus = getPhysicalStrengthStanceBonus(player, type);

        return Math.floor(player.getSkills().getSkill(skill).getLevel() * prayerMultiplier) + stanceBonus;
    }

    /**
     * Gets the offensive accuracy bonus for the current attack.
     * <p>
     * The supplied {@code attackStyleBonus} determines which offensive bonus is read from equipment or NPC combat
     * definition.
     *
     * @param attacker The attacking mob.
     * @param attackStyleBonus The offensive equipment bonus to read.
     * @return The offensive accuracy bonus.
     */
    private static int getOffensiveAccuracyBonus(Mob attacker, EquipmentBonus attackStyleBonus) {
        return attacker instanceof Player ?
                attacker.asPlr().getEquipment().getBonus(attackStyleBonus) :
                attacker.asNpc().getCombatDef().getBonus(attackStyleBonus);
    }

    /**
     * Gets the defensive bonus that opposes the attacker's offensive style.
     *
     * @param victim The defending mob.
     * @param attackStyleBonus The attacker's offensive style bonus.
     * @return The matching defensive bonus.
     */
    private static int getMatchingPhysicalDefenceBonus(Mob victim, EquipmentBonus attackStyleBonus) {
        EquipmentBonus defenceBonus = attackStyleBonus.getOpposite();
        return victim instanceof Player ?
                victim.asPlr().getEquipment().getBonus(defenceBonus) :
                victim.asNpc().getCombatDef().getBonus(defenceBonus);
    }

    /**
     * Gets the physical damage bonus used by the physical max-hit formula.
     * <p>
     * Melee uses the equipped strength bonus. Ranged uses the strength value of the currently loaded ammunition.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The physical damage bonus.
     * @throws IllegalStateException If ranged damage is requested without ammo loaded.
     */
    private static int getPhysicalDamageBonus(Player player, PhysicalType type) {
        PlayerCombatContext combat = player.getCombat();

        if (type == PhysicalType.MELEE) {
            return player.getEquipment().getBonus(EquipmentBonus.STRENGTH);
        } else if (combat.getRanged().getAmmo() != null) {
            return combat.getRanged().getAmmo().getStrength();
        }

        throw new IllegalStateException("Missing ranged ammo for ranged strength bonus.");
    }

    /**
     * Gets the stance bonus applied to a player's attack roll.
     *
     * @param player The attacking player.
     * @return The stance-based attack bonus.
     */
    private static int getAttackRollStanceBonus(Player player) {
        CombatStance stance = player.getCombat().getStyleDef().getStance();

        if (stance == CombatStance.ACCURATE) {
            return 3;
        } else if (stance == CombatStance.CONTROLLED) {
            return 1;
        }
        return 0;
    }

    /**
     * Gets the stance bonus applied to a player's defence roll.
     *
     * @param player The defending player.
     * @return The stance-based defence bonus.
     */
    private static int getDefenceRollStanceBonus(Player player) {
        CombatStance stance = player.getCombat().getStyleDef().getStance();

        if (stance == CombatStance.DEFENSIVE) {
            return 3;
        } else if (stance == CombatStance.CONTROLLED) {
            return 1;
        }
        return 0;
    }

    /**
     * Gets the stance bonus applied to effective physical strength.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The stance-based physical strength bonus.
     */
    private static int getPhysicalStrengthStanceBonus(Player player, PhysicalType type) {
        CombatStance stance = player.getCombat().getStyleDef().getStance();

        if (type == PhysicalType.RANGED) {
            return stance == CombatStance.ACCURATE ? 3 : 0;
        }

        switch (stance) {
            case ACCURATE:
                return 3;
            case CONTROLLED:
                return 1;
            default:
                return 0;
        }
    }

    /**
     * Gets the prayer multiplier applied to attack-roll calculations.
     *
     * @param player The player whose active prayers are checked.
     * @param type The combat damage type being used.
     * @return The attack-roll prayer multiplier.
     */
    private static double getAttackRollPrayerMultiplier(Player player, CombatDamageType type) {
        CombatPrayerSet prayers = player.getCombat().getPrayers();

        if (type == CombatDamageType.MELEE) {
            if (prayers.isActive(CombatPrayer.CLARITY_OF_THOUGHT)) {
                return 1.05;
            } else if (prayers.isActive(CombatPrayer.IMPROVED_REFLEXES)) {
                return 1.10;
            } else if (prayers.isActive(CombatPrayer.INCREDIBLE_REFLEXES)) {
                return 1.15;
            }
        }
        return 1.0;
    }

    /**
     * Gets the prayer multiplier applied to defence-roll calculations.
     *
     * @param player The player whose active prayers are checked.
     * @return The defence-roll prayer multiplier.
     */
    private static double getDefenceRollPrayerMultiplier(Player player) {
        CombatPrayerSet prayers = player.getCombat().getPrayers();

        if (prayers.isActive(CombatPrayer.THICK_SKIN)) {
            return 1.05;
        } else if (prayers.isActive(CombatPrayer.ROCK_SKIN)) {
            return 1.10;
        } else if (prayers.isActive(CombatPrayer.STEEL_SKIN)) {
            return 1.15;
        }
        return 1.0;
    }

    /**
     * Gets the prayer multiplier applied to physical strength calculations.
     *
     * @param player The player whose active prayers are checked.
     * @param type The combat damage type being used for the strength calculation.
     * @return The physical strength prayer multiplier.
     */
    private static double getPhysicalStrengthPrayerMultiplier(Player player, CombatDamageType type) {
        CombatPrayerSet prayers = player.getCombat().getPrayers();

        if (type == CombatDamageType.MELEE) {
            if (prayers.isActive(CombatPrayer.BURST_OF_STRENGTH)) {
                return 1.05;
            } else if (prayers.isActive(CombatPrayer.SUPERHUMAN_STRENGTH)) {
                return 1.10;
            } else if (prayers.isActive(CombatPrayer.ULTIMATE_STRENGTH)) {
                return 1.15;
            }
        }
        return 1.0;
    }
}