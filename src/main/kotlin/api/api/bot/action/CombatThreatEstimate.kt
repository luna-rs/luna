package api.bot.action

import api.bot.action.eating.MonsterMemory.Companion.monsterMemory
import api.combat.npc.NpcCombatHandler
import engine.combat.prayer.CombatPrayer
import game.combat.BarrowsEquipmentSet
import game.combat.npcHooks.dragon.KingBlackDragonBreath
import game.content.barrows.BarrowsCombat
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor

/**
 * An ordinary-attack estimate in native game ticks, without selecting or executing an attack.
 * Custom attacks, special attacks, status effects and movement can make the estimate incomplete.
 *
 * @property damagePerTick Expected damage after accuracy and applicable protection prayers.
 * @property maximumHit The largest estimated single hit, for a separate burst-danger check.
 * @property uncertain Whether missing definitions or custom attacks require a safety margin.
 * @author lare96
 */
internal data class CombatThreatEstimate(val damagePerTick: Double, val maximumHit: Int, val uncertain: Boolean = false)

/**
 * Ten live Prayer points are the conservative reserve required to credit full standard-NPC protection.
 * Below this reserve, at least half the ordinary hit remains in the estimate to account for imminent exhaustion;
 * potion stock and future prayer upkeep are not predicted.
 */
private const val PROTECTION_PRAYER_RESERVE = 10

/**
 * Estimates the selected discipline against actual worn equipment, without rolls, messages, consumption or searches.
 * Live estimates reuse native formulas and check the attacking bot's current ammunition and spell costs. Capability
 * estimates use static skills, neutral prayers and stances, and ignore depleted consumables; they describe inherent
 * difficulty rather than the temporary state of an opponent that has just died. The neutral formulas deliberately
 * approximate ordinary attacks and omit temporary Charge, special attacks and custom defence hooks. Dharok uses
 * the native low-health damage bonus, with a fixed one-hitpoint envelope for health-independent capability checks.
 *
 * @param attacker The mob whose outgoing danger is being estimated.
 * @param defender The mob receiving the hypothetical attack.
 * @param capability Whether to ignore temporary stat, prayer and consumable state.
 * @param checkSupplies Whether this is the evaluating bot's own attack, allowing its private spell costs to be read.
 * @return A bounded estimate, marked uncertain when necessary data is unavailable.
 */
internal fun estimateCombatThreat(attacker: Mob, defender: Mob, capability: Boolean,
                                  checkSupplies: Boolean = false): CombatThreatEstimate {
    if (attacker is Npc && npcDefinition(attacker) == null || defender is Npc && npcDefinition(defender) == null) {
        return unknownThreat(attacker, defender)
    }
    return try {
        when (attacker) {
            is Player -> estimatePlayer(attacker, defender, capability, checkSupplies)
            is Npc -> estimateNpc(attacker, defender, capability)
            else -> unknownThreat(attacker, defender)
        }
    } catch (_: IllegalStateException) {
        unknownThreat(attacker, defender)
    } catch (_: NullPointerException) {
        // Some definition-dependent native getters reject an uninitialized equipment/style profile.
        unknownThreat(attacker, defender)
    } catch (_: NoSuchElementException) {
        unknownThreat(attacker, defender)
    }
}

private fun npcDefinition(npc: Npc): NpcCombatDefinition? = NpcCombatDefinition.ALL.get(npc.id).orElse(null)

