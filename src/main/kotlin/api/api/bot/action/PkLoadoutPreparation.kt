package api.bot.action

import api.bot.script.BotScript.ActionSafety
import api.combat.death.PlayerDeathItems
import api.predef.ext.allSuitableFood
import engine.bot.gear.BotGearPurpose
import engine.controllers.Controllers.inWilderness
import game.player.item.consume.food.Food
import game.skill.magic.Rune
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainer.StackPolicy
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Packs compact, owned PK switches through ordinary banking and equipment actions.
 * Every saved discipline receives its weapon and usable consumables. Body/leg switches are optional;
 * other slots share gear. Food is filled towards sixteen before optional armour and longer supply stacks.
 * Two inventory slots remain free in every modeled outfit for native equipment displacement.
 *
 * @author lare96
 */
internal class PkLoadoutPreparation(private val bot: Bot, private val handler: BotActionHandler) {
    private val clan = PkClanPolicy(bot)
    /**
     * A concrete departure outfit and inventory, checked against physical ownership and prospective death loss.
     *
     * @author lare96
     */
    data class Plan(val equipment: List<Item>, val inventory: List<Item>)

    /**
     * Tests all permanently assigned disciplines against carried weapons, ammunition and at least one spell cast.
     * Food exhaustion triggers a return to bank; no saved identity is changed because stock ran out.
     */
    fun ready(): Boolean {
        val purposes = purposes()
        return clan.wearingCape() && purposes.isNotEmpty() && bot.inventory.any {
            it != null && Food.ID_TO_FOOD[it.id]?.heal?.let { heal -> heal > 0 } == true
        } && purposes.all { CombatLoadout.find(bot, it, carriedOnly = true)?.gear?.get(Equipment.WEAPON) != null }
    }

    /**
     * Resolves saved disciplines without treating a build's incidental training skills as additional PK styles.
     */
    private fun purposes(): List<BotGearPurpose> {
        if (bot.preferences.isSkiller) return emptyList()
        val styles = bot.preferences.pk?.styles() ?: return emptyList()
        return listOf(BotGearPurpose.MELEE to Skill.ATTACK, BotGearPurpose.RANGED to Skill.RANGED,
            BotGearPurpose.MAGIC to Skill.MAGIC).filter { styles.uses(it.second) }.map { it.first }
    }

    /**
     * Separates absent physical supplies from a rejected risk budget or another departure restriction.
     * Legal unarmed training does not satisfy the PK session's owned-weapon requirement.
     * Scavenging detours may address stock shortages, but must not bypass unrelated PK policies.
     */
    fun missingSupplies(): Boolean = purposes().let { purposes ->
        purposes.isNotEmpty() && ((bot.inventory.filterNotNull() + bot.equipment.filterNotNull() +
            bot.bank.filterNotNull()).none { Food.ID_TO_FOOD[it.id]?.heal?.let { it > 0 } == true } ||
            purposes.any { CombatLoadout.find(bot, it)?.gear?.get(Equipment.WEAPON) == null })
    }

