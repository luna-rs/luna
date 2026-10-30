package game.bot.scripts

import api.bot.action.BotCombatActionHandler
import api.bot.action.CombatLoadout
import api.predef.*
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * A conservative three-style Waterbirth visit, using owned gear and supplies only.
 * The melee armour stays equipped; the bot carries a ranged weapon, staff, ammunition and elemental spell runes.
 * Admission deliberately requires strong levels and physical armour rather than treating combat level as readiness.
 *
 * @author lare96
 */
internal object DagannothKingsTrip {
    const val FOOD = 385
    const val PRAYER = 2434
    const val ANTIPOISON = 2446
    const val COINS = 995
    // This trip keeps physical armour on for Prime; require usable offence in that mixed outfit.
    const val MINIMUM_RANGED_ATTACK = 40
    const val MINIMUM_AMMO_STRENGTH = 30
    // Rex has zero Magic and only ten magic defence; this retains at least 24 equipment roll units (bonus + 64).
    const val MINIMUM_MAGIC_ATTACK = -40
    val prayerDoses = setOf(2434, 139, 141, 143)
    val antipoisonDoses = setOf(2446, 175, 177, 179)
    val supplies = mapOf(FOOD to 16, PRAYER to 4, ANTIPOISON to 1, COINS to 2000)

    /**
     * Resolved outfits are selections, not reservations; preparation rechecks physical stock after withdrawing.
     *
     * @author lare96
     */
    data class Plan(val melee: CombatLoadout, val ranged: CombatLoadout, val magic: CombatLoadout) {
        val ammunition: Int get() = ranged.gear[Equipment.AMMUNITION] ?: ranged.gear[Equipment.WEAPON]!!
        val baseGear: Array<Int?> get() = melee.gear.copyOf().also {
            it[Equipment.SHIELD] = null
            it[Equipment.AMMUNITION] = ammunition
        }
        val switches: Set<Int> get() = setOf(ranged.gear[Equipment.WEAPON]!!, magic.gear[Equipment.WEAPON]!!)

        /**
         * Supplies and the two unequipped weapons; the third weapon and ammunition are already equipped.
         */
        fun carriedRequirements(): Map<Int, Int> = supplies.toMutableMap().also { requested ->
            switches.forEach { requested[it] = 1 }
            magic.runes.forEach { (id, amount) -> requested[id] = amount * 250 }
        }
    }

