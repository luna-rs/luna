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
import game.skill.crafting.textileCrafting.MakeTextileActionItem
import game.skill.crafting.textileCrafting.Textile
import game.skill.crafting.textileCrafting.TextileType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Weaves cloth, empty sacks, and baskets through Luna's existing loom recipes and player dialogue.
 *
 * The same script handles each [TextileType.LOOM] recipe. Its bank batch contains complete groups of four wool,
 * four jute, or six willow branches, respecting current stock and inventory capacity. Existing player actions
 * consume those groups and award the configured Crafting XP. No gathering or material spawning is introduced.
 *
 * [InventoryBotScript] supplies verified unnoted banking, safety, bounded progress checks, and saved retry budgets.
 * Missing inputs request a total stock target of 1,000. Loaded looms must match a supported ID and the configured
 * position inside the active subzone. Existing zone travel and teleport fallback handle reaching each location.
 *
 * @param bot The bot weaving its supplies.
 * @property textile The existing loom recipe selected for the session.
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Candidate loom subzones, retained in saved sessions.
 * @author lare96
 */
class WeaveTextileBotScript(
    bot: Bot,
    val textile: Textile,
    duration: Duration,
    zones: MutableList<SubZone> = LOOMS.keys.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    init { require(textile.type == TextileType.LOOM) { "A loom recipe is required." } }

    override val verifiedProductionWithdrawals = true

    companion object {
        /** Supported loom positions supplied with their existing travel and banking subzones. */
        val LOOMS = mapOf(SubZone.SOUTH_FALADOR_LOOM to Position(3039, 3287),
                          SubZone.IORWERTH_CAMP to Position(2198, 3249))

        /**
         * Saved loom recipe and inherited session configuration and retry budgets.
         *
         * @author lare96
         */
        class TextileData : InventoryScriptData() {
            /** [Textile] enum name identifying the selected loom recipe. */
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

    /** Minimum permanent/current Crafting level required by the player action. */
    val requiredLevel = textile.level
    /** Input quantity required for one completed textile. */
    private val materials = listOf(textile.rawItem)

    /** Restores the recipe, original zones, duration, and persistent retry budgets. */
    constructor(bot: Bot, data: TextileData) : this(bot, Textile.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent recipe eligibility and minimum owned inputs across inventory and bank. */
    fun isEligible() = bot.crafting.staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials)

    /** Returns complete recipe groups, including a smaller final bank batch. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, levelEligible = bot.crafting.staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials), bot.crafting.level >= requiredLevel)) return true
        attemptProduction(textile.rawItem.id) { startProduction() }
        return true
    }

    /** Uses an input on the configured loaded loom and selects the number of complete carried recipes. */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val position = LOOMS[zone] ?: return false
        val loom = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.position == position && it.position in zone.area && it.id in TextileType.LOOM.objectIds
        }.firstOrNull() ?: return false
        if (!handler.inventory.useItem(textile.rawItem.id).onObject(loom)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = bot.inventory.computeAmountForId(textile.rawItem.id) / textile.rawItem.amount
        if (amount < 1) return false
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeTextileActionItem::class.java)?.takeIf { it.textile == textile }?.interrupt()
    }

    override fun snapshot(): TextileData = TextileData().also {
        it.recipe = textile.name
        saveInventoryState(it)
    }
}
