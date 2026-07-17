package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CraftRuneBotScript
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar

/**
 * Creates Runecrafting scripts for skilling bots.
 *
 * Training currently selects the highest-level altar the bot can use, then creates a [CraftRuneBotScript] for that
 * altar. Profit behaviour currently falls back to the same training logic until rune margins are implemented.
 *
 * @author lare96
 */
object RunecraftingScriptFactory : SkillingScriptFactory(SKILL_RUNECRAFTING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        var (highestLevel, highestAltar) = Pair(1, Altar.AIR)
        for (altar in Altar.entries) {
            val rune = CraftableRune.ALTAR_TO_RUNE[altar]
            if (rune != null && level >= rune.level && rune.level > highestLevel) {
                highestLevel = rune.level
                highestAltar = altar
            }
        }
        return CraftRuneBotScript(bot, highestAltar, getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        // TODO Determine which rune is worth the most based on margin and demand.
        return getTrainingScript(bot, level, zones)
    }
}