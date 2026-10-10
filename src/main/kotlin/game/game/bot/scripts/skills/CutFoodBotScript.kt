package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlinx.coroutines.withTimeoutOrNull
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
    companion object {
        /** Supported cutting recipes mapped to their non-stackable output quantities. */
        val RECIPES = mapOf(IncompleteFood.PINEAPPLE_RING to 4)
        /** Maximum consecutive failed interactions or unresolved banking requests. */
        private const val MAX_FAILURES = 3

        /**
         * Saved retry budgets alongside inherited duration and processing zones.
         *
         * @author lare96
         */
        class CutFoodData : ZonedBotScriptData() {
            /** Consecutive interactions without verified pineapple consumption. */
            var failures = 0
            /** Enum name of the supported food-cutting recipe. */
            var recipe = ""
            /** Unresolved banking requests since the last verified batch withdrawal. */
            var bankFailures = 0
            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe").asString
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
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
    /** Consecutive failed interactions, reset when a pineapple is consumed. */
    private var failures = 0
    /** Banking requests since the last completely verified withdrawal. */
    private var bankFailures = 0

    /** Restores session configuration and exhausted retry budgets without granting new attempts. */
    constructor(bot: Bot, data: CutFoodData) : this(bot, IncompleteFood.valueOf(data.recipe), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Whether the permanent Cooking level and owned pineapple/knife supplies permit selection. */
    fun isEligible(): Boolean = bot.cooking.staticLevel >= requiredLevel && bot.ownsProductionSupplies(supplies)

    /** Withdraws one tool and a batch sized for the selected recipe output count. */
    fun bankBatch(): List<Item> {
        if (!bot.bank.contains(knife)) return emptyList()
        val amount = minOf((bot.inventory.capacity() - 1) / outputs, bot.bank.computeAmountForId(ingredient))
        return if (amount > 0) listOf(Item(knife), Item(ingredient, amount)) else emptyList()
    }

    /** Safety required before initiating item or banking interactions. */
    private fun safe() = bot.health > 0 && !bot.isLocked && !bot.combat.inCombat() &&
        bot.actions.size(ActionType.STRONG) == 0

    /** Whether one ingredient and its preserved tool are present in inventory. */
    private fun hasSupplies() = bot.inventory.containsAll(supplies)

    /** Reserves the additional slots required to finish every carried ingredient for this recipe. */
    private fun hasOutputSpace() = bot.inventory.computeRemainingSize().toLong() >=
        bot.inventory.computeAmountForId(ingredient).toLong() * (outputs - 1)

    override fun withdraw(): List<Item> {
        if (!safe() || bot.cooking.staticLevel < requiredLevel || bot.cooking.level < requiredLevel ||
            failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(supplies)) {
            for (item in supplies) {
                val owned = bot.bank.computeAmountForId(item.id).toLong() + bot.inventory.computeAmountForId(item.id)
                if (owned < 1) bot.preferences.raiseWantedItemTarget(item.id, if (item.id == knife) 3 else 1_000)
            }
            stop()
            return emptyList()
        }
        forceBanking = true
        return supplies
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun withdrawBankItems(items: List<Item>): Boolean {
        val success = withTimeoutOrNull(15_000) {
            handler.banking.clickBankingMode(false)
            handler.banking.withdrawAll(items) && bot.inventory.containsAll(items)
        } == true
        if (success) {
            bankFailures = 0
            forceBanking = false
        }
        return success
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (!safe()) return false
        if (!forceBanking && hasSupplies() && hasOutputSpace()) return false
        if (bankFailures >= MAX_FAILURES) {
            stop()
            return false
        }
        bankFailures++
        return true
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!safe()) return true
        if (bot.cooking.level < requiredLevel) {
            stop()
            return true
        }
        if (!hasSupplies() || !hasOutputSpace()) {
            forceBanking = true
            return true
        }
        val before = bot.inventory.computeAmountForId(ingredient)
        val started = withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && startProduction(before)
        } == true
        val progressed = started && waitFor(10.seconds) { bot.inventory.computeAmountForId(ingredient) < before }
        if (progressed) failures = 0 else if (++failures >= MAX_FAILURES) stop()
        bot.naturalDexterityDelay()
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
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}