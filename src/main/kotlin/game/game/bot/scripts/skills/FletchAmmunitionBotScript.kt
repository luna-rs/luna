package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.bolts.Bolt
import game.skill.fletching.bolts.BoltTip
import game.skill.fletching.darts.Dart
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Makes darts, tipped bolts or bolt tips through their native item-on-item interactions.
 * These recipes produce one immediate batch per use without opening a make-item dialogue. Native recipe
 * definitions supply the levels, inputs, outputs and batch sizes; this script shares their banking and execution.
 *
 * Owned partial stacks qualify. Each interaction checks room for its output after consumed inputs leave, including
 * the two tips made from a gem or pearl. The inventory lifecycle supplies safe travel, verified withdrawals,
 * expiry and bounded retries. Saved sessions retain the recipe and inherited retry state.
 *
 * @param bot The bot making ammunition.
 * @property recipe The native ammunition or tip recipe retained for this session.
 * @param duration The production session duration.
 * @param zones Existing processing zones with bank and travel support.
 * @author lare96
 */
class FletchAmmunitionBotScript(
    bot: Bot,
    val recipe: Recipe,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_FLETCHING
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * One native direct-use recipe, with an optional reusable chisel instead of a second consumed input.
         *
         * @author lare96
         */
        data class Recipe(val key: String, val level: Int, val used: Int, val target: Int, val output: Int,
                          val batchSize: Int, val outputPerInput: Int = 1, val tool: Boolean = false) {
            val materials: List<Item> = if (tool) listOf(Item(target)) else listOf(Item(used), Item(target))
            val tools: Set<Int> = if (tool) setOf(used) else emptySet()
        }

        /**
         * Native recipes keyed by their type and enum name so saved sessions distinguish pearl-tip sources.
         */
        val RECIPES: Map<String, Recipe> = (
            Dart.entries.map { Recipe("DART:${it.name}", it.level, Dart.FEATHER, it.tip, it.id, Dart.BATCH_AMOUNT) } +
                Bolt.entries.map { Recipe("BOLT:${it.name}", it.level, Bolt.BOLTS, it.tip, it.id, Bolt.SET_AMOUNT) } +
                BoltTip.entries.map {
                    Recipe("TIP:${it.name}", it.level, BoltTip.CHISEL, it.source, it.tip, 1, it.amount, tool = true)
                }
            ).associateBy { it.key }

        /**
         * Maximum paired stackable inputs carried on one banking trip.
         */
        private const val WITHDRAW_AMOUNT = 1_000

        /**
         * Saved recipe key and inherited session/retry state.
         *
         * @author lare96
         */
        class AmmunitionData : InventoryScriptData() {
            var recipe = ""

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe").asString
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /**
     * Permanent Fletching level used by factory selection and startup validation.
     */
    val requiredLevel = recipe.level

    /**
     * Restores the native recipe, zones, duration and retry budgets.
     */
    constructor(bot: Bot, data: AmmunitionData) :
        this(bot, RECIPES.getValue(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Checks the native level and one owned production unit, including a chisel for tip shaping.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
        bot.ownsProductionSupplies(recipe.materials, recipe.tools)

    /**
     * Balances attachment inputs up to one thousand pairs, or reserves a chisel and space for shaped tips.
     */
    fun bankBatch(): List<Item> {
        if (recipe.tools.isNotEmpty() || !bot.inventory.stackable(recipe.output) ||
            recipe.materials.any { !bot.inventory.stackable(it.id) }) {
            return bot.productionBatch(recipe.materials, recipe.tools)
        }
        val amount = minOf(WITHDRAW_AMOUNT, recipe.materials.minOf { bot.bank.computeAmountForId(it.id) })
        return if (amount > 0) recipe.materials.map { Item(it.id, amount) } else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(recipe.materials, recipe.tools,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /**
     * Checks native partial-batch consumption and the slots occupied by its resulting ammunition or tips.
     */
    private fun hasMaterials(): Boolean {
        if (!bot.inventory.containsAll(recipe.materials) || recipe.tools.any { it !in bot.inventory }) return false
        val amount = minOf(recipe.batchSize, recipe.materials.minOf { bot.inventory.computeAmountForId(it.id) })
        val outputAmount = amount * recipe.outputPerInput
        if (bot.inventory.stackable(recipe.output) &&
            bot.inventory.computeAmountForId(recipe.output).toLong() + outputAmount > Int.MAX_VALUE) return false
        val freedSlots = recipe.materials.sumOf {
            if (!bot.inventory.stackable(it.id)) amount
            else if (bot.inventory.computeAmountForId(it.id) == amount) 1 else 0
        }
        val outputSlots = if (!bot.inventory.stackable(recipe.output)) outputAmount
            else if (bot.inventory.contains(recipe.output)) 0 else 1
        return bot.inventory.computeRemainingSize() + freedSlots >= outputSlots
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        attemptProduction(recipe.materials.first().id, outputId = recipe.output) {
            handler.inventory.useItem(recipe.used).onItem(recipe.target)
        }
        return true
    }

    override fun snapshot(): AmmunitionData = AmmunitionData().also {
        it.recipe = recipe.key
        saveInventoryState(it)
    }
}
