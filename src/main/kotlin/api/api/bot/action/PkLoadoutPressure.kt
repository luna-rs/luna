package api.bot.action

import engine.bot.gear.BotGearPurpose
import game.combat.BarrowsEquipmentSet
import game.item.degradable.DegradableEquipmentHandler.CRYSTAL_ITEMS_CHARGES
import game.item.degradable.DegradableEquipmentHandler.charges
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.def.WeaponTypeDefinition
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor

/**
 * Expected primary damage from ordinary attacks with one supplied outfit and its native timing.
 *
 * @author lare96
 */
internal data class PkLoadoutPressure(
    val expectedDamagePerLaunch: Double,
    val speed: Int,
    val range: Int,
    val availableLaunches: Int
)

/**
 * Resolves the physical stance that the switcher applies for the chosen discipline.
 */
internal fun pkPhysicalStyle(bot: Bot, purpose: BotGearPurpose, weapon: WeaponTypeDefinition): CombatStyleDefinition? =
    BotCombatActionHandler.allowedStyles(bot, weapon.styles).firstOrNull {
        Skill.MAGIC !in it.exp && (Skill.RANGED in it.exp) == (purpose == BotGearPurpose.RANGED)
    }

/**
 * Estimates direct damage without applying a switch, spending supplies, or rolling RNG. The prospective outfit
 * retains gear the switcher leaves in place. Protection mitigation comes from the visible overhead.
 * Special attacks, poison, secondary targets and defensive reactions are outside ordinary pressure.
 */
internal fun pkLoadoutPressure(bot: Bot, target: Player, purpose: BotGearPurpose, loadout: CombatLoadout,
                               current: Boolean): PkLoadoutPressure? {
    val weapon = (if (current) bot.equipment[Equipment.WEAPON]?.id else loadout.gear[Equipment.WEAPON]) ?: return null
    val weaponEquipment = EquipmentDefinition.ALL.get(weapon).orElse(null) ?: return null
    val equipment = if (current) bot.equipment.map { it?.equipDef } else loadout.gear.mapIndexed { slot, selected ->
        when {
            slot == Equipment.SHIELD && weaponEquipment.isTwoHanded -> null
            selected != null -> EquipmentDefinition.ALL.get(selected).orElse(null) ?: return null
            else -> bot.equipment[slot]?.equipDef?.takeUnless {
                purpose == BotGearPurpose.MAGIC && it.getBonus(EquipmentBonus.MAGIC_ATTACK.index) < 0
            }
        }
    }
    fun bonus(type: EquipmentBonus) = equipment.sumOf { it?.getBonus(type.index) ?: 0 }
    if (purpose == BotGearPurpose.MAGIC) {
        val spell = loadout.spell?.takeIf { it.spellbook == Spellbook.ANCIENT && it.maxHit > 0 } ?: return null
        val casts = loadout.runes.minOfOrNull { (id, perCast) ->
            bot.inventory.computeAmountForId(id) / perCast
        } ?: Int.MAX_VALUE
        val type = CombatDamageType.MAGIC
        val accuracy = CombatFormula.calculatePlayerHitChance(bot, target, type, CombatStance.ACCURATE,
            EquipmentBonus.MAGIC_ATTACK, bonus(EquipmentBonus.MAGIC_ATTACK)).coerceIn(0.0, 1.0)
        val cap = protectedCap(spell.maxHit, target.prayerIcon, PrayerIcon.PROTECT_FROM_MAGIC)
        return PkLoadoutPressure(accuracy * cap / 2.0, speed = 5, range = 10, availableLaunches = casts)
            .takeIf { casts > 0 && accuracy.isFinite() }
    }

    val ranged = purpose == BotGearPurpose.RANGED
    if (!ranged && purpose != BotGearPurpose.MELEE) return null
    val style = if (current) bot.combat.weapon.styleDef else WeaponDefinition.ALL.get(weapon).orElse(null)?.let {
        pkPhysicalStyle(bot, purpose, it.typeDef)
    }
    if (style == null || style.speed < 1 || style.range < 1 || (style.range > 2) != ranged) return null
    val type = if (ranged) CombatDamageType.RANGED else CombatDamageType.MELEE
    val accuracyBonus = if (ranged) EquipmentBonus.RANGED_ATTACK else style.bonus
    val physicalType = if (ranged) PhysicalType.RANGED else PhysicalType.MELEE
    val ammo = if (ranged) pkRangedAmmo(bot, weapon, equipment[Equipment.AMMUNITION]?.id(), current) ?: return null
        else null
    val strength = ammo?.first ?: bonus(EquipmentBonus.STRENGTH)
    val unprotected = CombatFormula.calculatePhysicalMaxHit(bot, physicalType, style.stance, strength)
    val protection = if (ranged) PrayerIcon.PROTECT_FROM_MISSILES else PrayerIcon.PROTECT_FROM_MELEE
    val cap = protectedCap(unprotected, target.prayerIcon, protection)
    val accuracy = CombatFormula.calculatePlayerHitChance(bot, target, type, style.stance, accuracyBonus,
        bonus(accuracyBonus)).coerceIn(0.0, 1.0)
    if (!accuracy.isFinite()) return null
    val pressure = if (ranged) accuracy * cap / 2.0 else
        meleePressure(bot, equipment, accuracy, cap, unprotected)
    return PkLoadoutPressure(pressure, style.speed, style.range, ammo?.second ?: Int.MAX_VALUE)
}

