package io.luna.game.model.mob.combat;

import engine.combat.prayer.CombatPrayer;
import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.util.RandomUtils;

/**
 * Static combat formula helpers for hit accuracy and max-hit calculations.
 *
 * @author lare96
 * @link <a href="https://rune-server.org/threads/archive-combat-formulas.688072/">OSRS Combat Formulas</a>
 */
public final class CombatFormula {

    /**
     * The physical combat families supported by the shared physical max-hit formula.
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
     * Rolls a physical or magic hit from {@code 0} to the player's current maximum hit, inclusive.
     *
     * @param mob The attacking mob.
     * @return The rolled physical or magic damage.
     */
    public static int rollHit(Mob mob, CombatDamageType type) {
        return RandomUtils.inclusive(mob.getCombat().computeMaxHit(type));
    }

    /**
     * Rolls a magic hit from {@code 0} to the player's current magical maximum hit, inclusive.
     *
     * @param player The attacking player.
     * @return The rolled magical damage.
     */
    public static int rollMagicHit(Player player) {
        return RandomUtils.inclusive(computeMagicalMaxHit(player));
    }

    /**
     * Computes the player's current magical maximum hit.
     * <p>
     * This derives max-hit from the selected spell, autocast state, and any spell-specific modifiers.
     *
     * @param player The attacking player.
     * @return The current magical maximum hit.
     */
    public static int computeMagicalMaxHit(Player player) {
        // TODO Verify Asteria 3.0 magic data and import it properly.
        // TODO Resolve selected/autocast spell from CombatContext.
        // TODO Use spell-specific max hits and modifiers.
        return 10;
    }

    /**
     * Rolls a physical hit from {@code 0} to the player's current physical maximum hit, inclusive.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The rolled physical damage.
     */
    public static int rollPhysicalHit(Player player, PhysicalType type) {
        return RandomUtils.inclusive(computePhysicalMaxHit(player, type));
    }

    /**
     * Computes the maximum physical hit for melee or ranged.
     * <p>
     * This uses the standard effective-strength and equipment-strength based formula.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The maximum physical hit.
     */
    public static int computePhysicalMaxHit(Player player, PhysicalType type) {
        double effectiveStrength = computePhysicalEffectiveStrength(player, type);
        double strengthBonus = getPhysicalStrengthBonus(player);
        // TODO Apply special attack modifiers.
        return (int) Math.floor(0.5d + effectiveStrength * (strengthBonus + 64d) / 640d);
    }

    /**
     * Rolls whether an attack lands successfully.
     * <p>
     * This compares the attacker's attack roll against the defender's defence roll and then converts that comparison
     * into a final hit chance.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The combat damage type being rolled.
     * @return {@code true} if the attack is accurate.
     */
    public static boolean rollAccuracy(Mob attacker, Mob victim, CombatDamageType type) {
        double attackRoll = computeAttackRoll(attacker, type);
        double defenceRoll = type == CombatDamageType.MAGIC && victim instanceof Player ?
                computeMagicDefenceRoll(attacker, (Player) victim) :
                computeDefenceRoll(attacker, victim, type);

        double hitChance;
        if (attackRoll > defenceRoll) {
            hitChance = 1d - (defenceRoll + 2d) / (2d * (attackRoll + 1d));
        } else {
            hitChance = attackRoll / (2d * (defenceRoll + 1d));
        }
        return RandomUtils.nextDouble() <= hitChance;
    }

    /**
     * Computes the effective physical strength level used by the physical max-hit formula.
     * <p>
     * This is derived from the relevant visible skill level, the applicable prayer multiplier, and the combat stance
     * strength bonus.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The effective physical strength level.
     */
    public static double computePhysicalEffectiveStrength(Player player, PhysicalType type) {
        int skill = type == PhysicalType.RANGED ? Skill.RANGED : Skill.STRENGTH;
        double prayerMultiplier = getStrengthPrayerMultiplier(player,
                type == PhysicalType.RANGED ? CombatDamageType.RANGED : CombatDamageType.MELEE);
        int stanceBonus = getPhysicalStrengthStanceBonus(player, type);

        return Math.floor(player.getSkills().getSkill(skill).getLevel() * prayerMultiplier) + stanceBonus;
    }

    /**
     * Returns the stance bonus applied to effective physical strength.
     *
     * @param player The attacking player.
     * @param type The physical combat family being used.
     * @return The stance strength bonus.
     */
    public static int getPhysicalStrengthStanceBonus(Player player, PhysicalType type) {
        CombatStance stance = player.getCombat().getWeapon().getStyleDef().getStance();

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
     * Returns the equipment strength bonus used by the physical max-hit formula.
     * <p>
     * The exact bonus read depends on the active weapon style's configured offensive bonus type.
     *
     * @param player The attacking player.
     * @return The physical strength bonus.
     */
    public static int getPhysicalStrengthBonus(Player player) {
        EquipmentBonus bonus = player.getCombat().getWeapon().getStyleDef().getBonus();
        return player.getEquipment().getBonus(bonus);
    }

    /**
     * Computes the maximum offensive attack roll for the given combat type.
     * <p>
     * NPCs currently use their relevant skill level directly. Players additionally apply prayer, stance, and
     * equipment bonuses.
     *
     * @param attacker The attacking mob.
     * @param type The combat damage type being used.
     * @return The maximum attack roll.
     */
    public static int computeAttackRoll(Mob attacker, CombatDamageType type) {
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
            return level;
        }

        Player player = (Player) attacker;
        return (int) (Math.floor(level * getAttackPrayerMultiplier(player, type))
                + getAttackStanceBonus(player) + 8)
                * (getAttackBonus(player) + 64);
    }

