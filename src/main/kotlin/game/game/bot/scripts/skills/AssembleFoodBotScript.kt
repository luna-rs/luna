package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.bot.scripts.FillWaterBotScript
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Assembles validated food recipes using owned input pairs and the existing player make dialogue.
 *
 * Supports plain-pizza assembly and toppings, pie shells, and all implemented pie assembly stages. Cooking
 * requirements range from level 1 to 95. Meat, anchovy, and pineapple toppings award 26, 39, and 45 Cooking XP
 * respectively; plain-pizza and pie assembly award none. Each step uses two inputs and creates
 * a product, plus an empty container when water is used. [PrepareFoodActionItem] owns conversions and returns.
 * Alternative meat, compost, and water inputs are selected explicitly. No recipe expands its inventory footprint.
 *
 * [InventoryBotScript] handles banking, travel, session expiry, and weak-action gating. Each bank batch contains
 * up to fourteen balanced pairs. Missing startup ingredients request total stock targets of 1,000. Three failed
 * interactions or unresolved bank requests stop the script; snapshots preserve the recipe and retry budgets.
 * Missing water can queue the reusable filling prerequisite from owned containers. Non-training Cooking selects
 * owned supplies or an available refill; training selects only recipes that award experience. Dough making and
 * baking remain separate activities, and higher-tier pie baking still requires entries in the cooking table.
 *
 * @param bot The bot assembling food.
 * @property food A supported preparation recipe.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking support.
 * @property secondary The selected ingredient from the recipe alternatives, consumed one per operation.
 * @author lare96
 */
class AssembleFoodBotScript(
    bot: Bot,
    val food: IncompleteFood,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList(),
    val secondary: Int = food.otherIngredients.first()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        /** Validated two-input recipes whose outputs fit within their input inventory footprint. */
        val RECIPES = setOf(
            IncompleteFood.INCOMPLETE_PIZZA, IncompleteFood.UNCOOKED_PLAIN_PIZZA,
            IncompleteFood.MEAT_PIZZA, IncompleteFood.ANCHOVY_PIZZA, IncompleteFood.PINEAPPLE_PIZZA,
            IncompleteFood.PIE_SHELL, IncompleteFood.UNCOOKED_BERRY_PIE, IncompleteFood.UNCOOKED_MEAT_PIE,
            IncompleteFood.PART_MUD_PIE_1, IncompleteFood.PART_MUD_PIE_2, IncompleteFood.RAW_MUD_PIE,
            IncompleteFood.UNCOOKED_APPLE_PIE,
            IncompleteFood.PART_GARDEN_PIE_1, IncompleteFood.PART_GARDEN_PIE_2, IncompleteFood.RAW_GARDEN_PIE,
            IncompleteFood.PART_FISH_PIE_1, IncompleteFood.PART_FISH_PIE_2, IncompleteFood.RAW_FISH_PIE,
            IncompleteFood.PART_ADMIRAL_PIE_1, IncompleteFood.PART_ADMIRAL_PIE_2, IncompleteFood.RAW_ADMIRAL_PIE,
            IncompleteFood.PART_SUMMER_PIE_1, IncompleteFood.PART_SUMMER_PIE_2, IncompleteFood.RAW_SUMMER_PIE
        )
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Saved recipe and retry counters alongside the inherited duration and candidate zones.
         * Counters survive resumption so restoring a script does not reset an exhausted retry budget.
         *
         * @author lare96
         */
        class AssemblyData : ZonedBotScriptData() {
            /** [IncompleteFood] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Selected alternate ingredient or filled water container. */
            var secondary = 0
            /** Consecutive interactions that did not consume a base ingredient. */
            var failures = 0
            /** Banking requests since the last successfully verified withdrawal. */
            var bankFailures = 0

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                secondary = data.get("secondary")?.asInt ?: IncompleteFood.valueOf(recipe).otherIngredients.first()
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("secondary", secondary)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
            }
        }
    }

    init {
        require(food in RECIPES)
        require(secondary in food.otherIngredients)
    }

    /** Permanent Cooking level required for selection and startup validation. */
    val requiredLevel = food.lvl
    /** One base ingredient and one secondary ingredient, the minimum supplies for a single conversion. */
    private val materials = listOf(Item(food.baseIngredient), Item(secondary))
    /** Consecutive failed interactions, reset after an input is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset only after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the recipe, session configuration, and retry budgets from a snapshot.
     * Current supplies and bot safety are checked again by the normal initialization hooks.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized food-assembly state.
     */
    constructor(bot: Bot, data: AssemblyData) :
        this(bot, IncompleteFood.valueOf(data.recipe), data.duration, data.zones, data.secondary) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /**
     * Checks permanent level eligibility and one complete input pair across inventory and bank.
     * Temporary current-level, combat, lock, and strong-action restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_COOKING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Whether owned base ingredients and empty containers can satisfy a missing water ingredient. */
    fun canPrepareWater(): Boolean = secondary in WaterResource.FILLED_IDS &&
        bot.cooking.staticLevel >= requiredLevel && !bot.ownsProductionSupplies(listOf(Item(secondary))) &&
        bot.ownsProductionSupplies(listOf(Item(food.baseIngredient),
            Item(WaterResource.FILLABLES.inverse().getValue(secondary))))

    /** Queues one bounded refill using owned containers, then returns selection to the activity coordinator. */
    private fun queueWaterPreparation(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0 || !canPrepareWater()) return false
        val empty = WaterResource.FILLABLES.inverse().getValue(secondary)
        val baseStock = bot.bank.computeAmountForId(food.baseIngredient).toLong() +
            bot.inventory.computeAmountForId(food.baseIngredient)
        val emptyStock = bot.bank.computeAmountForId(empty).toLong() + bot.inventory.computeAmountForId(empty)
        bot.scriptStack.softPushHead(FillWaterBotScript(bot, empty, minOf(14L, baseStock, emptyStock).toInt(), duration))
        stop()
        return true
    }
    /** Returns up to fourteen balanced input pairs from bank stock, or an empty batch if either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_COOKING).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials)) {
            if (queueWaterPreparation()) return emptyList()
            val missing = materials.filter {
                bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id) < it.amount
            }.map { it.id }
            missing.forEach { bot.preferences.raiseWantedItemTarget(it, 1_000) }
            stop()
            return emptyList()
        }
        // InventoryBotScript validates these minimum supplies; the bank hook calculates the actual batch.
        forceBanking = true
        return materials
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty() && !queueWaterPreparation()) stop()
    }

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
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return false
        if (!forceBanking && hasMaterials()) return false
        if (bankFailures >= MAX_FAILURES) {
            stop()
            return false
        }
        bankFailures++
        return true
    }

    /** Whether inventory contains at least one complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
        if (bot.skill(SKILL_COOKING).level < requiredLevel) {
            stop()
            return true
        }
        if (!hasMaterials()) {
            forceBanking = true
            return true
        }
        val amount = bot.inventory.computeAmountForId(materials.first().id)
        val started = withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && startProduction()
        } == true
        val progressed = started && waitFor(10.seconds) {
            bot.inventory.computeAmountForId(materials.first().id) < amount
        }
        if (progressed) failures = 0
        else if (++failures >= MAX_FAILURES) stop()
        bot.naturalDexterityDelay()
        return true
    }

    /** Uses the recipe ingredients, waits for its single-item dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(food.baseIngredient).onItem(secondary)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(PrepareFoodActionItem::class.java)?.takeIf { it.food == food }?.interrupt()
    }

    override fun snapshot(): AssemblyData = AssemblyData().also {
        it.recipe = food.name
        it.secondary = secondary
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
