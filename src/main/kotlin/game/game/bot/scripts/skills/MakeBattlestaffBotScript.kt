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
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.battlestaffCrafting.MakeBattlestaffActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Assembles one configured elemental battlestaff from owned battlestaves and matching charged orbs.
 *
 * [InventoryBotScript] supplies zone travel, banking, session expiry, and weak-action gating. After depositing
 * the previous inventory, each bank visit withdraws up to fourteen of each input. The smaller available stock
 * limits the batch, allowing the last partial batch to be processed without requesting unavailable supplies.
 *
 * Production uses the ordinary item-on-item interaction and single-recipe make dialogue. The existing
 * [MakeBattlestaffActionItem] performs level validation, conversion, and experience awards. This script does
 * not charge orbs or assume that missing supplies can be purchased; missing startup inputs are added to the
 * wanted-item list before stopping.
 *
 * Three consecutive interactions without input consumption end the session. Unresolved banking requests
 * are also bounded; failed or timed-out withdrawals stop through the inherited banking hook. Snapshots retain
 * the recipe, session configuration, and retry counters, while live actions and inventory slots are rebuilt.
 *
 * @param bot The bot running this script.
 * @property battlestaff The elemental recipe to assemble throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class MakeBattlestaffBotScript(
    bot: Bot,
    val battlestaff: Battlestaff,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved recipe and retry counters alongside the inherited duration and candidate zones.
         * Counters survive resumption so restoring a script does not reset an exhausted retry budget.
         *
         * @author lare96
         */
        class BattlestaffData : InventoryScriptData() {
            /** [Battlestaff] enum name used to reconstruct the configured recipe. */
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

    /** Permanent Crafting level required for selection and startup validation. */
    val requiredLevel = battlestaff.level
    /** One battlestaff and one matching charged orb, the minimum supplies for a single conversion. */
    private val materials = listOf(Item(Battlestaff.BATTLESTAFF), battlestaff.orbItem)

    /**
     * Restores the recipe, session configuration, and retry budgets from a snapshot.
     * Current supplies and bot safety are checked again by the normal initialization hooks.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized battlestaff-assembly state.
     */
    constructor(bot: Bot, data: BattlestaffData) :
        this(bot, Battlestaff.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Checks permanent level eligibility and one complete input pair across inventory and bank.
     * Temporary current-level, combat, lock, and strong-action restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced input pairs from bank stock, or an empty batch if either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses a battlestaff on its charged orb, waits for the dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(Battlestaff.BATTLESTAFF).onItem(battlestaff.orb)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeBattlestaffActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): BattlestaffData = BattlestaffData().also {
        it.recipe = battlestaff.name
        saveInventoryState(it)
    }
}
