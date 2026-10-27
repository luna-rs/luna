package io.luna.game.model.mob.combat;

import engine.combat.prayer.CombatPrayer;
import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.def.NpcCombatDefinition;
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
 * Player effective strength includes eight base levels and the appropriate stance bonus. Magic uses its own equipment
 * bonus and fixed attack stance, while NPC accuracy and defence include nine effective levels. Player magic defence
 * rounds the combined weighted Defence and Magic levels once.
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
        return calculateHitChance(attacker, victim, type, 100);
    }

    /**
     * Calculates hit chance after multiplying the offensive roll, before comparing it with the defensive roll.
     * Special attack accuracy bonuses modify this roll rather than adding percentage points to final hit chance.
     * Integer percentage scaling avoids floating-point rounding errors and temporary decimal objects. A long
     * intermediate safely holds the product of the integer base roll and percentage before division by 100.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The damage type being checked.
     * @param attackRollPercent A non-negative percentage; {@code 100} preserves the ordinary roll.
     * @return The resulting hit probability.
     */
    public static double calculateHitChance(Mob attacker, Mob victim, CombatDamageType type,
                                          int attackRollPercent) {
        return calculateHitChance(attacker, victim, type, attackRollPercent, null);
    }

    /**
     * Calculates hit probability with an optional melee defence bonus override. The attacker's selected offensive
     * style and stance still determine the attack roll; only the target's defensive bonus selection is overridden.
     *
     * @param attacker The attacking mob.
     * @param victim The defending mob.
     * @param type The damage type being checked.
     * @param attackRollPercent The non-negative offensive roll percentage.
     * @param meleeDefenceBonus The stab, slash, or crush defence bonus to check, or null for ordinary style matching.
     * @return The resulting hit probability.
     */
    public static double calculateHitChance(Mob attacker, Mob victim, CombatDamageType type,
                                          int attackRollPercent, EquipmentBonus meleeDefenceBonus) {
        if (meleeDefenceBonus != null && (type != CombatDamageType.MELEE ||
                (meleeDefenceBonus != EquipmentBonus.STAB_DEFENCE &&
                        meleeDefenceBonus != EquipmentBonus.SLASH_DEFENCE &&
                        meleeDefenceBonus != EquipmentBonus.CRUSH_DEFENCE))) {
            throw new IllegalArgumentException("Melee defence override must select stab, slash, or crush defence.");
        }
        if (attackRollPercent < 0) {
            throw new IllegalArgumentException("Attack roll percentage must be non-negative.");
        }
        // An NPC's attack style bonus describes its melee attack, so its ranged attacks need their own.
        EquipmentBonus attackStyleBonus = type == CombatDamageType.MAGIC ? EquipmentBonus.MAGIC_ATTACK :
                type == CombatDamageType.RANGED ? EquipmentBonus.RANGED_ATTACK : attacker.getCombat().getAttackStyleBonus();
        double attackRoll = Math.floorDiv((long) calculateAttackRoll(attacker, attackStyleBonus, type) *
                attackRollPercent, 100L);
        double defenceRoll = type == CombatDamageType.MAGIC ?
                calculateMagicDefenceRoll(victim) :
                calculatePhysicalDefenceRoll(victim, meleeDefenceBonus == null ?
                        attackStyleBonus : meleeDefenceBonus.getOpposite());
        return calculateHitChanceFromRolls(attackRoll, defenceRoll);
    }

    /**
     * Captures a player's ranged offensive roll, including current level, prayer, stance, and equipment bonuses.
     * Delayed secondary attacks can retain this value without reading equipment changed after launch.
     *
     * @param attacker The attacking player.
     * @return The ranged offensive roll before any special attack multiplier.
     */
    public static int calculateRangedAttackRoll(Player attacker) {
        return calculateAttackRoll(attacker, EquipmentBonus.RANGED_ATTACK, CombatDamageType.RANGED);
    }

    /**
     * Captures a caster's magic offensive roll using the shared level and equipment formula.
     *
     * @param attacker The casting mob.
     * @return The magic offensive roll before comparing it with a recipient's defence.
     */
    public static int calculateMagicAttackRoll(Mob attacker) {
        return calculateAttackRoll(attacker, EquipmentBonus.MAGIC_ATTACK, CombatDamageType.MAGIC);
    }

    /**
     * Calculates magic hit probability from a captured offensive roll and this recipient's current defence.
     * Area spells retain the caster's original offence while resolving each secondary recipient independently.
     *
     * @param attackRoll The magic offensive roll captured when preparing the primary hit.
     * @param victim The recipient of this hit.
     * @return The resulting hit probability.
     */
    public static double calculateMagicHitChance(int attackRoll, Mob victim) {
        return calculateHitChanceFromRolls(attackRoll, calculateMagicDefenceRoll(victim));
    }

    /**
     * Calculates ranged hit probability using a captured offensive roll and the victim's current defence.
     *
     * @param attackRoll The ranged offensive roll captured before launch.
     * @param victim The target of this hit.
     * @return The resulting hit probability.
     */
    public static double calculateRangedHitChance(int attackRoll, Mob victim) {
        return calculateHitChanceFromRolls(attackRoll,
                calculatePhysicalDefenceRoll(victim, EquipmentBonus.RANGED_ATTACK));
    }

    /**
     * Converts offensive and defensive roll limits into the shared hit probability.
     *
     * @param attackRoll The offensive roll limit.
     * @param defenceRoll The defensive roll limit.
     * @return The resulting hit probability.
     */
    private static double calculateHitChanceFromRolls(double attackRoll, double defenceRoll) {
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
            return (level + 9) * (getOffensiveAccuracyBonus(attacker, attackStyleBonus) + 64);
        }

        Player player = attacker.asPlr();
        return (int) (Math.floor(level * getAttackRollPrayerMultiplier(player, type))
                + (type == CombatDamageType.MAGIC ? 1 : getAttackRollStanceBonus(player)) + 8)
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
            return (level + 9) * (getMatchingPhysicalDefenceBonus(victim, attackStyleBonus) + 64);
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
            double effectiveDefence = Math.floor(player.getSkills().getSkill(Skill.DEFENCE).getLevel() *
                    getDefenceRollPrayerMultiplier(player));
            int effectiveMagicDefence = (int) Math.floor(effectiveDefence * 0.3 +
                    victim.getSkills().getSkill(Skill.MAGIC).getLevel() * 0.7);
            int magicDefenceBonus = player.getEquipment().getBonus(EquipmentBonus.MAGIC_DEFENCE);
            return (effectiveMagicDefence + 8) * (magicDefenceBonus + 64);
        }

        Npc npc = victim.asNpc();
        return (npc.getSkills().getSkill(Skill.MAGIC).getLevel() + 9) *
                (npc.combatDef().getMagicDefenceBonus() + 64);
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

        return Math.floor(player.getSkills().getSkill(skill).getLevel() * prayerMultiplier) + stanceBonus + 8;
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
        if (attacker instanceof Player) {
            return attacker.asPlr().getEquipment().getBonus(attackStyleBonus);
        }
        NpcCombatDefinition def = attacker.asNpc().combatDef();
        return attackStyleBonus == EquipmentBonus.MAGIC_ATTACK ? def.getMagicBonus() :
                attackStyleBonus == EquipmentBonus.RANGED_ATTACK ? def.getRangedBonus() : def.getAttackBonus();
    }

    /**
     * Gets the defensive bonus that opposes the attacker's offensive style.
     *
     * @param victim The defending mob.
     * @param attackStyleBonus The attacker's offensive style bonus.
     * @return The matching defensive bonus.
     */
    private static int getMatchingPhysicalDefenceBonus(Mob victim, EquipmentBonus attackStyleBonus) {
        return victim instanceof Player ?
                victim.asPlr().getEquipment().getBonus(attackStyleBonus.getOpposite()) :
                victim.asNpc().combatDef().getMatchingDefenceBonus(attackStyleBonus);
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
            case AGGRESSIVE:
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
