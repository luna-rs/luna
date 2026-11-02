package engine.bot.coordinator.skill

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
 * Bots burn logs they already own, usually ones they chopped themselves, at the nearest [FiremakingSpot]s that no other
 * bot is using. Bots without eligible logs gather a bounded stock they can burn, then resume Firemaking within the
 * same session budget. Occupied burning spots do not cause bots with usable fuel to gather more. Burning logs
 * never makes money, so profit scripts always chop.
 *
 * Log selection is personality-based through [getBestActivity]: dextrous and intelligent bots burn their best logs,
 * while other bots may pick worse ones.
 *
 * Partial inventories are eligible, so the last owned logs can be burned before gathering more.
 *
 * @author lare96
 * @author TheLining
 */
object FiremakingScriptFactory : SkillingScriptFactory(SKILL_FIREMAKING) {

    /**
     * How many of the nearest free spots a bot chooses between.
     */
    private const val SPOT_CHOICES = 2

    /**
     * Four burning inventories provide a useful batch without turning preparation into a full Woodcutting session.
     */
    const val GATHER_TARGET = FiremakingBotScript.LOGS_PER_TRIP * 4

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return getTrainingScript(bot, level, zones, getDuration(bot))
    }

    /**
     * Continues an existing Firemaking session without restarting its time budget or gathering unusable logs.
     */
    fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>, duration: Duration): BotScript {
        if (duration <= Duration.ZERO) return VoidBotScript(bot)
        getBurnScript(bot, level, zones, duration)?.let { return it }
        if (Log.entries.any { it.level <= level && bot.itemTracker.count(it.id) > 0 }) return VoidBotScript(bot)
        return WoodcuttingScriptFactory.getFiremakingScript(bot, level, duration, zones)
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
     * @return The script, or `null` if the bot doesn't have any logs it can burn, or every spot is
     * taken.
     */
    fun getBurnScript(bot: Bot,
                      level: Int = bot.firemaking.staticLevel,
                      zones: MutableList<SubZone> = ArrayList(),
                      duration: Duration = getDuration(bot)): FiremakingBotScript? {
        if (duration <= Duration.ZERO) return null
        val owned = Log.entries.filter { bot.itemTracker.count(it.id) > 0 }
        val log = getBestActivity(bot, level, { it.level }, owned) ?: return null
        val spots = FiremakingSpot.entries.filter { !it.isTaken() }
            .sortedBy { bot.position.computeLongestDistance(it.zone.inside) }
            .take(SPOT_CHOICES)
        if (spots.isEmpty()) {
            return null
        }
        spots.mapTo(zones) { it.zone }
        return FiremakingBotScript(bot, log, duration, zones)
    }
}