    /**
     * Plans synchronously from owned stock. A coordinator may project the one authorized missing clan cape;
     * the actual cape is only replenished when the script runs. Other supplies always require physical stock.
     * Each unsuccessful minimum kit lowers the damage spell or excludes
     * one preferred weapon, so cheap owned alternatives are considered without buying or creating supplies.
     * Extra food/armour/supplies are admitted only when every modeled switch fits both space and the risk cap.
     */
    fun plan(allowMissingClanCape: Boolean = false): Plan? {
        val purposes = purposes().takeIf { it.isNotEmpty() } ?: return null
        val stock = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
            .groupBy { it.id }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            .toMutableMap()
        if (allowMissingClanCape) clan.cape?.let { stock.putIfAbsent(it, 1) }
        val foods = stock.keys.filter { Food.ID_TO_FOOD[it]?.heal?.let { heal -> heal > 0 } == true }
            .sortedByDescending { Food.ID_TO_FOOD.getValue(it).heal }
        if (foods.isEmpty()) return null
        val losesItems = PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())
        val assess = PkRiskBudget.assessor(bot, losesItems)
        val excluded = mutableSetOf<Int>()
        var maximumSpellHit = Int.MAX_VALUE
        while (true) {
            val selected = purposes.associateWith { CombatLoadout.find(bot, it, excludingWeapons = excluded,
                maximumSpellHit = maximumSpellHit) ?: return null }
            val quantities = linkedMapOf<Int, Int>()
            clan.cape?.let { quantities[it] = 1 }
            selected.values.forEach { quantities[it.gear[Equipment.WEAPON] ?: return null] = 1 }
            val ranged = selected[BotGearPurpose.RANGED]
            val ammunition = ranged?.gear?.get(Equipment.AMMUNITION) ?: ranged?.gear?.get(Equipment.WEAPON)
                ?.takeIf { WeaponDefinition.ALL.retrieve(it).type in CombatLoadout.thrown }
            ammunition?.let { quantities[it] = 1 }
            val magic = selected[BotGearPurpose.MAGIC]
            magic?.runes?.forEach { (id, amount) -> quantities[id] = maxOf(quantities[id] ?: 0, amount) }
            if (quantities.any { (id, amount) -> (stock[id] ?: 0) < amount }) return null
            val starting = arrayOfNulls<Int>(14)
            starting[Equipment.CAPE] = clan.cape
            starting[Equipment.WEAPON] = selected.values.first().gear[Equipment.WEAPON]
            starting[Equipment.AMMUNITION] = ranged?.gear?.get(Equipment.AMMUNITION)

            fun layout(purpose: BotGearPurpose): Array<Int?> = starting.copyOf().also { layout ->
                val outfit = selected.getValue(purpose)
                layout[Equipment.WEAPON] = outfit.gear[Equipment.WEAPON]
                for (slot in listOf(Equipment.CHEST, Equipment.LEGS)) {
                    outfit.gear[slot]?.takeIf { it in quantities }?.let { layout[slot] = it }
                }
                if (purpose == BotGearPurpose.MAGIC) {
                    for (slot in layout.indices.filter { it != Equipment.WEAPON && it != Equipment.AMMUNITION }) {
                        val id = layout[slot] ?: continue
                        if (EquipmentDefinition.ALL.retrieve(id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) < 0) layout[slot] = null
                    }
                }
                if (EquipmentDefinition.ALL.retrieve(layout[Equipment.WEAPON]!!).isTwoHanded) layout[Equipment.SHIELD] = null
            }
            fun snapshot(layout: Array<Int?>): Plan? {
                val remaining = quantities.toMutableMap()
                val worn = layout.mapIndexedNotNull { slot, id -> id?.let {
                    val amount = if (slot == Equipment.AMMUNITION || it == ammunition) quantities.getValue(it) else 1
                    remaining[it] = remaining.getValue(it) - amount
                    Item(it, amount)
                } }
                val inventory = ItemContainer(28, StackPolicy.STANDARD, -1)
                for ((id, amount) in remaining) if (amount > 0) {
                    val item = Item(id, amount)
                    if (!inventory.hasSpaceFor(item) || !inventory.add(item)) return null
                }
                if (inventory.computeRemainingSize() < 2) return null
                val carried = inventory.filterNotNull()
                if (!assess(carried, worn, true).withinBudget) return null
                return Plan(worn, carried)
            }
            fun fits(): Boolean = quantities.all { (id, amount) -> amount <= (stock[id] ?: 0) } &&
                purposes.all { snapshot(layout(it)) != null }
            fun add(id: Int, amount: Int = 1): Boolean {
                val old = quantities[id] ?: 0
                quantities[id] = old + amount
                if (fits()) return true
                if (old == 0) quantities.remove(id) else quantities[id] = old
                return false
            }

            val firstFood = foods.firstOrNull { add(it) }
            if (firstFood == null) {
                // Spell tiers are bounded by native max-hit definitions; weapon exclusions grow monotonically.
                val nextHit = magic?.spell?.maxHit?.minus(1)
                if (nextHit != null && nextHit >= 2 && CombatLoadout.find(bot, BotGearPurpose.MAGIC,
                        excludingWeapons = excluded, maximumSpellHit = nextHit) != null) {
                    maximumSpellHit = nextHit
                } else {
                    val weapon = selected.values.mapNotNull { it.gear[Equipment.WEAPON] }
                        .maxBy { bot.world.economy.getPrice(it) }
                    excluded += weapon
                    maximumSpellHit = Int.MAX_VALUE
                }
                continue
            }
            var foodCount = 1
            for (id in foods) while (foodCount < 16 && (quantities[id] ?: 0) < stock.getValue(id) && add(id)) foodCount++

            // Shared slots use the primary outfit, with neutral magic-compatible alternatives where possible.
            for (slot in starting.indices.filter { it !in setOf(Equipment.WEAPON, Equipment.AMMUNITION) &&
                    !(it == Equipment.CAPE && clan.enabled) }) {
                val id = selected.values.mapNotNull { it.gear[slot] }.firstOrNull { id ->
                    BotGearPurpose.MAGIC !in purposes || slot in setOf(Equipment.CHEST, Equipment.LEGS) ||
                        EquipmentDefinition.ALL.retrieve(id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) >= 0
                } ?: continue
                starting[slot] = id
                if (id !in quantities && !add(id)) starting[slot] = null
                else if (!fits()) starting[slot] = null
            }
            for (outfit in selected.values.drop(1)) for (slot in listOf(Equipment.CHEST, Equipment.LEGS)) {
                outfit.gear[slot]?.takeIf { it !in quantities }?.let { add(it) }
            }
            fun extend(costs: Map<Int, Int>, limit: Int) {
                if (costs.isEmpty()) return
                var low = 1
                var high = minOf(limit, costs.minOf { (id, cost) -> stock.getValue(id) / cost })
                while (low < high) {
                    val middle = low + (high - low + 1) / 2
                    val before = quantities.toMap()
                    costs.forEach { (id, cost) -> quantities[id] = cost * middle }
                    val accepted = fits()
                    quantities.clear()
                    quantities.putAll(before)
                    if (accepted) low = middle else high = middle - 1
                }
                costs.forEach { (id, cost) -> quantities[id] = cost * low }
            }
            ammunition?.let { extend(mapOf(it to 1), 500) }
            magic?.let { extend(it.runes, 500) }
            return snapshot(layout(purposes.first()))
        }
    }

    /**
     * Deposits unrelated belongings, builds a fresh plan and applies it with native banking/equipment helpers.
     * Every suspension is followed by safety checks. Partial withdrawals and changed prices cannot authorize
     * Wilderness entry; a failed preparation finishes at the bank rather than retrying an impossible kit forever.
     */
    suspend fun prepare(): Boolean {
        fun safe(): Boolean = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness()
        if (!safe()) return false
        try {
            if (!handler.banking.travelToBankOpen() || !safe()) return false
            if (!handler.banking.depositInventory() || !safe() || !handler.equipment.unequipAll() || !safe() ||
                !handler.banking.depositInventory() || !safe()) return false
            if (!clan.ensureCape()) return false
            val plan = plan() ?: run {
                if (bot.bank.none { it != null && Food.ID_TO_FOOD.containsKey(it.id) }) {
                    bot.preferences.allSuitableFood().forEach { bot.preferences.raiseWantedItemTarget(it.id, 750) }
                }
                return false
            }
            for ((id, items) in (plan.inventory + plan.equipment).groupBy { it.id }) {
                val amount = items.sumOf { it.amount }
                when {
                    id in Rune.ID_TO_RUNE || EquipmentDefinition.ALL.get(id).map { it.index == Equipment.AMMUNITION }.orElse(false) ||
                        WeaponDefinition.ALL.get(id).map { it.type in CombatLoadout.thrown }.orElse(false) ->
                        bot.preferences.raiseWantedItemTarget(id, maxOf(1000, amount * 2))
                    id in Food.ID_TO_FOOD -> bot.preferences.raiseWantedItemTarget(id, 750)
                }
            }
            handler.banking.clickBankingMode(noted = false)
            for (item in plan.equipment) {
                if (!safe() || !bot.bank.isOpen || !handler.banking.withdraw(item) || !safe() ||
                    bot.inventory.computeAmountForId(item.id) < item.amount) {
                    bot.log("PK preparation failed while withdrawing equipment ${item.id}.")
                    return false
                }
            }
            // Native equip clicks close the bank. Withdraw the whole outfit before equipping any of it.
            for (item in plan.equipment) {
                if (!safe() || !handler.equipment.equip(item.id) || !safe() ||
                    bot.equipment.computeAmountForId(item.id) != item.amount) {
                    bot.log("PK preparation failed while equipping ${item.id}.")
                    return false
                }
            }
            if (!safe() || !handler.banking.travelToBankOpen() || !safe()) {
                bot.log("PK preparation failed while reopening the bank for supplies.")
                return false
            }
            handler.banking.clickBankingMode(noted = false)
            val withdraw = plan.inventory.groupBy { it.id }.map { (id, items) -> Item(id, items.sumOf { it.amount }) }
            for (item in withdraw) {
                if (!safe() || !bot.bank.isOpen || !handler.banking.withdraw(item) || !safe() ||
                    bot.inventory.computeAmountForId(item.id) < item.amount) {
                    bot.log("PK preparation failed while withdrawing supplies ${item.id}.")
                    return false
                }
            }
            fun counts(items: List<Item>) = items.groupBy { it.id }.mapValues { (_, stacks) -> stacks.sumOf { it.amount.toLong() } }
            return safe() && ready() && bot.equipment.filterNotNull() == plan.equipment &&
                counts(bot.inventory.filterNotNull()) == counts(plan.inventory) && handler.combat.pkRiskBudget().withinBudget
        } finally {
            bot.overlays.closeWindows()
        }
    }
}
