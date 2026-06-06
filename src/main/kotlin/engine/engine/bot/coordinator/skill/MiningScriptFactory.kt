package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.MineBotScript
import game.skill.mining.Ore
import io.luna.game.model.mob.bot.Bot
import java.util.*

/**
 * Creates mining scripts based on the bot's level, personality, and current objective.
 *
 * Training scripts focus on experience and progression, while profit scripts favor ores that are more likely to
 * produce useful or valuable resources. The selected ores and zones are adjusted according to the bot's Mining level
 * so the bot does not attempt to mine ores it cannot access.
 *
 * Personality affects the training path:
 * - Dextrous bots prefer efficient iron mining.
 * - Intelligent bots prefer stronger mining locations.
 * - Other bots mine a broader set of available ores.
 * - Low-level bots start with basic ores in starter mining zones.
 *
 * @author lare96
 */
object MiningScriptFactory : SkillingScriptFactory(SKILL_MINING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val ores = EnumSet.noneOf(Ore::class.java)

        if (level > 15) {
            if (bot.personality.isDextrous) {
                ores += Ore.IRON

                zones += SubZone.SOUTH_EAST_ARDOUGNE_MINE
                zones += SubZone.LEGENDS_GUILD_MINE
            } else if (bot.personality.isIntelligent) {
                ores += Ore.IRON

                zones += SubZone.AL_KHARID_MINE
                zones += SubZone.LEGENDS_GUILD_MINE
            } else {
                ores.addAll(Ore.ORE_MAP.keys())

                if (!bot.personality.isDumb) {
                    ores.removeIf { it.level < 15 }
                }

                zones += SubZone.AL_KHARID_MINE
                zones += SubZone.VARROCK_SE_MINE
                zones += SubZone.VARROCK_SW_MINE
                zones += SubZone.LEGENDS_GUILD_MINE
                zones += SubZone.SOUTH_EAST_ARDOUGNE_MINE
            }
        } else {
            ores += Ore.COPPER
            ores += Ore.TIN
            ores += Ore.CLAY

            zones += SubZone.VARROCK_SW_MINE
            zones += SubZone.VARROCK_SE_MINE
        }

        ores.removeIf { level < it.level }
        require(ores.isNotEmpty()) { "No tree types were selected from MiningScriptFactory." }
        return MineBotScript(bot, ores, getDuration(bot), zones)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val ores = EnumSet.noneOf(Ore::class.java)

        if (level < Ore.GOLD.level) {
            ores += Ore.RUNE_ESSENCE
            zones += SubZone.ESSENCE_MINE
        } else if (level < Ore.MITHRIL.level) {
            if (rand(bot.personality.intelligence)) {
                ores += Ore.GOLD
            } else {
                ores += Ore.COAL
                ores += Ore.RUNE_ESSENCE
                zones += SubZone.ESSENCE_MINE
            }

            zones += SubZone.AL_KHARID_MINE
        } else if (level < Ore.ADAMANT.level) {
            if (rand(bot.personality.intelligence)) {
                ores += Ore.COAL
                ores += Ore.MITHRIL
            } else {
                ores += Ore.GOLD
                ores += Ore.SILVER
                ores += Ore.COAL
                zones += SubZone.AL_KHARID_MINE
            }

            zones += SubZone.SOUTH_LUMBRIDGE_MINE
        } else {
            // TODO@0.5.0 Mining guild.
            // TODO Rune rocks in wilderness if feeling greedy.
            //  Need to modify script superclass to logout/run automatically if a PKer is spotted.
            //  Based on personality and emotions.
            ores += Ore.MITHRIL
            ores += Ore.ADAMANT
            ores += Ore.RUNE

            zones += SubZone.AL_KHARID_MINE
            zones += SubZone.SOUTH_LUMBRIDGE_MINE
            zones += SubZone.EDGEVILLE_DUNGEON_MINE
        }

        ores.removeIf { level < it.level }
        require(ores.isNotEmpty()) { "No tree types were selected from MiningScriptFactory." }
        return MineBotScript(bot, ores, getDuration(bot), zones)
    }
}