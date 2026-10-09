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
import game.skill.herblore.grindIngredient.GrindActionItem
import game.skill.herblore.grindIngredient.Ingredient
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

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

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Saved recipe and retry counters alongside the inherited duration and candidate zones.
         * Restoring a session retains exhausted retry budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class IngredientData : ZonedBotScriptData() {
            /** [Ingredient] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Consecutive interactions that did not consume an ingredient. */
            var failures = 0
            /** Banking requests since the last successfully verified withdrawal. */
            var bankFailures = 0

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
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

    /** Grinding has no recipe-specific Herblore level gate in the existing gameplay action. */
    val requiredLevel = 1
    /** Minimum ingredient supply for one conversion. */
    private val materials = listOf(ingredient.oldItem)
    /** The reusable tool reserved in each batch and retained by the grinding action. */
    private val tools = setOf(Ingredient.PESTLE_AND_MORTAR)
    /** Consecutive failed interactions, reset after an ingredient is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the recipe, session configuration, and retry budgets from a saved snapshot.
     * Initialization still checks current supplies and bot safety before the script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized ingredient-grinding state.
     */
    constructor(bot: Bot, data: IngredientData) :
        this(bot, Ingredient.valueOf(data.recipe), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Checks permanent level eligibility and an owned ingredient plus pestle and mortar across inventory and bank. */
    fun isEligible(): Boolean =
        bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials, tools)

    /** Returns one mortar and up to twenty-seven banked ingredients, or an empty batch when either is missing. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_HERBLORE).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials, tools)) {
            val missing = materials.filter {
                bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id) < it.amount
            }.map { it.id } + tools.filter { !bot.ownsProductionSupplies(emptyList(), setOf(it)) }
            missing.forEach { bot.preferences.addWantedItem(it, if (it in tools) 3 else 28) }
            stop()
            return emptyList()
        }
        // InventoryBotScript validates these minimum supplies; the bank hook calculates the actual batch.
        forceBanking = true
        return tools.map { Item(it) } + materials
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
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

    /** Whether inventory contains the reusable tool and at least one configured ingredient. */
    private fun hasMaterials() = bot.inventory.containsAll(materials) && tools.all { it in bot.inventory }

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
        if (bot.skill(SKILL_HERBLORE).level < requiredLevel) {
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
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
