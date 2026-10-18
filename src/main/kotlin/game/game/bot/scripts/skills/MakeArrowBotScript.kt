package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.attachArrow.MakeArrowActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Produces headless arrows from shafts and feathers, or finished arrows from headless arrows and tips.
 * One matching input pair qualifies. Bank withdrawals balance owned stacks up to [WITHDRAW_AMOUNT] of
 * each input, including partial stock. Non-stackable definitions use a capacity-safe balanced withdrawal.
 *
 * Production uses the native item interaction and dialogue. Its quantity counts sets of up to fifteen,
 * so the requested set count includes a final partial set. [MakeArrowActionItem] consumes the inputs and
 * awards the configured experience. The shared inventory lifecycle provides verified banking, safety,
 * travel, expiry, realistic missing-supply targets, and bounded retries retained in snapshots.
 *
 * @param bot The bot making arrows.
 * @property arrow The configured headless or finished arrow recipe.
 * @param duration The production session duration.
 * @param zones Existing processing zones with banking and travel support.
 * @author lare96
 */
class MakeArrowBotScript(
    bot: Bot,
    val arrow: Arrow,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Maximum units of each stackable input withdrawn per bank visit. */
        const val WITHDRAW_AMOUNT = 1_000

        /** Saved arrow recipe and inherited session/retry state. @author lare96 */
        class MakeArrowData : InventoryScriptData() {
            /** Arrow enum retained under its existing snapshot field name. */
            var arrow: Arrow = Arrow.HEADLESS_ARROW

            override fun load(data: JsonObject) {
                super.load(data)
                arrow = Arrow.valueOf(data.get("arrow").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("arrow", arrow.name)
            }
        }
    }

    /** Permanent Fletching level required for selection and startup. */
    val requiredLevel = arrow.level
    /** Minimum paired consumables required for a partial native production set. */
    private val materials = listOf(Item(arrow.tip), Item(arrow.with))

    /** Restores the existing arrow field, saved zones, duration, and shared retry budgets. */
    constructor(bot: Bot, data: MakeArrowData) : this(bot, data.arrow, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level and one complete owned pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns balanced partial stacks, with a capacity-safe fallback for non-stackable definitions. */
    fun bankBatch(): List<Item> {
        if (!bot.inventory.stackable(arrow.id) || materials.any { !bot.inventory.stackable(it.id) }) {
            return bot.productionBatch(materials)
        }
        val amount = minOf(WITHDRAW_AMOUNT, materials.minOf { bot.bank.computeAmountForId(it.id) })
        return if (amount > 0) materials.map { Item(it.id, amount) } else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Checks paired inputs and the inventory space released by the next full or partial native set. */
    private fun hasMaterials(): Boolean {
        if (!bot.inventory.containsAll(materials)) return false
        val amount = minOf(Arrow.SET_AMOUNT, materials.minOf { bot.inventory.computeAmountForId(it.id) })
        val freedSlots = materials.sumOf {
            if (!bot.inventory.stackable(it.id)) amount
            else if (bot.inventory.computeAmountForId(it.id) == amount) 1 else 0
        }
        val outputSlots = if (!bot.inventory.stackable(arrow.id)) amount
                          else if (bot.inventory.contains(arrow.id)) 0 else 1
        return bot.inventory.computeRemainingSize() + freedSlots >= outputSlots
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        attemptProduction(arrow.tip) { startProduction() }
        return true
    }

    /** Uses the registered interaction direction and requests enough native sets for the balanced inputs. */
    private suspend fun startProduction(): Boolean {
        val usedId = if (arrow == Arrow.HEADLESS_ARROW) arrow.tip else arrow.with
        val targetId = if (arrow == Arrow.HEADLESS_ARROW) arrow.with else arrow.tip
        if (!handler.inventory.useItem(usedId).onItem(targetId)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) }
        val sets = (amount - 1) / Arrow.SET_AMOUNT + 1
        handler.widgets.clickMakeItem(0, sets)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeArrowActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): MakeArrowData = MakeArrowData().also {
        it.arrow = arrow
        saveInventoryState(it)
    }
}