    /**
     * Computes the maximum defensive roll against an incoming hit.
     * <p>
     * NPCs currently use their Defence level directly. Players additionally apply prayer, stance, and defensive
     * equipment bonuses.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The incoming combat damage type.
     * @return The maximum defence roll.
     */
    public static int computeDefenceRoll(Mob attacker, Mob victim, CombatDamageType type) {
        int level = victim.getSkills().getSkill(Skill.DEFENCE).getLevel();
        if (victim instanceof Npc) {
            return level;
        }
        Player victimPlr = (Player) victim;
        int defenceBonus = attacker instanceof Player ? getDefenceBonus((Player) attacker, victimPlr) : 0;
        return (int) (Math.floor(level * getDefencePrayerMultiplier(victimPlr))
                + getDefenceStanceBonus(victimPlr, type) + 8)
                * (defenceBonus + 64);
    }

    /**
     * Computes the special player magic-defence roll.
     * <p>
     * Player magic defence is derived from both Defence and Magic, using the usual weighted split of 30% Defence and
     * 70% Magic before the final equipment-style multiplier is applied.
     *
     * @param attacker The attacking mob.
     * @param victim The defending player.
     * @return The magic defence roll.
     */
    public static int computeMagicDefenceRoll(Mob attacker, Player victim) {
        int defenceLevel = (int) Math.floor(computeDefenceRoll(attacker, victim, CombatDamageType.MAGIC) * 0.3d);
        int magicLevel = (int) Math.floor(victim.getSkills().getSkill(Skill.MAGIC).getLevel() * 0.7d);
        return (defenceLevel + magicLevel) * (magicLevel + 64);
    }

    /**
     * Returns the offensive equipment accuracy bonus for the attacking player.
     * <p>
     * The bonus used is determined by the active weapon style's configured offensive bonus type.
     *
     * @param attacker The attacking player.
     * @return The offensive equipment accuracy bonus.
     */
    public static int getAttackBonus(Player attacker) {
        EquipmentBonus bonus = attacker.getCombat().getWeapon().getStyleDef().getBonus();
        return attacker.getEquipment().getBonus(bonus);
    }

    /**
     * Returns the defensive equipment bonus that opposes the attacker's current offensive bonus.
     *
     * @param attacker The attacking player.
     * @param victim The defending player.
     * @return The matching defensive bonus.
     */
    public static int getDefenceBonus(Player attacker, Player victim) {
        EquipmentBonus attackBonus = attacker.getCombat().getAttackStyleBonus();
        return victim.getEquipment().getBonus(attackBonus.getOpposite());
    }

    /**
     * Returns the offensive stance bonus used in attack-roll calculations.
     *
     * @param player The attacking player.
     * @return The offensive stance bonus.
     */
    public static int getAttackStanceBonus(Player player) {
        CombatStance stance = player.getCombat().getWeapon().getStyleDef().getStance();

        if (stance == CombatStance.ACCURATE) {
            return 3;
        } else if (stance == CombatStance.CONTROLLED) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns the defensive stance bonus used in defence-roll calculations.
     *
     * @param player The defending player.
     * @param type The incoming combat damage type.
     * @return The defensive stance bonus.
     */
    public static int getDefenceStanceBonus(Player player, CombatDamageType type) {
        CombatStance stance = player.getCombat().getWeapon().getStyleDef().getStance();

        if (stance == CombatStance.DEFENSIVE) {
            return 3;
        } else if (stance == CombatStance.CONTROLLED) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns the prayer multiplier applied to physical max-hit calculations.
     *
     * @param player The player whose active prayers are checked.
     * @param type The physical combat damage type.
     * @return The physical strength prayer multiplier.
     */
    public static double getStrengthPrayerMultiplier(Player player, CombatDamageType type) {
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

    /**
     * Returns the prayer multiplier applied to offensive attack rolls.
     *
     * @param player The player whose active prayers are checked.
     * @param type The combat damage type.
     * @return The offensive prayer multiplier.
     */
    public static double getAttackPrayerMultiplier(Player player, CombatDamageType type) {
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
     * Returns the prayer multiplier applied to defensive rolls.
     *
     * @param player The player whose active prayers are checked.
     * @return The defensive prayer multiplier.
     */
    public static double getDefencePrayerMultiplier(Player player) {
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
}