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
        val activities = Altar.entries.filter { it in CraftableRune.ALTAR_TO_RUNE }.map { altar ->
            BotSkillingActivity(tokens = setOf(altar), zones = setOf(altar.zone))
        }
        val activity = getSkillingActivities(level, { CraftableRune.ALTAR_TO_RUNE.getValue(it).level }, activities)
            .firstOrNull() ?: BotSkillingActivity(tokens = setOf(Altar.AIR), zones = setOf(Altar.AIR.zone))
        return CraftRuneBotScript(bot, activity.tokens.single(), getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        // TODO Determine which rune is worth the most based on margin and demand.
        return getTrainingScript(bot, level, zones)
    }
}
