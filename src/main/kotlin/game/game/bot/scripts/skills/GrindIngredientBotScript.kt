package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.herblore.grindIngredient.GrindActionItem
import game.skill.herblore.grindIngredient.Ingredient
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Grinds one configured ingredient with a retained pestle and mortar through the normal make-item dialogue.
 *
 * [InventoryBotScript] supplies banking, zone travel, session expiry, and weak-action gating. After depositing
 * the previous inventory, the script withdraws one pestle and mortar and up to twenty-seven ingredients.
 * Smaller remaining batches are allowed; exhausted inputs or a missing tool end the session.
 *
 * The existing [GrindActionItem] performs conversion without awarding Herblore experience. This activity
 * prepares owned supplies for other uses and is selected only by the factory's profit mode. It neither buys
 * inputs nor assumes that their processed form will sell at a profit. Missing startup supplies are added to
 * wanted items before stopping.
 *
 * Each interaction must consume an input within ten seconds. Three consecutive failed interactions or
 * unresolved banking requests exhaust their separate budgets. Withdrawals are unnoted, verified, and limited
 * to fifteen seconds; failed withdrawals stop through the inherited banking hook. Snapshots retain the recipe,
 * duration, candidate zones, and retry counters rather than live inventory slots or actions.
 *
 * @param bot The bot running this script.
 * @property ingredient The grinding recipe to process throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class GrindIngredientBotScript(
    bot: Bot,
    val ingredient: Ingredient,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved recipe and retry counters alongside the inherited duration and candidate zones.
         * Restoring a session retains exhausted retry budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class IngredientData : InventoryScriptData() {
            /** [Ingredient] enum name used to reconstruct the configured recipe. */
            var recipe = ""

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /** Grinding has no recipe-specific Herblore level gate in the existing gameplay action. */
    val requiredLevel = 1
    /** Minimum ingredient supply for one conversion. */
    private val materials = listOf(ingredient.oldItem)
    /** The reusable tool reserved in each batch and retained by the grinding action. */
    private val tools = setOf(Ingredient.PESTLE_AND_MORTAR)

    /**
     * Restores the recipe, session configuration, and retry budgets from a saved snapshot.
     * Initialization still checks current supplies and bot safety before the script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized ingredient-grinding state.
     */
    constructor(bot: Bot, data: IngredientData) :
        this(bot, Ingredient.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level eligibility and an owned ingredient plus pestle and mortar across inventory and bank. */
    fun isEligible(): Boolean =
        bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials, tools)

    /** Returns one mortar and up to twenty-seven banked ingredients, or an empty batch when either is missing. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, tools, levelEligible = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains the reusable tool and at least one configured ingredient. */
    private fun hasMaterials() = bot.inventory.containsAll(materials) && tools.all { it in bot.inventory }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_HERBLORE).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses the mortar on an ingredient, waits for the single-recipe dialogue, and requests the carried batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(Ingredient.PESTLE_AND_MORTAR).onItem(ingredient.id)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(GrindActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): IngredientData = IngredientData().also {
        it.recipe = ingredient.name
        saveInventoryState(it)
    }
}
