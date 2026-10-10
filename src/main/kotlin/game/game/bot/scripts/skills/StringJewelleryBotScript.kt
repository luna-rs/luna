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
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.crafting.jewelleryMaking.SilverJewelleryTable
import game.skill.crafting.jewelleryMaking.StringJewelleryAction
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

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

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

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
        class JewelleryData : ZonedBotScriptData() {
            /** Decimal unstrung item id used to reconstruct the configured recipe. */
            var recipe = ""
            /** Consecutive interactions that did not consume unstrung jewellery. */
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

    init { require(unstrung in UNSTRUNG_IDS) }

    /** Minimum Crafting level for stringing; manufacturing requirements do not apply. */
    val requiredLevel = 1
    /** One unstrung jewellery item and one ball of wool, the minimum inputs for a conversion. */
    private val materials = listOf(Item(unstrung), Item(BALL_OF_WOOL))
    /** Consecutive failed interactions, reset after an input is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset only after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the supported item id, session configuration, and retry budgets from saved state.
     * Normal lifecycle hooks validate current supplies and bot safety before production resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized jewellery-stringing state.
     */
    constructor(bot: Bot, data: JewelleryData) : this(bot, data.recipe.toInt(), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Checks permanent level eligibility and one input pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced banked input pairs, or an empty batch if either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_CRAFTING).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials)) {
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

    /** Whether inventory contains at least one complete pair of stringing inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
        if (bot.skill(SKILL_CRAFTING).level < requiredLevel) {
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
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
