package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.BuryBoneBotScript
import game.bot.scripts.skills.EctofuntusBotScript
import game.skill.prayer.Bone
import io.luna.game.model.mob.bot.Bot

/**
 * Selects Ectofuntus worship or banked bone burial using owned supplies and existing personality rules.
 *
 * All bone recipes are level-independent. Candidates are ordered by experience per bone, so intelligent
 * and dextrous bots prefer Ectofuntus when a complete owned batch is available, then the strongest burial recipe.
 * Other personalities retain normal burial selection. Failed worship trips defer to burial during their cooldown.
 * An empty training selection records demand for 1,000 ordinary bones and completes immediately.
 * Prayer is registered alongside the other skill factories; its profit entry completes without consuming stock.
 *
 * @author lare96
 */
object PrayerScriptFactory : SkillingScriptFactory(SKILL_PRAYER) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        getProductionScript(bot) ?: object : DynamicBotScript(bot) {
            override suspend fun run(): Boolean {
                bot.preferences.raiseWantedItemTarget(Bone.BONES.id, 1_000)
                return true
            }
        }

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
        val candidates = Bone.entries.sortedByDescending { it.exp }
            .map { BuryBoneBotScript(bot, it, duration) }.filter { it.isEligible() }
        return getBestActivity(bot, 1, { 1 }, candidates)
    }
}