    /**
     * Unboosted levels and every XP-bearing discipline must suit this account's permanent build.
     */
    fun levelsReady(bot: Bot): Boolean = bot.combatLevel >= 115 &&
        listOf(bot.attack, bot.strength, bot.defence, bot.hitpoints, bot.ranged, bot.magic)
            .all { it.staticLevel >= 85 } && bot.prayer.staticLevel >= 70 &&
        listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC)
            .all { BotCombatActionHandler.mayGainExperience(bot, it) }

    /**
     * Plans all three disciplines together so selecting the camp cannot silently fall back to an unprepared style.
     */
    fun plan(bot: Bot, carriedOnly: Boolean = false): Plan? {
        if (!levelsReady(bot)) return null
        val melee = CombatLoadout.find(bot, BotGearPurpose.MELEE, carriedOnly = carriedOnly) ?: return null
        val ranged = CombatLoadout.find(bot, BotGearPurpose.RANGED, carriedOnly = carriedOnly) ?: return null
        // The crossing and carried kit use a bow/crossbow with its own ammunition slot, not thrown stacks.
        if (ranged.gear[Equipment.AMMUNITION] == null) return null
        val magic = CombatLoadout.find(bot, BotGearPurpose.MAGIC, carriedOnly = carriedOnly) ?: return null
        if (melee.gear[Equipment.WEAPON] == null || (magic.spell?.maxHit ?: 0) < 15) return null
        val result = Plan(melee, ranged, magic)
        if (!KalphiteQueenTrip.gearReady(result.baseGear) || !rangedReady(result) || !magicReady(result)) return null
        val ammo = if (carriedOnly) CombatLoadout.carried(bot, result.ammunition)
            else bot.itemTracker.count(result.ammunition)
        if (ammo < if (carriedOnly) 50 else 500) return null
        val casts = if (carriedOnly) 20 else 250
        if (magic.runes.any { (id, amount) ->
                (if (carriedOnly) bot.inventory.computeAmountForId(id) else bot.itemTracker.count(id)) < amount * casts
            }) return null
        return result
    }

    /**
     * Checks the selected ammunition through native compatibility metadata and the outfit actually worn for Prime.
     */
    internal fun rangedReady(plan: Plan): Boolean {
        val weapon = plan.ranged.gear[Equipment.WEAPON] ?: return false
        val ammo = AmmoDefinition.AMMO_REQUIRING_WEAPONS[plan.ammunition] ?: return false
        return weapon in ammo.weapons && ammo.strength >= MINIMUM_AMMO_STRENGTH &&
            mixedAttackBonus(plan, weapon, EquipmentBonus.RANGED_ATTACK) >= MINIMUM_RANGED_ATTACK
    }

    /**
     * Prevents a staff-only switch from taking an outfit with a nearly collapsed magic attack roll to Rex.
     */
    internal fun magicReady(plan: Plan): Boolean = plan.magic.gear[Equipment.WEAPON]?.let {
        mixedAttackBonus(plan, it, EquipmentBonus.MAGIC_ATTACK) >= MINIMUM_MAGIC_ATTACK
    } == true

    /**
     * A carried outfit is not worn protection. Restored, replaced or degraded armour ends this visit conservatively.
     */
    internal fun armourMatches(bot: Bot, plan: Plan): Boolean = plan.baseGear.withIndex()
        .filter { it.index !in setOf(Equipment.WEAPON, Equipment.AMMUNITION, Equipment.SHIELD) }
        .all { (slot, id) -> bot.equipment[slot]?.id == id }

    private fun mixedAttackBonus(plan: Plan, weapon: Int, bonus: EquipmentBonus): Int =
        plan.baseGear.also { it[Equipment.WEAPON] = weapon }.filterNotNull().sumOf { equipDef(it).getBonus(bonus.index) }

    /**
     * Profit selection requires a complete fresh visit; nothing is purchased, granted or refilled by admission.
     */
    fun ready(bot: Bot): Boolean {
        val selected = plan(bot) ?: return false
        return preparationSlots(selected) <= 28 && supplies.all { (id, amount) ->
            bot.itemTracker.count(id) >= amount
        }
    }

    /**
     * Reserves one extra slot for an offhand displaced by a two-handed ranged weapon after a resumed visit.
     */
    internal fun preparationSlots(plan: Plan, stackable: (Int) -> Boolean = { itemDef(it).isStackable }): Int =
        1 + plan.carriedRequirements().entries.sumOf { (id, amount) -> if (stackable(id)) 1 else amount }

    /**
     * Six sharks and a prayer reserve belong to the return walk rather than the next fight.
     */
    fun canFight(bot: Bot, plan: Plan): Boolean = levelsReady(bot) && armourMatches(bot, plan) &&
        bot.inventory.computeAmountForId(FOOD) > 6 &&
        (bot.prayer.level >= 20 || prayerDoses.any { bot.inventory.contains(it) }) &&
        (bot.status.isAntiPoison() || antipoisonDoses.any { bot.inventory.contains(it) }) &&
        CombatLoadout.carried(bot, plan.ammunition) >= 20 &&
        plan.magic.runes.all { (id, amount) -> bot.inventory.computeAmountForId(id) >= amount } &&
        plan.switches.all { CombatLoadout.carried(bot, it) > 0 } &&
        CombatLoadout.carried(bot, plan.melee.gear[Equipment.WEAPON]!!) > 0
}
