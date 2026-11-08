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
import game.skill.herblore.makePotion.MakePotionActionItem
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Mixes one finished-potion recipe from owned unfinished potions and secondary ingredients.
 *
 * Uses the normal item-on-item interaction and make dialogue. [MakePotionActionItem] owns ingredient
 * consumption, current-level validation, animation, and XP awards. All recipes come from [FinishedPotion],
 * including preparation oil; this script adds no quest requirements or recipe rules absent from that handler.
 * The Herblore factory can select eligible recipes in training or profit mode using its usual personality rules.
 * Selecting profit mode does not imply a market margin. Owned unfinished potions permit selection even when
 * the secondary is missing; bounded startup acquisition purchases only missing secondary ingredients.
 *
 * [InventoryBotScript] handles travel to existing processing zones, banking, duration, and weak-action gating.
 * Each bank visit withdraws up to fourteen balanced input pairs, limited by the smaller available stock.
 * Missing secondaries retain their normal stock targets. Death, locks, combat, strong
 * actions, and insufficient permanent level prevent startup; current level is checked before each interaction.
 *
 * Three failed interactions or unresolved banking requests stop production. Unnoted withdrawals are verified
 * and bounded to fifteen seconds. Snapshots preserve the recipe, duration, zones, and both retry budgets.
 * Finishing interrupts only the matching potion action. Successful stock exhaustion can queue one eligible
 * next production stage with the remaining budget; failed or paused work never starts a continuation.
 *
 * @param bot The bot mixing potions.
 * @property potion The existing finished-potion recipe used for this session.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Existing banked processing zones available to the script.
 * @author lare96
 */
class MakePotionBotScript(
    bot: Bot,
    val potion: FinishedPotion,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_HERBLORE)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session preserves its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class PotionData : InventoryScriptData() {
            /** [FinishedPotion] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            var produced = false
            var continued = false

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                produced = data.get("produced")?.asBoolean ?: false
                continued = data.get("continued")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("produced", produced)
                data.addProperty("continued", continued)
            }
        }
    }

    /** Permanent Herblore level required for selection and startup validation. */
    val requiredLevel = potion.level
    /** One unfinished potion and one secondary ingredient, the minimum supplies for a single conversion. */
    private val materials = listOf(potion.unfItem, potion.secondaryItem)
    private var produced = false
    private var continued = false

    /**
     * Restores the recipe, session configuration, and retry budgets from saved state.
     * Initialization still checks current supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized finished-potion state.
     */
    constructor(bot: Bot, data: PotionData) :
        this(bot, FinishedPotion.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
        produced = data.produced
        continued = data.continued
    }

    /**
     * Checks permanent level and one owned unfinished potion; missing secondary is acquired during startup.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(listOf(potion.unfItem))

    override suspend fun beforeInventoryInit(): Boolean {
        if (produced && HerbloreProduction.owned(bot, potion.unf) < 1) {
            queueContinuation()
            stop()
            return false
        }
        return isEligible() && HerbloreProduction.prepare(bot, potion.unf, potion.secondary)
    }

    /** Returns up to fourteen balanced input pairs, or an empty batch when either banked ingredient is missing. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) {
            queueContinuation()
            stop()
        }
    }

    /**
     * Continues verified production once, retaining the remaining budget and rejecting interrupted work.
     */
    private fun queueContinuation() {
        if (!produced || continued || isPaused() || isTerminated() || productionRetriesExhausted() || !isActionSafe()) return
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return
        continued = true
        HerbloreProduction.afterFinished(bot, potion, remaining)?.let { bot.scriptStack.softPushHead(it) }
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory currently holds a complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_HERBLORE).level >= requiredLevel)) return true
        val inputBefore = bot.inventory.computeAmountForId(potion.unf)
        val outputBefore = bot.inventory.computeAmountForId(potion.id)
        try {
            attemptProduction(materials.first().id) { startProduction() }
        } finally {
            if (bot.inventory.computeAmountForId(potion.unf) < inputBefore ||
                bot.inventory.computeAmountForId(potion.id) > outputBefore) produced = true
        }
        return true
    }

    /** Uses the unfinished potion on its secondary ingredient, waits for the single-recipe dialogue, and requests the batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(potion.unf).onItem(potion.secondary)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakePotionActionItem::class.java)?.takeIf { it.potion == potion }?.interrupt()
    }

    override fun snapshot(): PotionData = PotionData().also {
        it.recipe = potion.name
        it.produced = produced
        it.continued = continued
        saveInventoryState(it)
    }
}
