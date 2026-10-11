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
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.crafting.jewelleryMaking.SilverJewelleryTable
import game.skill.crafting.jewelleryMaking.StringJewelleryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strings a configured unstrung gold amulet or silver symbol using owned balls of wool.
 *
 * [InventoryBotScript] manages travel, banking, session expiry, and weak-action gating. Each bank visit
 * withdraws up to fourteen balanced input pairs, limited by the smaller available stock. Production uses
 * the ordinary wool-on-jewellery interaction and make dialogue; [StringJewelleryAction] converts the inputs
 * and awards four Crafting experience per item. Stringing requires level one, regardless of the level needed
 * to manufacture the unstrung item. This script does not make, enchant, or bless jewellery.
 *
 * Missing startup inputs raise their total wanted-stock targets to at least 1,000 before stopping. Three
 * consecutive interactions without input consumption or unresolved bank requests end the session. Snapshots
 * preserve the item id, duration, zones, and retry counters; live inventory and actions are checked on resumption.
 *
 * @param bot The bot running this script.
 * @property unstrung The supported unstrung jewellery item processed throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class StringJewelleryBotScript(
    bot: Bot,
    val unstrung: Int,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Consumable used by every stringing interaction in makeJewellery.kts. */
        const val BALL_OF_WOOL = 1759
        /** Gold amulets and silver symbols supported by the existing item-on-item handler. */
        val UNSTRUNG_IDS = GoldJewelleryTable.AMULETS.jewelleryItems.map { it.id } + listOf(
            SilverJewelleryTable.SARADOMIN_SYMBOL.jewelleryItem.id,
            SilverJewelleryTable.ZAMORAK_SYMBOL.jewelleryItem.id
        )

        /**
         * Saved item id and retry counters alongside the inherited duration and candidate zones.
         * Restoring a session retains exhausted budgets rather than granting new attempts.
         *
         * @author lare96
         */
        class JewelleryData : InventoryScriptData() {
            /** Decimal unstrung item id used to reconstruct the configured recipe. */
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

    init { require(unstrung in UNSTRUNG_IDS) }

    /** Minimum Crafting level for stringing; manufacturing requirements do not apply. */
    val requiredLevel = 1
    /** One unstrung jewellery item and one ball of wool, the minimum inputs for a conversion. */
    private val materials = listOf(Item(unstrung), Item(BALL_OF_WOOL))

    /**
     * Restores the supported item id, session configuration, and retry budgets from saved state.
     * Normal lifecycle hooks validate current supplies and bot safety before production resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized jewellery-stringing state.
     */
    constructor(bot: Bot, data: JewelleryData) : this(bot, data.recipe.toInt(), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level eligibility and one input pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced banked input pairs, or an empty batch if either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete pair of stringing inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses wool on the configured jewellery, waits for its dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(BALL_OF_WOOL).onItem(unstrung)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(StringJewelleryAction::class.java)?.interrupt()
    }

    override fun snapshot(): JewelleryData = JewelleryData().also {
        it.recipe = unstrung.toString()
        saveInventoryState(it)
    }
}
