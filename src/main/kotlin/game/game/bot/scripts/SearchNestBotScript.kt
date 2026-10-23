package game.bot.scripts

import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Searches owned, unsearched bird nests through the normal first inventory option.
 *
 * Each search replaces one nest with an empty nest and adds one reward from Luna's existing [Nest] table.
 * Banking withdraws at most half an inventory of one nest type, reserving one reward slot per search even when
 * every reward is non-stackable. [InventoryBotScript] supplies the existing banking, travel, session-expiry,
 * and weak-action lifecycle in supported stationary processing zones.
 *
 * General activities select this script from owned inventory or bank stock. No nests are purchased or requested,
 * and the script does not gather nests, grant experience, or change their reward distribution. The woodcutting
 * script's existing pickup-and-search behavior remains separate. Missing supplies end the session; three failed
 * interactions or unresolved banking requests also stop it. Snapshots retain the nest type and retry budgets.
 *
 * @param bot The bot searching its nests.
 * @property nest The supported unsearched nest type processed throughout this session.
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking and travel support.
 * @author lare96
 */
class SearchNestBotScript(
    bot: Bot,
    val nest: Nest,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved nest recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class NestData : InventoryScriptData() {
            /** [Nest] enum name used to reconstruct the configured recipe. */
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

    /** One unsearched nest, the minimum supply for a single conversion. */
    private val materials = listOf(Item(nest.id))

    /**
     * Restores the nest, session configuration, and retry budgets from a snapshot.
     * Initialization still checks current owned supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized nest-search state.
     */
    constructor(bot: Bot, data: NestData) : this(bot, Nest.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks at least one unsearched nest across inventory and bank. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen banked unsearched nests, or an empty batch when stock is exhausted. */
    fun bankBatch(): List<Item> {
        val amount = minOf(bot.inventory.capacity() / 2, bot.bank.computeAmountForId(nest.id))
        return if (amount > 0) listOf(Item(nest.id, amount)) else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials() && bot.inventory.computeRemainingSize() > 0)

    /** Whether inventory contains at least one unsearched nest of the configured type. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials() && bot.inventory.computeRemainingSize() > 0)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Clicks the nest's first inventory option to invoke the existing search handler once. */
    private suspend fun startProduction(): Boolean = handler.inventory.clickItem(1, nest.id)

    override fun snapshot(): NestData = NestData().also {
        it.recipe = nest.name
        saveInventoryState(it)
    }
}
