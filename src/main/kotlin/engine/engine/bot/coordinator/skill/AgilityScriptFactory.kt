package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.AgilityBotCourse
import game.bot.scripts.skills.AgilityBotScript
import game.bot.scripts.skills.SkullballBotScript
import game.bot.scripts.skills.RoguesDenBotScript
import game.skill.agility.skullball.Skullball
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.minutes

/**
 * Selects supported native Agility courses through the normal skilling coordinator.
 *
 * Obstacle courses, Yanille room crossings, Skullball and Rogues' Den use the same level/personality selection.
 * Owned admission equipment and survivable fall damage are checked before selection. Profit visits consider
 * the Pyramid's ordinary artefact sale and the maze's native safe rewards.
 *
 * @author lare96
 */
object AgilityScriptFactory : SkillingScriptFactory(SKILL_AGILITY) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val duration = getDuration(bot)
        val options = ArrayList<BotSkillingActivity<Pair<Int, BotScript>>>()
        AgilityBotCourse.entries.filter { AgilityBotScript.canTrain(bot, it) }.forEach {
            options += BotSkillingActivity(setOf(it.minimumLevel to AgilityBotScript(bot, it, duration)), setOf(it.zone))
        }
        if (SkullballBotScript.eligible(bot)) options += BotSkillingActivity(
            setOf(Skullball.LEVEL to SkullballBotScript(bot, duration)), setOf(SubZone.WEREWOLF_SKULLBALL))
        if (RoguesDenBotScript.eligible(bot)) options += BotSkillingActivity(
            setOf(50 to RoguesDenBotScript(bot, duration.coerceAtLeast(20.minutes))), setOf(SubZone.ROGUES_DEN_MAZE))
        val activity = getBestSkillingActivity(bot, level, { it.first }, options)
            ?: return object : DynamicBotScript(bot) {
                override suspend fun run() = true
            }
        zones.addAll(activity.zones)
        return activity.tokens.single().second
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        if (RoguesDenBotScript.eligible(bot) && rand(0.25)) {
            zones += SubZone.ROGUES_DEN_MAZE
            return RoguesDenBotScript(bot, getDuration(bot).coerceAtLeast(20.minutes))
        }
        val pyramid = AgilityBotCourse.AGILITY_PYRAMID
        if (level >= pyramid.course.level && AgilityBotScript.canTrain(bot, pyramid)) {
            zones += pyramid.zone
            return AgilityBotScript(bot, pyramid, getDuration(bot))
        }
        return object : DynamicBotScript(bot) {
            override suspend fun run() = true
        }
    }
}
