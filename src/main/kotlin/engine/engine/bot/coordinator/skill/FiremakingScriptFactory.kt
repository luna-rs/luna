package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.FiremakingBotScript
import game.bot.scripts.skills.FiremakingBotScript.Companion.LOGS_PER_TRIP
import game.bot.scripts.skills.FiremakingBotScript.FiremakingSpot
import game.skill.firemaking.Log
import io.luna.game.model.mob.bot.Bot

/**
 * Creates Firemaking scripts for bots.
 *
 * Bots burn logs they already own, usually ones they chopped themselves, at the nearest [FiremakingSpot]s that no other
 * bot is using. Bots without a full inventory of logs, or that find every spot taken, chop logs instead. Burning logs
 * never makes money, so profit scripts always chop.
 *
 * Log selection is personality-based through [getBestActivity]: dextrous and intelligent bots burn their best logs,
 * while other bots may pick worse ones.
 *
 * @author TheLining
 */
object FiremakingScriptFactory : SkillingScriptFactory(SKILL_FIREMAKING) {

    /**
     * How many of the nearest free spots a bot chooses between.
     */
    private const val SPOT_CHOICES = 2

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return getBurnScript(bot, level, zones)
            ?: WoodcuttingScriptFactory.getTrainingScript(bot, bot.woodcutting.staticLevel, zones)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return WoodcuttingScriptFactory.getProfitScript(bot, bot.woodcutting.staticLevel, zones)
    }

    /**
     * Creates a script that burns logs [bot] already owns at the nearest free spots.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current Firemaking level.
     * @param zones The mutable zone list to populate with firemaking spots.
     * @return The script, or `null` if the bot doesn't have a full inventory of logs it can burn, or every spot is
     * taken.
     */
    fun getBurnScript(bot: Bot,
                      level: Int = bot.firemaking.staticLevel,
                      zones: MutableList<SubZone> = ArrayList()): FiremakingBotScript? {
        val owned = Log.entries.filter { bot.itemTracker.count(it.id) >= LOGS_PER_TRIP }
        val log = getBestActivity(bot, level, { it.level }, owned) ?: return null
        val spots = FiremakingSpot.entries.filter { !it.isTaken() }
            .sortedBy { bot.position.computeLongestDistance(it.zone.inside) }
            .take(SPOT_CHOICES)
        if (spots.isEmpty()) {
            return null
        }
        spots.mapTo(zones) { it.zone }
        return FiremakingBotScript(bot, log, getDuration(bot), zones)
    }
}
