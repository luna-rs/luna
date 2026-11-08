package api.bot.script

import api.bot.action.BotBankSpace
import api.predef.itemDef
import api.predef.weapon
import api.predef.world
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellCost
import game.skill.magic.Staff
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemEconomyDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil

/**
 * Restricts destructive alchemy to real surplus and values the target and native spell costs together.
 * Callers retain their own target whitelist and refresh this policy after suspended preparation.
 *
 * @author lare96
 */
object AlchemyPolicy {

    /**
     * Limits training's complete opportunity loss to twenty coins per point of native Magic experience.
     */
    const val MAX_TRAINING_GP_PER_XP = 20L

    /**
     * Counts only ordinary carried or banked copies above the account's current working reservations.
     * Equipped copies satisfy a reservation but are never available for destruction. Dynamic copies neither
     * become targets nor stand in for a reserved ordinary copy. Notes share their base item's reservation.
     * A pending production input stays wholly protected because its public reservation exposes ids, not amounts.
     */
    fun surplus(bot: Bot, id: Int, retained: Map<Int, Int> = BotBankSpace.retainedStock(bot)): Long {
        val base = baseId(id)
        if (bot.scriptStack.scripts.any { script ->
                script is InventoryBotScript && !script.isFinished() && !script.isTerminated() &&
                    script.bankSpaceProtectedItems().any { baseId(it) == base }
            }) return 0L
        val transferable = (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).filter {
            !it.isDynamic && baseId(it.id) == base
        }.sumOf { it.amount.toLong() }
        val equipped = bot.equipment.filterNotNull().filter {
            !it.isDynamic && baseId(it.id) == base
        }.sumOf { it.amount.toLong() }
        return minOf(transferable, (transferable + equipped - (retained[base] ?: 0)).coerceAtLeast(0L))
    }

    /**
     * Applies ownership and price policy without requiring runes to be present yet, allowing bounded supply recovery.
     * The script remains responsible for its whitelist, spellbook, level, build cap and native cast requirements.
     * A dynamic carried copy of the exact target id blocks id-based native selection from consuming it first.
     */
    fun eligible(bot: Bot, id: Int, type: AlchemyType, profit: Boolean, fireStaffId: Int? = null,
                 minimumPrices: Map<Int, Long> = emptyMap(), retained: Map<Int, Int>? = null): Boolean {
        val definition = itemDef(baseId(id))
        if (!definition.isTradeable || definition.value <= 0 ||
            bot.inventory.any { it != null && it.id == id && it.isDynamic }) return false
        val available = if (retained == null) surplus(bot, id) else surplus(bot, id, retained)
        if (available < 1L) return false
        val margin = margin(bot, id, type, fireStaffId, minimumPrices)
        return if (profit) margin > 0L else margin >= -(type.xp * MAX_TRAINING_GP_PER_XP).toLong()
    }

    /**
     * Prices the native alchemy proceeds after the target's guide opportunity cost and all consumed rune stacks.
     * Fractional observed guides round upward. Quoted purchase prices may raise either cost, but never discount
     * an owned item's current guide value. Invalid observed prices fall back to the configured or native value.
     * An explicit staff is a preparation estimate only; the default uses actual equipment for cast revalidation.
     */
    fun margin(bot: Bot, id: Int, type: AlchemyType, fireStaffId: Int? = null,
               minimumPrices: Map<Int, Long> = emptyMap()): Long {
        val base = baseId(id)
        fun price(itemId: Int): Long = maxOf(guidePrice(itemId),
            minimumPrices[baseId(itemId)] ?: 0L, 1L)
        val target = price(base)
        var remaining = proceeds(base, type) - target
        for (rune in runeCosts(bot, type, fireStaffId)) {
            val unit = price(rune.id)
            if (unit > Long.MAX_VALUE / rune.amount || remaining < Long.MIN_VALUE + unit * rune.amount) {
                return Long.MIN_VALUE
            }
            remaining -= unit * rune.amount
        }
        return remaining
    }

    /**
     * Uses native combination-rune priority over carried then banked stock, appending base runes only for shortfalls.
     * This keeps missing supplies eligible while valuing the exact native rune identities rather than treating all
     * fire substitutes alike. Level checks are deliberately left to activity eligibility and the actual cast.
     */
    fun runeCosts(bot: Bot, type: AlchemyType, fireStaffId: Int? = null): List<Item> {
        val equipped = bot.equipment.weapon?.id
        val weapon = fireStaffId?.takeIf { usableOwnedFireStaff(bot, it) } ?: equipped
        val carried = Magic.spellCost(type.level, bot.inventory, { bot.equipment.contains(it) }, weapon,
            type.level, type.requirements)
        if (carried is SpellCost.Castable) return carried.items
        val items = bot.inventory.filterNotNull() + bot.bank.filterNotNull() +
            type.requirements.filterIsInstance<RuneRequirement>().map { Item(it.rune.id, it.amount) }
        return (Magic.spellCost(type.level, items, { bot.equipment.contains(it) }, weapon,
            type.level, type.requirements) as SpellCost.Castable).items
    }

    /**
     * Finds actual equipped fire representation first, then an owned staff meeting native equipment requirements.
     * Discovering a banked staff never equips it; casting callers must subsequently validate actual equipment.
     */
    fun resolveFireStaff(bot: Bot): Int? {
        bot.equipment.weapon?.id?.takeIf { providesFire(it) }?.let { return it }
        return Staff.entries.asSequence().filter { Rune.FIRE in it.represents }.flatMap { it.ids.asSequence() }
            .firstOrNull { usableOwnedFireStaff(bot, it) }
    }

    private fun usableOwnedFireStaff(bot: Bot, id: Int): Boolean = providesFire(id) &&
        (bot.equipment.weapon?.id == id ||
            ((bot.inventory.any { it?.id == id } || bot.bank.any { it?.id == id }) &&
                EquipmentDefinition.ALL.get(id).map { it.meetsAllRequirements(bot) }.orElse(false)))

    private fun providesFire(id: Int): Boolean = Staff.ID_TO_STAFF[id]?.represents?.contains(Rune.FIRE) == true

    private fun baseId(id: Int): Int = itemDef(id).unnotedId.orElse(id)

    private fun guidePrice(id: Int): Long {
        val base = baseId(id)
        val observed = world.economy.prices[base]?.price
        if (observed != null && observed.isFinite() && observed > 0.0) {
            return ceil(observed).toLong().coerceAtLeast(1L)
        }
        return (ItemEconomyDefinition.ALL.get(base).orElse(null)?.initialPrice?.toLong()
            ?: itemDef(base).value.toLong()).coerceAtLeast(1L)
    }

    private fun proceeds(id: Int, type: AlchemyType): Long =
        (itemDef(id).value * type.valueMultiplier).toInt().coerceAtLeast(1).toLong()
}
