package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.MineBotScript
import game.content.miningGuild.MiningGuild
import game.skill.mining.Ore
import game.skill.mining.Ore.*
import io.luna.game.model.mob.bot.Bot

/**
 * Mining methods keep each mine's rocks together with its access and personality preferences.
 * Training retains iron-focused efficient personalities and broader casual routes. Profit progresses
 * from essence/basic ores to gold, coal, and high-tier metals. The guild is preferred once accessible.
 *
 * @author lare96
 */
object MiningScriptFactory : SkillingScriptFactory(SKILL_MINING) {
    /**
     * Supported rock types from the subzone definitions; essence is a separate mining method.
     */
    internal val rocks = linkedMapOf(
        SubZone.VARROCK_SW_MINE to setOf(CLAY, TIN, IRON, SILVER),
        SubZone.VARROCK_SE_MINE to setOf(COPPER, TIN, IRON),
        SubZone.FALADOR_WEST_MINE to setOf(COPPER, TIN, IRON, COAL),
        SubZone.DWARVEN_MINE to setOf(CLAY, COPPER, TIN, IRON, COAL, GOLD, MITHRIL, ADAMANT),
        SubZone.MINING_GUILD to setOf(IRON, COAL, MITHRIL, ADAMANT, RUNE),
        SubZone.LEGENDS_GUILD_MINE to setOf(IRON, COAL),
        SubZone.SOUTH_EAST_ARDOUGNE_MINE to setOf(IRON, COAL),
        SubZone.AL_KHARID_MINE to setOf(COPPER, TIN, IRON, SILVER, COAL, GOLD, MITHRIL, ADAMANT),
        SubZone.SOUTH_LUMBRIDGE_MINE to setOf(COAL, MITHRIL, ADAMANT),
        SubZone.EDGEVILLE_DUNGEON_MINE to setOf(COPPER, TIN, IRON, SILVER, COAL, MITHRIL, ADAMANT),
        SubZone.ESSENCE_MINE to setOf(RUNE_ESSENCE)
    )

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, true)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, false)

    /**
     * Selects a complete mining activity with the existing personality rule.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): MineBotScript {
        val activity = getBestSkillingActivity(bot, level, { it.level }, activities(bot, level, training))
            ?: error("No mining activity is available at level $level.")
        zones.clear()
        zones.addAll(activity.zones)
        return MineBotScript(bot, activity.tokens, getDuration(bot), zones)
    }

    /**
     * Keeps ore and access decisions in the mining factory rather than adding a generic policy system.
     */
    internal fun activities(bot: Bot, level: Int, training: Boolean): List<BotSkillingActivity<Ore>> {
        val efficient = bot.personality.isDextrous || bot.personality.isIntelligent
        val desired = if (training) trainingOres(bot, level) else profitOres(bot, level)
        val activities = ArrayList<BotSkillingActivity<Ore>>()
        for ((zone, supported) in rocks) {
            if (zone == SubZone.MINING_GUILD &&
                (level < MiningGuild.REQUIRED_LEVEL || bot.mining.level < MiningGuild.REQUIRED_LEVEL)) {
                continue
            }
            if (training && zone == SubZone.AL_KHARID_MINE && bot.combatLevel <= 30) {
                continue
            }
            if (training && zone == SubZone.EDGEVILLE_DUNGEON_MINE) {
                continue
            }
            val tokens = linkedSetOf<Ore>()
            for (ore in desired) {
                if (ore in supported && ore.level <= level) {
                    tokens.add(ore)
                }
            }
            if (tokens.isNotEmpty()) {
                activities.add(BotSkillingActivity(tokens = tokens, zones = setOf(zone)))
            }
        }
        // A bot that just escaped an attacker in or near the guild mines elsewhere for a while.
        val preferGuild = (if (training) efficient else bot.personality.isIntelligent) &&
            bot.recentEscape?.avoids(SubZone.MINING_GUILD) != true
        if (preferGuild) {
            for (activity in activities) {
                if (SubZone.MINING_GUILD in activity.zones) {
                    return listOf(activity)
                }
            }
        }
        return activities
    }

    /**
     * Chooses training ores using the existing personality preferences.
     */
    private fun trainingOres(bot: Bot, level: Int): Set<Ore> {
        if (level < IRON.level) {
            return setOf(COPPER, TIN, CLAY)
        }
        if (bot.personality.isDextrous || bot.personality.isIntelligent) {
            val coalChance = if (bot.personality.isDextrous) 1.0 / 3.0 else 0.5
            if (rand(coalChance)) {
                return setOf(IRON, COAL)
            }
            return setOf(IRON)
        }
        if (bot.personality.isDumb) {
            return Ore.entries.toSet() - setOf(RUNE_ESSENCE, PURE_ESSENCE)
        }
        return setOf(IRON, COAL, GOLD, MITHRIL, ADAMANT, RUNE)
    }

    /**
     * Chooses profit ores using the existing level progression and intelligence rolls.
     */
    private fun profitOres(bot: Bot, level: Int): Set<Ore> {
        if (level < GOLD.level) {
            if (!rand(bot.personality.intelligence)) {
                return setOf(RUNE_ESSENCE)
            }
            if (level >= COAL.level) {
                return setOf(COAL)
            }
            if (level >= IRON.level) {
                return setOf(IRON)
            }
            return setOf(COPPER, TIN)
        }
        if (level < MITHRIL.level) {
            if (rand(bot.personality.intelligence)) {
                return setOf(GOLD)
            }
            return setOf(COAL, RUNE_ESSENCE)
        }
        if (level < ADAMANT.level) {
            if (rand(bot.personality.intelligence)) {
                return setOf(COAL, MITHRIL)
            }
            return setOf(GOLD, SILVER, COAL)
        }
        return setOf(MITHRIL, ADAMANT, RUNE)
    }
}