private fun protectedCap(cap: Int, overhead: PrayerIcon, matching: PrayerIcon): Int =
    if (overhead == matching) Math.floorDiv(cap * 60L, 100L).toInt() else cap

/**
 * Resolves native compatible ammunition and its remaining shots. Current attacks require it in the equipped slot.
 */
internal fun pkRangedAmmo(bot: Bot, weapon: Int, ammoId: Int?, equippedOnly: Boolean): Pair<Int, Int>? {
    fun ammoItem(slot: Int, id: Int): Item? =
        bot.equipment[slot]?.takeIf { it.id == id } ?:
            if (equippedOnly) null else bot.inventory.firstOrNull { it?.id == id }

    val ammoless = AmmoDefinition.NO_AMMO_WEAPONS[weapon]
    if (ammoless != null) {
        val item = ammoItem(Equipment.WEAPON, weapon) ?: return null
        val shots = when {
            ammoless.type == AmmoType.CRYSTAL_ARROW -> (item as? DynamicItem)?.charges ?: CRYSTAL_ITEMS_CHARGES
            item.itemDef.isStackable -> item.amount
            else -> Int.MAX_VALUE
        }
        return if (shots > 0) ammoless.strength to shots else null
    }
    val id = ammoId ?: return null
    val ammo = AmmoDefinition.AMMO_REQUIRING_WEAPONS[id]?.takeIf { weapon in it.weapons } ?: return null
    val item = ammoItem(Equipment.AMMUNITION, id)?.takeIf { it.amount > 0 } ?: return null
    return ammo.strength to item.amount
}

private fun meleePressure(bot: Bot, gear: List<EquipmentDefinition?>, accuracy: Double,
                          cap: Int, unprotected: Int): Double {
    val itemIdAt = { slot: Int -> gear[slot]?.id() ?: -1 }
    val ordinary = accuracy * cap / 2.0
    if (BarrowsEquipmentSet.VERAC.isWorn(itemIdAt)) {
        return 0.75 * ordinary + 0.25 * (unprotected / 2.0 + 1.0)
    }
    if (BarrowsEquipmentSet.DHAROK.isWorn(itemIdAt)) {
        val multiplier = BarrowsEquipmentSet.dharokBonus(bot.totalHealth, bot.health)
        val meanBonus = (0..cap).sumOf { floor(it * multiplier) } / (cap + 1.0)
        return ordinary + accuracy * meanBonus
    }
    return ordinary
}
