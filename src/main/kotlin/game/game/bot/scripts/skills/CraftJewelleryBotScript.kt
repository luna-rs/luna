package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.crafting.jewelleryMaking.*
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Crafts one existing gold or silver jewellery recipe through the normal furnace interface.
 *
 * [InventoryBotScript] supplies banking, travel, expiry, and weak-action gating. Each bank batch reserves
 * one mould and withdraws up to twenty-seven bars for plain jewellery or thirteen bar/gem pairs. Partial
 * stock limits both ingredients equally. Missing startup inputs request total targets of 1,000; moulds use
 * a target of three. Selection requires owned supplies and the permanent recipe level; current level and
 * temporary combat, lock, death, and strong-action restrictions are checked before production.
 *
 * Recipes, moulds, widgets, and slots are derived from the existing gold and silver tables. Using the bar
 * on a loaded furnace opens the appropriate player interface, then a normal Make 1, 5, or 10 item click
 * starts [CraftJewelleryAction]. That action owns conversions, mould retention, level checks, and XP.
 * The existing Al Kharid and Falador furnace zones provide banking and travel. No missing recipes are added.
 *
 * Three failed interactions or unresolved bank requests end the session. Withdrawals must be unnoted and
 * verified within fifteen seconds; input consumption must begin within ten seconds. Saved data retains the
 * product ID, duration, zones, and both retry budgets. Finishing interrupts an active jewellery action.
 *
 * @param bot The bot crafting jewellery.
 * @property productId Output ID of an entry in the existing jewellery tables.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Candidate banked furnace zones.
 * @author lare96
 */
class CraftJewelleryBotScript(
    bot: Bot,
    val productId: Int,
    duration: Duration,
    zones: MutableList<SubZone> = DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {

        /** Banked furnace routes already used by the existing ore-smelting factory. */
        val DEFAULT_ZONES = listOf(SubZone.AL_KHARID_BANK, SubZone.FALADOR_WEST_BANK)
        /**
         * Interface coordinates and supplies for one entry in the existing gold or silver table.
         * All gameplay levels, gems, outputs, and experience remain in [jewellery].
         *
         * @property jewellery Existing player recipe.
         * @property bar Metal consumed for each item.
         * @property mould Reusable mould retained in the inventory.
         * @property widget Item widget populated by the player interface.
         * @property index Slot of the output inside that widget.
         * @author lare96
         */
        data class Recipe(val jewellery: JewelleryItem, val bar: BarType, val mould: Int,
                          val widget: Int, val index: Int)

        /** All declared recipes keyed by output ID; widget slots follow the existing interface item order. */
        val RECIPES: Map<Int, Recipe> = buildMap {
            for (table in GoldJewelleryTable.VALUES) table.jewelleryItems.forEachIndexed { index, jewellery ->
                put(jewellery.id, Recipe(jewellery, BarType.GOLD, table.mouldId, table.buttonWidgetId, index))
            }
            for (table in SilverJewelleryTable.VALUES) {
                val jewellery = table.jewelleryItem
                put(jewellery.id, Recipe(jewellery, BarType.SILVER, table.mouldId, table.mouldWidgetId, 0))
            }
        }

        /**
         * Saved furnace zones and retry counters alongside the inherited duration and candidate zones.
         * Counters survive resumption so restoring a script does not reset an exhausted retry budget.
         *
         * @author lare96
         */
        class CraftJewelleryData : InventoryScriptData() {
            /** Existing jewellery output ID used to restore the selected recipe. */
            var productId = -1

            override fun load(data: JsonObject) {
                super.load(data)
                productId = data.get("productId")?.asInt ?: -1
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("productId", productId)
            }
        }
    }

    /** Recipe resolved from the saved or selected output ID. */
    private val recipe = RECIPES.getValue(productId)
    /** Mould reserved in each balanced batch. */
    private val tools = setOf(recipe.mould)

    /** Permanent Crafting level required for selection and startup validation. */
    val requiredLevel = recipe.jewellery.level
    /** Minimum supplies for one conversion; the bar and optional gem are non-stackable. */
    private val materials = listOfNotNull(Item(recipe.bar.id), recipe.jewellery.requiredItem)

    /**
     * Restores the session configuration, and retry budgets from a snapshot.
     * Current supplies and bot safety are checked again by the normal initialization hooks.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized jewellery crafting state.
     */
    constructor(bot: Bot, data: CraftJewelleryData) :
        this(bot, data.productId, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Checks permanent level eligibility and one complete input pair across inventory and bank.
     * Temporary current-level, combat, lock, and strong-action restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials, tools)

    /** Reserves one mould and withdraws balanced inputs up to capacity, limited by available bank stock. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, tools, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials) && tools.all { it in bot.inventory }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Opens the metal-specific furnace interface and clicks a supported fixed quantity for this output. */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val furnace = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.position in zone.area && it.def().name == "Furnace" && "Smelt" in it.def().actions
        }.firstOrNull() ?: return false
        if (!handler.inventory.useItem(recipe.bar.id).onObject(furnace)) return false
        if (!waitFor(3.seconds) {
            if (recipe.bar == BarType.GOLD) GoldJewelleryInterface::class in bot.overlays
            else SilverJewelleryInterface::class in bot.overlays
        }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        if (amount < 1) return false
        val option = when {
            amount >= 10 -> 3
            amount >= 5 -> 2
            else -> 1
        }
        return output.sendItemWidgetClick(option, recipe.index, recipe.widget, productId)
    }

    override suspend fun finish() {
        bot.actions.first(CraftJewelleryAction::class.java)?.interrupt()
    }

    override fun snapshot(): CraftJewelleryData = CraftJewelleryData().also {
        it.productId = productId
        saveInventoryState(it)
    }
}
