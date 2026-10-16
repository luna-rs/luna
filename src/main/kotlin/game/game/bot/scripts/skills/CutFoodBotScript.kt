package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cuts supported owned foods using the normal item interaction and make dialogue.
 *
 * [PrepareFoodActionItem] consumes each pineapple, preserves the knife, and produces four rings without Cooking
 * experience. Bank batches contain one knife and at most six pineapples, reserving space for twenty-four rings.
 * [InventoryBotScript] manages the existing zone travel, banking, session expiry, and weak-action lifecycle.
 * Cooking's non-training selection can choose this preparation when the bot owns its supplies.
 *
 * Missing startup supplies raise total wanted-stock targets to 1,000 pineapples or three knives before stopping.
 * Three consecutive failed interactions or unresolved bank requests stop the script. Retry budgets, duration,
 * and zones survive persistence; current level, inventory space, and bot safety are rechecked on resumption.
 *
 * @param bot The bot cutting food.
 * @property food A validated cutting recipe from [RECIPES].
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking support.
 * @author lare96
 */
class CutFoodBotScript(
    bot: Bot,
    val food: IncompleteFood,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true
    companion object {
        /** Supported cutting recipes mapped to their non-stackable output quantities. */
        val RECIPES = mapOf(IncompleteFood.PINEAPPLE_RING to 4)

        /**
         * Saved retry budgets alongside inherited duration and processing zones.
         *
         * @author lare96
         */
        class CutFoodData : InventoryScriptData() {
            /** Enum name of the supported food-cutting recipe. */
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

    init { require(food in RECIPES) }

    /** Consumable selected by the existing preparation table. */
    val ingredient = food.baseIngredient
    /** Preserved cutting tool selected by the existing item interaction. */
    val knife = food.otherIngredients.single()
    /** Non-stackable outputs produced by the validated gameplay recipe. */
    private val outputs = RECIPES.getValue(food)

    /** Cooking requirement enforced by the ordinary preparation action. */
    val requiredLevel = food.lvl
    /** Minimum owned supplies for one operation, including the preserved tool. */
    private val supplies = listOf(Item(ingredient), Item(knife))

    /** Restores session configuration and exhausted retry budgets without granting new attempts. */
    constructor(bot: Bot, data: CutFoodData) : this(bot, IncompleteFood.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Whether the permanent Cooking level and owned pineapple/knife supplies permit selection. */
    fun isEligible(): Boolean = bot.cooking.staticLevel >= requiredLevel && bot.ownsProductionSupplies(supplies)

    /** Withdraws one tool and a batch sized for the selected recipe output count. */
    fun bankBatch(): List<Item> {
        if (!bot.bank.contains(knife)) return emptyList()
        val amount = minOf((bot.inventory.capacity() - 1) / outputs, bot.bank.computeAmountForId(ingredient))
        return if (amount > 0) listOf(Item(knife), Item(ingredient, amount)) else emptyList()
    }

    /** Whether one ingredient and its preserved tool are present in inventory. */
    private fun hasSupplies() = bot.inventory.containsAll(supplies)

    /** Reserves the additional slots required to finish every carried ingredient for this recipe. */
    private fun hasOutputSpace() = bot.inventory.computeRemainingSize().toLong() >=
        bot.inventory.computeAmountForId(ingredient).toLong() * (outputs - 1)

    override fun withdraw(): List<Item> = productionWithdraw(listOf(Item(ingredient)), setOf(knife),
        levelEligible = bot.cooking.staticLevel >= requiredLevel && bot.cooking.level >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasSupplies() && hasOutputSpace())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasSupplies() && hasOutputSpace(), levelEligible = bot.cooking.level >= requiredLevel)) return true
        attemptProduction(ingredient) { amount -> startProduction(amount) }
        return true
    }

    /** Opens the single-recipe player dialogue and requests the safely sized inventory batch. */
    private suspend fun startProduction(amount: Int): Boolean {
        if (!handler.inventory.useItem(ingredient).onItem(knife)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(PrepareFoodActionItem::class.java)?.takeIf { it.food == food }?.interrupt()
    }

    override fun snapshot(): CutFoodData = CutFoodData().also {
        it.recipe = food.name
        saveInventoryState(it)
    }
}