private fun estimatePlayer(player: Player, defender: Mob, capability: Boolean,
                           checkSupplies: Boolean): CombatThreatEstimate {
    val magic = player.combat.magic
    val spell = magic.selectedSpell?.takeIf { it != CombatSpellDefinition.NONE }
        ?: magic.autocastSpell?.takeIf { magic.isAutocasting && magic.canAutocast(it) }
    if (spell != null) {
        if (!capability && checkSupplies && player is Bot && (player.spellbook != spell.spellbook ||
                Magic.spellCost(player, spell.level, spell.required) !is SpellCost.Castable)) {
            return CombatThreatEstimate(0.0, 0)
        }
        val maximum = if (!capability) magic.getMaxHit(spell) else when (spell.spell) {
            CombatSpell.MAGIC_DART -> 10 + level(player, Skill.MAGIC, true) / 10
            CombatSpell.WIND_BOLT, CombatSpell.WATER_BOLT, CombatSpell.EARTH_BOLT, CombatSpell.FIRE_BOLT ->
                spell.maxHit + if (player.equipment.computeIdForIndex(Equipment.HANDS) == 777) 3 else 0
            else -> spell.maxHit
        }
        return estimateStyle(player, defender, CombatDamageType.MAGIC, maximum, 5, capability)
    }
    val ranged = player.combat.weapon.isRanged
    val type = if (ranged) CombatDamageType.RANGED else CombatDamageType.MELEE
    val ammo = player.combat.ranged.ammo
    val weaponId = player.equipment.computeIdForIndex(Equipment.WEAPON)
    val usableAmmo = ammo != null && ammo != AmmoDefinition.UNEQUIPPED &&
        weaponId in ammo.weapons && (ammo.isAmmoless ||
        player.equipment.computeIdForIndex(Equipment.AMMUNITION) in ammo.ammo)
    if (!capability && ranged && !usableAmmo) return CombatThreatEstimate(0.0, 0)
    val strengthBonus = if (!ranged) player.equipment.getBonus(EquipmentBonus.STRENGTH)
        else if (usableAmmo) ammo.strength else level(player, Skill.RANGED, true).coerceAtMost(100)
    var maximum = if (!capability) player.combat.getDefaultMaxHit(type) else {
        val strength = level(player, if (ranged) Skill.RANGED else Skill.STRENGTH, true)
        floor(0.5 + (strength + 8.0) * (strengthBonus + 64.0).coerceAtLeast(0.0) / 640.0).toInt().coerceAtLeast(1)
    }
    if (!ranged && BarrowsEquipmentSet.DHAROK.isWorn(player.equipment)) {
        val total = player.totalHealth.coerceIn(1, 1000)
        val health = if (capability) 1 else player.health
        maximum = floor(maximum * (1 + BarrowsEquipmentSet.dharokBonus(total, health))).toInt()
    }
    return estimateStyle(player, defender, type, maximum, player.combat.weapon.speed, capability,
        uncertain = capability && ranged && !usableAmmo)
}

private fun estimateNpc(npc: Npc, defender: Mob, capability: Boolean): CombatThreatEstimate {
    val definition = npcDefinition(npc) ?: return unknownThreat(npc, defender)
    if (definition.attackSpeed <= 0 || definition.maximumHit < 0) return unknownThreat(npc, defender)
    val standard = NpcCombatHandler.usesStandardAttack(npc)
    var maximum = if (capability) definition.maximumHit else npc.combat.getDefaultMaxHit(CombatDamageType.MELEE)
    if (npc.id == 2026) {
        val total = definition.hitpoints.coerceIn(1, 1000)
        maximum = BarrowsCombat.dharokMaximum(maximum, if (capability) 1 else npc.health, total)
    }
    if (defender is Bot) maximum = maxOf(maximum, defender.monsterMemory.biggestHit(npc.id))
    // A learned dominant style cannot establish that a custom attack never switches or bypasses protection.
    val styles = if (standard) listOf(CombatDamageType.MELEE) else CombatDamageType.entries
    var estimate = CombatThreatEstimate(0.0, 0, !standard)
    for (type in styles) {
        estimate = combine(estimate, estimateStyle(npc, defender, type, maximum, definition.attackSpeed,
            capability, uncertain = !standard, ignoreProtection = !standard))
    }
    if (defender is Player && BotPotionActionHandler.breathesFire(npc)) {
        val shield = defender.equipment.computeIdForIndex(Equipment.SHIELD) == 1540
        val prayer = !capability && defender.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)
        val antifire = !capability && defender.status.isAntiFire()
        val fireMaximum = if (npc.id == 50) {
            KingBlackDragonBreath.Kind.entries.maxOf { KingBlackDragonBreath.maximumHit(it, shield, prayer, antifire) }
        } else {
            val protection = (if (shield) 0.8 else 0.0) + (if (prayer) 0.4 else 0.0) +
                (if (antifire) 0.2 else 0.0)
            (50 - floor(50 * protection).toInt()).coerceAtLeast(0)
        }
        val speed = if (npc.id == 50) 5 else definition.attackSpeed
        // Breath can override ordinary accuracy and prayer handling; its envelope intentionally assumes a hit.
        estimate = combine(estimate, CombatThreatEstimate(fireMaximum / (2.0 * speed), fireMaximum, true))
    }
    return estimate
}

private fun combine(first: CombatThreatEstimate, second: CombatThreatEstimate) = CombatThreatEstimate(
    maxOf(first.damagePerTick, second.damagePerTick), maxOf(first.maximumHit, second.maximumHit),
    first.uncertain || second.uncertain)

