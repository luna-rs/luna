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
import game.skill.herblore.makePotion.MakePotionActionItem
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mixes one finished-potion recipe from owned unfinished potions and secondary ingredients.
 *
 * Uses the normal item-on-item interaction and make dialogue. [MakePotionActionItem] owns ingredient
 * consumption, current-level validation, animation, and XP awards. All recipes come from [FinishedPotion],
 * including preparation oil; this script adds no quest requirements or recipe rules absent from that handler.
 * The Herblore factory can select eligible recipes in training or profit mode using its usual personality rules.
 * Selecting profit mode does not imply a market margin, and the script does not purchase or manufacture supplies.
 *
 * [InventoryBotScript] handles travel to existing processing zones, banking, duration, and weak-action gating.
 * Each bank visit withdraws up to fourteen balanced input pairs, limited by the smaller available stock.
 * Missing startup inputs request total stock targets of 1,000 and end the session. Death, locks, combat, strong
 * actions, and insufficient permanent level prevent startup; current level is checked before each interaction.
 *
 * Three failed interactions or unresolved banking requests stop production. Unnoted withdrawals are verified
 * and bounded to fifteen seconds. Snapshots preserve the recipe, duration, zones, and both retry budgets.
 * Finishing interrupts only the matching potion action so the coordinator can choose another activity.
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
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Saved recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session preserves its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class PotionData : ZonedBotScriptData() {
            /** [FinishedPotion] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Consecutive interactions that did not consume an unfinished potion. */
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

    /** Permanent Herblore level required for selection and startup validation. */
    val requiredLevel = potion.level
    /** One unfinished potion and one secondary ingredient, the minimum supplies for a single conversion. */
    private val materials = listOf(potion.unfItem, potion.secondaryItem)
    /** Consecutive failed production interactions, reset after an unfinished potion is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the recipe, session configuration, and retry budgets from saved state.
     * Initialization still checks current supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized finished-potion state.
     */
    constructor(bot: Bot, data: PotionData) :
        this(bot, FinishedPotion.valueOf(data.recipe), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Checks permanent level eligibility and one complete input pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced input pairs, or an empty batch when either banked ingredient is missing. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_HERBLORE).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
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

    /** Whether inventory currently holds a complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

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
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
