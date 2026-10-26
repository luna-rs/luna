package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.bonesToItems.BonesToItemsAction
import game.skill.magic.bonesToItems.BonesToItemsAction.Companion.BONES
import game.skill.magic.bonesToItems.BonesToItemsAction.Companion.bonesToItemsDelay
import game.skill.magic.bonesToItems.BonesToItemsType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Prepares bananas or peaches from owned normal bones through the existing spellbook button.
 *
 * The native action converts every carried normal bone in one cast. Banking therefore reserves one stack per rune
 * type and withdraws only one cast's rune cost, then fills the remaining slots with bones. It handles partial stock
 * and the final amount needed for [FOOD_TARGET]; other bone types are never converted. Native spell requirements
 * resolve staff and combination-rune substitutions and own consumption, conversion, experience and cooldowns.
 *
 * InventoryBotScript supplies safe bank travel, verified withdrawals, bounded retries, account-build XP checks and
 * progress detection. This is a bank-based preparation session; combat scripts and normal food handlers can use its
 * banked products without acquiring a separate opportunistic casting behavior. Saved state retains the food recipe,
 * remaining duration, processing hubs and shared failure counters.
 *
 * @param bot The bot preparing food.
 * @property type The existing bone-conversion spell selected for this session.
 * @param duration Remaining session time.
 * @param zones Existing safe Magic processing hubs.
 * @author lare96
 */
class BonesToFoodBotScript(
    bot: Bot, val type: BonesToItemsType, duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_MAGIC)
) : InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_MAGIC
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Total owned stock to prepare before yielding to another activity.
         */
        const val FOOD_TARGET = 500

        /**
         * Recipe selection stored alongside the common inventory-production lifecycle state.
         *
         * @author lare96
         */
        class BonesToFoodData : InventoryScriptData() {
            /**
             * Native spell enum name used to reconstruct the session.
             */
            var recipe = BonesToItemsType.BANANAS.name

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: BonesToItemsType.BANANAS.name
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /**
     * Restores a preparation session without generating food, bones or rune supplies.
     */
    constructor(bot: Bot, data: BonesToFoodData) : this(bot, BonesToItemsType.valueOf(data.recipe),
        data.duration, data.zones) { restoreInventoryState(data) }

    /**
     * Minimum input stock for one native cast, used by shared startup and total wanted-item targets.
     */
    private fun materials(): List<Item> = listOf(Item(BONES)) + bot.productionRuneCosts(type.requirements)

    /**
     * Uses Luna's current player spell eligibility and owned stock; no unlock flags or free materials are invented.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= type.level &&
        bot.itemTracker.count(type.id) < FOOD_TARGET && bot.ownsProductionSupplies(materials())

    /**
     * Plans one whole-inventory conversion, charging one rune payment regardless of the bone count.
     */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(type.requirements, bankedOnly = true)
        if (costs.any { bot.bank.computeAmountForId(it.id) < it.amount }) return emptyList()
        val amount = minOf(bot.inventory.capacity() - costs.size, bot.bank.computeAmountForId(BONES),
            FOOD_TARGET - bot.itemTracker.count(type.id))
        return if (amount <= 0) emptyList() else costs + Item(BONES, amount)
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials(), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= type.level &&
            bot.itemTracker.count(type.id) < FOOD_TARGET)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    /**
     * Quotes the actual carried costs without emitting missing-supply messages on each banking check.
     */
    private fun canContinue(): Boolean = bot.inventory.contains(BONES) &&
        Magic.spellCost(bot, type.level, type.requirements) is SpellCost.Castable

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), levelEligible = bot.spellbook == Spellbook.REGULAR &&
                bot.magic.level >= type.level)) return true
        attemptProduction(BONES, outputId = type.id) { output.clickButton(type.spellId) }
        return true
    }

    override suspend fun finish() { bot.bonesToItemsDelay.cancelQueuedTask() }

    override fun snapshot(): BonesToFoodData = BonesToFoodData().also {
        it.recipe = type.name
        saveInventoryState(it)
    }
}