private fun estimateStyle(attacker: Mob, defender: Mob, type: CombatDamageType, maximum: Int, speed: Int,
                          capability: Boolean, uncertain: Boolean = false,
                          ignoreProtection: Boolean = false): CombatThreatEstimate {
    var hit = maximum.coerceIn(0, 1000)
    var limitedProtection = false
    if (!capability && !ignoreProtection && defender is Player) {
        val prayer = when (type) {
            CombatDamageType.MELEE -> CombatPrayer.PROTECT_FROM_MELEE
            CombatDamageType.RANGED -> CombatPrayer.PROTECT_FROM_MISSILES
            CombatDamageType.MAGIC -> CombatPrayer.PROTECT_FROM_MAGIC
        }
        if (defender.combat.prayers.isActive(prayer)) {
            if (attacker !is Npc) hit = hit * 3 / 5 else {
                val points = defender.skill(Skill.PRAYER).level
                if (points >= PROTECTION_PRAYER_RESERVE) hit = 0 else {
                    limitedProtection = true
                    if (points > 0) hit = (hit + 1) / 2
                }
            }
        }
    }
    val chance = if (capability) neutralHitChance(attacker, defender, type)
        else CombatFormula.calculateHitChance(attacker, defender, type).coerceIn(0.0, 1.0)
    return CombatThreatEstimate(chance * hit / (2.0 * speed.coerceIn(1, 20)), hit,
        uncertain || limitedProtection || speed !in 1..20 || maximum !in 0..1000)
}

/**
 * Mirrors the native roll comparison with static levels and equipment, omitting stance and prayer multipliers.
 * Double intermediates keep malformed high-level definitions from overflowing the estimate.
 */
private fun neutralHitChance(attacker: Mob, defender: Mob, type: CombatDamageType): Double {
    val bonus = when (type) {
        CombatDamageType.MAGIC -> EquipmentBonus.MAGIC_ATTACK
        CombatDamageType.RANGED -> EquipmentBonus.RANGED_ATTACK
        CombatDamageType.MELEE -> attacker.combat.attackStyleBonus
    }
    val skill = when (type) {
        CombatDamageType.MAGIC -> Skill.MAGIC
        CombatDamageType.RANGED -> Skill.RANGED
        CombatDamageType.MELEE -> Skill.ATTACK
    }
    val attackLevel = level(attacker, skill, true) + if (attacker is Npc) 9.0 else 8.0
    val attack = attackLevel * (accuracyBonus(attacker, bonus) + 64.0).coerceAtLeast(0.0)
    val defenceLevel = if (type != CombatDamageType.MAGIC) level(defender, Skill.DEFENCE, true).toDouble()
        else if (defender is Npc) level(defender, Skill.MAGIC, true).toDouble()
        else floor(level(defender, Skill.DEFENCE, true) * 0.3 + level(defender, Skill.MAGIC, true) * 0.7)
    val defence = (defenceLevel + if (defender is Npc) 9.0 else 8.0) *
        (defenceBonus(defender, bonus) + 64.0).coerceAtLeast(0.0)
    return (if (attack > defence) 1.0 - (defence + 2) / (2 * (attack + 1))
        else attack / (2 * (defence + 1))).coerceIn(0.0, 1.0)
}

private fun accuracyBonus(mob: Mob, bonus: EquipmentBonus): Int = if (mob is Player) mob.equipment.getBonus(bonus)
    else npcDefinition(mob as Npc)?.let {
        when (bonus) {
            EquipmentBonus.MAGIC_ATTACK -> it.magicBonus
            EquipmentBonus.RANGED_ATTACK -> it.rangedBonus
            else -> it.attackBonus
        }
    } ?: 0

private fun defenceBonus(mob: Mob, attackBonus: EquipmentBonus): Int = if (mob is Player)
    mob.equipment.getBonus(attackBonus.opposite) else npcDefinition(mob as Npc)?.getMatchingDefenceBonus(attackBonus) ?: 0

private fun level(mob: Mob, skill: Int, capability: Boolean): Int =
    (if (capability) mob.skill(skill).staticLevel else mob.skill(skill).level).coerceIn(0, 10000)

/**
 * Gives unavailable profiles a bounded combat-level approximation rather than another NPC's fallback definition.
 */
private fun unknownThreat(attacker: Mob, defender: Mob): CombatThreatEstimate {
    val attackLevel = attacker.combatLevel.coerceIn(0, 1000)
    val defenceLevel = defender.combatLevel.coerceIn(0, 1000)
    val maximum = (1 + attackLevel / 8).coerceAtMost(99)
    val chance = ((attackLevel + 10.0) / (2 * (defenceLevel + 10.0))).coerceIn(0.1, 0.9)
    return CombatThreatEstimate(chance * maximum / 8, maximum, true)
}
