package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.VoidBotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.FiremakingBotScript
import game.bot.scripts.skills.FiremakingBotScript.FiremakingSpot
import game.skill.firemaking.Log
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Creates Firemaking scripts for bots.
 *
 * Bots burn owned or purchased logs at a [FiremakingSpot]. Normal zoned travel preferences choose the
 * destination; occupied spots are discovered and rejected only after arrival.
 *
 * Log selection is personality-based through [getBestActivity]: dextrous and intelligent bots burn their best logs,
 * while other bots may pick worse ones.
 *
 * Partial inventories are eligible, so the last owned logs can be burned.
 *
 * @author lare96
 * @author TheLining
 */
object FiremakingScriptFactory : SkillingScriptFactory(SKILL_FIREMAKING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return getTrainingScript(bot, level, getDuration(bot, skillId))
    }

    /**
     * Continues an existing Firemaking session within the time it has left.
     */
    fun getTrainingScript(bot: Bot, level: Int, duration: Duration): BotScript {
        return BotProductionPlanner.select(bot, skillId, level, true, duration)
            ?: BotProductionPlanner.collectTrainingLogs(bot, skillId, level, duration)
            ?: VoidBotScript(bot)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript = VoidBotScript(bot)

    /**
     * Creates a script that burns logs [bot] already owns, with alternative spots for after-arrival competition.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current Firemaking level.
     * @param zones The mutable zone list to populate with firemaking spots.
     * @return The script, or `null` if the training goal is complete or the bot doesn't have any logs it can burn.
     */
    fun getBurnScript(bot: Bot,
                      level: Int = bot.firemaking.staticLevel,
                      zones: MutableList<SubZone> = ArrayList(),
                      duration: Duration = getDuration(bot, skillId)): FiremakingBotScript? {
        if (duration <= Duration.ZERO || !BotCombatActionHandler.wantsToTrain(bot, skillId)) return null
        val owned = Log.entries.filter { bot.itemTracker.count(it.id) > 0 }
        val log = getBestActivity(bot, level, { it.level }, owned) ?: return null
        zones.clear()
        FiremakingSpot.entries.mapTo(zones) { it.zone }
        return FiremakingBotScript(bot, log, duration, zones)
    }
}
