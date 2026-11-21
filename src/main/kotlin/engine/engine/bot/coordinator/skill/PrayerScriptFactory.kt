package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.VoidBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.BuryBoneBotScript
import game.bot.scripts.skills.EctofuntusBotScript
import game.skill.prayer.Bone
import io.luna.game.model.mob.bot.Bot

/**
 * Chooses a finishable Ectofuntus batch when it saves time or bone stock for the XP it earns. Otherwise the bot
 * buries an owned batch or uses the ordinary budgeted production planner to acquire bones when it has none.
 * Prayer's profit entry completes without consuming stock.
 *
 * @author lare96
 */
object PrayerScriptFactory : SkillingScriptFactory(SKILL_PRAYER) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        getProductionScript(bot) ?: VoidBotScript(bot)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        object : DynamicBotScript(bot) {
            override suspend fun run() = true
        }

    /**
     * Selects one owned production activity without creating materials or awarding experience.
     */
    internal fun getProductionScript(bot: Bot): BotScript? {
        val duration = getDuration(bot, skillId)
        EctofuntusBotScript.select(bot, duration)?.let { return it }
        if (!BotCombatActionHandler.wantsToTrain(bot, skillId)) return null
        val owned = Bone.entries.maxByOrNull { bone ->
            val total = bot.inventory.computeAmountForId(bone.id).toLong() + bot.bank.computeAmountForId(bone.id)
            total.coerceAtMost(bot.inventory.capacity().toLong()) * bone.exp
        }
        if (owned != null && (bot.inventory.contains(owned.id) || bot.bank.contains(owned.id)))
            return BuryBoneBotScript(bot, owned, duration, ownedOuting = true)
        return BotProductionPlanner.select(bot, skillId, bot.prayer.staticLevel, true, duration)
    }
}
