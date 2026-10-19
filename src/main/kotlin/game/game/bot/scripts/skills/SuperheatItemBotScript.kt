package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.magic.Magic
import game.skill.magic.superheatItem.SuperheatItemAction
import game.skill.magic.superheatItem.SuperheatItemAction.Companion.superheatDelay
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Superheats owned ores into any of the eight existing bar recipes through the normal item spell interaction.
 *
 * Selection requires the regular spellbook, permanent Magic level, and the recipe's permanent Smithing level.
 * Casting rechecks both current levels and carried supplies. The player action owns recipe selection,
 * conversion, rune consumption, both experience awards, effects, and its queued cooldown.
 *
 * Banking reserves one slot per distinct rune stack, then balances primary and secondary ore quantities
 * with rune stock for one cast per bar. Equipped staffs, combination runes, and free casts use the shared
 * rune planner. Iron batches exclude coal; unexpected carried coal triggers banking so the player action
 * cannot select steel instead. The input slot becomes the bar, so full inventories need no extra output slot.
 *
 * [InventoryBotScript] supplies verified withdrawals, bounded retries, safety, and progress detection.
 * Missing consumables request total stock targets of 1,000. Snapshots retain the bar, session configuration,
 * and inherited retry budgets; live rune costs and batch sizes are recomputed after restoration.
 *
 * @param bot The bot superheating ores.
 * @property barType Existing ore recipe used for the session.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Safe processing hubs with existing banking and travel support.
 * @author lare96
 */
class SuperheatItemBotScript(
    bot: Bot, val barType: BarType, duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_MAGIC)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Selected bar recipe stored alongside inherited session and retry data.
         *
         * @author lare96
         */
        class SuperheatItemData : InventoryScriptData() {
            /** Existing bar enum name used when restoring the session. */
            var bar = ""
            override fun load(data: JsonObject) {
                super.load(data)
                bar = data.get("bar")?.asString ?: ""
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("bar", bar)
            }
        }
    }

    /** Permanent Magic level required by the existing spell. */
    val requiredLevel = SuperheatItemAction.LEVEL
    /** Primary ore targeted by the normal magic-on-item interaction. */
    private val inputId = barType.oreRequired.first.id

    /** Restores the bar selection, session configuration, and inherited retry budgets. */
    constructor(bot: Bot, data: SuperheatItemData) : this(bot, BarType.valueOf(data.bar),
        data.duration, data.zones) { restoreInventoryState(data) }

    /** One bar's ores and planned rune costs used by startup and wanted-item handling. */
    private fun materials() = barType.oreList + bot.productionRuneCosts(SuperheatItemAction.RUNES)

    /** Checks regular spellbook, both permanent skill levels, and supplies across inventory and bank. */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR &&
        bot.magic.staticLevel >= requiredLevel && bot.smithing.staticLevel >= barType.level &&
        bot.ownsProductionSupplies(materials())

    /** Balances ore quantities with banked rune costs and the slots occupied by their distinct stacks. */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(SuperheatItemAction.RUNES, bankedOnly = true)
        val amount = minOf((bot.inventory.capacity() - costs.size) / barType.oreList.sumOf { it.amount },
            barType.oreList.minOf { bot.bank.computeAmountForId(it.id) / it.amount },
            costs.minOfOrNull { bot.bank.computeAmountForId(it.id) / it.amount } ?: Int.MAX_VALUE)
        if (amount < 1) return emptyList()
        return (barType.oreList + costs).map { Item(it.id, it.amount * amount) }
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials(), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= requiredLevel &&
            bot.smithing.staticLevel >= barType.level)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    /** Checks carried supplies and prevents an iron session from accidentally selecting a steel recipe. */
    private fun canContinue(): Boolean = bot.inventory.containsAll(barType.oreList) &&
        (barType != BarType.IRON || !bot.inventory.containsAll(BarType.STEEL.oreList)) &&
        Magic.checkRequirements(bot, requiredLevel, SuperheatItemAction.RUNES) != null

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), levelEligible = bot.spellbook == Spellbook.REGULAR &&
                bot.magic.level >= requiredLevel && bot.smithing.level >= barType.level)) return true
        attemptProduction(inputId, outputId = barType.id) {
            handler.inventory.useSpellOnItem(SuperheatItemAction.SPELL_ID, inputId)
        }
        return true
    }

    override suspend fun finish() { bot.superheatDelay.cancelQueuedTask() }

    override fun snapshot(): SuperheatItemData = SuperheatItemData().also {
        it.bar = barType.name
        saveInventoryState(it)
    }
}
