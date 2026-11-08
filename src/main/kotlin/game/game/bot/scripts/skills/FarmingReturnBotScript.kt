package game.bot.scripts.skills

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.isRealBot
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.SKILL_FARMING
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FarmingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.farming.Farming
import game.skill.farming.Farming.farmingPatchData
import game.skill.farming.seedling.TreeSeedling
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Checks pending native farming work once, then yields the stack while crops and compost grow.
 * Farm visits append one deduplicated checker. A saved idle-boundary reminder rearms it after a bounded interval,
 * so a growing patch never leaves a sleeping script at the head or relies on another random Farming selection.
 * Native farming state remains authoritative; the checker only queues an ordinary supplied visit.
 *
 * @author lare96
 */
class FarmingReturnBotScript(bot: Bot, private var profit: Boolean = false) : BotScript(bot) {
    override val actionSafety = ActionSafety.COMBAT

    companion object {
        internal const val CHECK_INTERVAL_MILLIS = 60_000L
        private var Bot.returnEnabled by Attr.boolean().persist("bot_farming_return_enabled")
        private var Bot.returnProfit by Attr.boolean { true }.persist("bot_farming_return_profit")
        private var Bot.nextReturnCheck by Attr.long().persist("bot_farming_return_check_at")
        private var Bot.yieldAfterReturn by Attr.boolean().persist("bot_farming_return_yield")

        /**
         * Saves the policy of an already queued check; crop state and future reminders live in native/player data.
         *
         * @author lare96
         */
        class ReturnData : BotScriptData() {
            var profit = false
            override fun load(data: JsonObject) {
                profit = data.get("profit")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                data.addProperty("profit", profit)
            }
        }

        private fun permitted(bot: Bot, profit: Boolean): Boolean = bot.isRealBot() &&
            bot.state == EntityState.ACTIVE && BotCombatActionHandler.mayGainExperience(bot, SKILL_FARMING) &&
            (profit || BotCombatActionHandler.wantsToTrain(bot, SKILL_FARMING)) &&
            bot.preferences.canChooseActivity(if (profit) BotActivity.PROFIT_SKILLS else BotActivity.TRAINING_SKILLS)

        private fun queuedVisit(bot: Bot): Boolean = bot.scriptStack.scripts.any {
            !it.isFinished() && !it.isTerminated() &&
                (it is FarmingBotScript || it is MakeCompostBotScript || it is PrepareSaplingBotScript)
        }

        /**
         * Appends one fast checker before a farming session can stop or be interrupted. Repeated visits share it.
         */
        @JvmStatic
        fun arm(bot: Bot, profit: Boolean) {
            if (!permitted(bot, profit)) return
            bot.returnEnabled = true
            bot.returnProfit = profit
            val existing = bot.scriptStack.scripts.filterIsInstance<FarmingReturnBotScript>()
                .firstOrNull { !it.isFinished() && !it.isTerminated() }
            if (existing != null) {
                existing.profit = profit
            } else {
                bot.scriptStack.pushTail(FarmingReturnBotScript(bot, profit))
            }
        }

        /**
         * Uses existing saved crop/bin records and owned intermediates as a cheap legacy bootstrap. It does not
         * instantiate every patch on every ordinary activity decision.
         */
        private fun pendingHint(bot: Bot): Boolean = bot.farmingPatchData.entrySet().any { (_, value) ->
            value.isJsonObject && value.asJsonObject.let { record ->
                record.get("crop")?.let { !it.isJsonNull } == true ||
                    record.get("scarecrow")?.asBoolean == true ||
                    (record.get("amount")?.asInt ?: 0) > 0
            }
        } || TreeSeedling.entries.any {
            listOf(it.seedling, it.wateredSeedling, it.sapling).any { id ->
                bot.itemTracker.count(id) > 0
            }
        }

        /**
         * Offers a one-shot return check only at a normal idle activity boundary. Future work is a timestamp,
         * leaving an empty stack available for the brain's usual activity selection while the crop grows.
         */
        @JvmStatic
        @JvmOverloads
        fun poll(bot: Bot, now: Long = System.currentTimeMillis()): FarmingReturnBotScript? {
            var profit = bot.returnProfit
            if (!bot.returnEnabled && !permitted(bot, profit) && permitted(bot, !profit)) profit = !profit
            if (!permitted(bot, profit) || !ActionSafety.NON_COMBAT.allows(bot) || now < bot.nextReturnCheck ||
                queuedVisit(bot) ||
                bot.scriptStack.scripts.any { it is FarmingReturnBotScript && !it.isFinished() && !it.isTerminated() }) return null
            if (bot.yieldAfterReturn) {
                bot.yieldAfterReturn = false
                bot.nextReturnCheck = now + CHECK_INTERVAL_MILLIS
                return null
            }
            if (!bot.returnEnabled && !pendingHint(bot)) return null
            bot.returnEnabled = true
            bot.returnProfit = profit
            return FarmingReturnBotScript(bot, profit)
        }
    }

    constructor(bot: Bot, data: ReturnData) : this(bot, data.profit)

    override suspend fun run(): Boolean {
        bot.returnProfit = profit
        val now = System.currentTimeMillis()
        if (bot.yieldAfterReturn) {
            bot.yieldAfterReturn = false
            bot.nextReturnCheck = now + CHECK_INTERVAL_MILLIS
            return true
        }
        if (now < bot.nextReturnCheck) return true
        bot.nextReturnCheck = now + CHECK_INTERVAL_MILLIS
        if (!permitted(bot, profit) || !ActionSafety.NON_COMBAT.allows(bot)) return true
        if (queuedVisit(bot)) return true
        val script = FarmingScriptFactory.getReturnScript(bot, profit)
        bot.returnEnabled = Farming.patches(bot).values.any { it.isOccupied() } ||
            Farming.compostBins(bot).values.any { it.amount > 0 } || pendingHint(bot)
        if (script != null) {
            bot.yieldAfterReturn = true
            bot.scriptStack.softPushHead(script)
        }
        return true
    }

    override fun snapshot() = ReturnData().also { it.profit = profit }
}
