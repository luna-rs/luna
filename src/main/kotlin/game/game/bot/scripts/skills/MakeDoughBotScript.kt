package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.obj.resource.fillable.WaterResource
import game.bot.scripts.FillWaterBotScript
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import game.skill.cooking.prepareFood.IncompleteFood
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Prepares a supported dough recipe from owned pots of flour and a configured water container.
 *
 * The normal item-on-item handler opens the shared dough dialogue, and [PrepareFoodActionItem] enforces Cooking
 * levels, consumes inputs, and returns a dough, empty pot, and empty water container without experience.
 * Each bank batch contains at most nine pairs: two inputs become three non-stackable outputs. Existing travel,
 * banking, expiry, and action gating come from [InventoryBotScript]. Preparation is selected only in non-training
 * Cooking mode; no sale price or profitability is assumed.
 *
 * Missing water can queue the reusable [FillWaterBotScript] when flour and matching empties are owned. Missing
 * consumables request total stock targets of 1,000. Three failed interactions or unresolved bank requests stop
 * the session; persistence retains retry budgets, recipe, water type, zones, and duration.
 *
 * @param bot The bot preparing dough.
 * @property food A recipe from [IncompleteFood.DOUGH].
 * @property water The filled water container consumed by this session.
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking support.
 * @author lare96
 */
class MakeDoughBotScript(
    bot: Bot,
    val food: IncompleteFood,
    val water: Int,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Pot of flour consumed by the existing dough handler. */
        const val FLOUR = 1933

        /**
         * Saved water-container id and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains exhausted budgets instead of granting new attempts.
         *
         * @author lare96
         */
        class DoughData : InventoryScriptData() {
            /** Enum name of the configured dough recipe. */
            var recipe = ""
            /** Filled water item id paired with the flour. */
            var water = 0

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                water = data.get("water").asInt
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("water", water)
            }
        }
    }

    init {
        require(food in IncompleteFood.DOUGH.values)
        require(water in food.otherIngredients)
    }

    /** Cooking requirement enforced by the existing preparation action; these recipes grant no experience. */
    val requiredLevel = food.lvl
    /** One flour and one filled water container, the minimum inputs for a conversion. */
    private val materials = listOf(Item(FLOUR), Item(water))

    /**
     * Restores the water-container id, session configuration, and retry budgets from saved state.
     * Normal lifecycle hooks recheck current supplies and bot safety before preparation resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized dough preparation state.
     */
    constructor(bot: Bot, data: DoughData) :
        this(bot, IncompleteFood.valueOf(data.recipe), data.water, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Whether at least one flour and configured filled water container are owned across inventory and bank. */
    fun isEligible(): Boolean = bot.cooking.staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials)

    /** Whether owned flour and matching empties can satisfy missing water through the refill prerequisite. */
    fun canPrepareWater(): Boolean = bot.cooking.staticLevel >= requiredLevel &&
        !bot.ownsProductionSupplies(listOf(Item(water))) &&
        bot.ownsProductionSupplies(listOf(Item(FLOUR), Item(WaterResource.FILLABLES.inverse().getValue(water))))

    /** Queues one bounded refill only when both flour and matching empties remain; no acquisition loop is created. */
    private fun queueWaterPreparation(): Boolean {
        if (!isInventoryActionSafe()) return false
        if (!canPrepareWater()) return false
        val empty = WaterResource.FILLABLES.inverse().getValue(water)
        val flourStock = bot.bank.computeAmountForId(FLOUR).toLong() + bot.inventory.computeAmountForId(FLOUR)
        val emptyStock = bot.bank.computeAmountForId(empty).toLong() + bot.inventory.computeAmountForId(empty)
        bot.scriptStack.softPushHead(FillWaterBotScript(bot, empty, minOf(9L, flourStock, emptyStock).toInt(), duration))
        stop()
        return true
    }

    /** Returns up to nine balanced banked input pairs, or an empty batch when either input is absent. */
    fun bankBatch(): List<Item> {
        val pairs = minOf(bot.inventory.capacity() / 3, bot.bank.computeAmountForId(FLOUR),
            bot.bank.computeAmountForId(water))
        return if (pairs > 0) listOf(Item(FLOUR, pairs), Item(water, pairs)) else emptyList()
    }

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, levelEligible = bot.cooking.level >= requiredLevel, prepareMissing = { queueWaterPreparation() })

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty() && !queueWaterPreparation()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials() && hasOutputSpace())

    /** Whether inventory contains at least one complete flour/water input pair. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials() && hasOutputSpace(), levelEligible = bot.cooking.level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Reserves one additional slot per pair for the returned pot. */
    private fun hasOutputSpace(): Boolean = bot.inventory.computeRemainingSize() >=
        minOf(bot.inventory.computeAmountForId(FLOUR), bot.inventory.computeAmountForId(water))

    /** Opens the shared player dialogue and selects the recipe using its actual table index. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(FLOUR).onItem(water)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        handler.widgets.clickMakeItem(IncompleteFood.DOUGH.keys.indexOf(food.id),
            minOf(bot.inventory.computeAmountForId(FLOUR), bot.inventory.computeAmountForId(water)))
        return true
    }

    override suspend fun finish() {
        bot.actions.first(PrepareFoodActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): DoughData = DoughData().also {
        it.recipe = food.name
        it.water = water
        saveInventoryState(it)
    }
}
