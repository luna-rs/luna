package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.VoidBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.EctofuntusBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * Selects existing Ectofuntus work or a finite, economically budgeted bone-burial goal.
 *
 * All bone recipes are level-independent. Ectofuntus is available to every personality and prepares missing empty
 * containers through native shop purchases before processing an owned batch. Failed worship trips defer to burial
 * during their cooldown. Burial selection retains the ordinary personality policy.
 * Burial can acquire a small affordable batch before any bones are owned.
 * Prayer is registered alongside the other skill factories; its profit entry completes without consuming stock.
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
        val duration = getDuration(bot)
        EctofuntusBotScript.select(bot, duration)?.let { return it }
        return BotProductionPlanner.select(bot, skillId, bot.prayer.staticLevel, true, duration)
    }
}
