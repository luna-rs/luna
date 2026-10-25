package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CraftRuneBotScript
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import game.player.item.consume.food.Food
import engine.bot.gear.BotItemTracker.Companion.itemTracker

/**
 * Creates Runecrafting scripts for skilling bots.
 *
 * Training currently selects the highest-level altar the bot can use, then creates a [CraftRuneBotScript] for that
 * altar. Healthy bots with owned healing supplies may choose the Abyss shortcut, weighted by confidence; its script
 * validates the packed death-loss budget before departure. Other trips retain ordinary talisman entry. Profit
 * behaviour currently falls back to the same training logic until rune margins are implemented.
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
        val abyss = bot.health >= 30 && Food.values().any { it.heal >= 8 && bot.itemTracker.count(it.id) >= 4 } &&
            rand(0.25 + bot.personality.confidence.coerceIn(0.0, 1.0) * 0.5)
        return CraftRuneBotScript(bot, activity.tokens.single(), getDuration(bot), viaAbyss = abyss)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        // TODO Determine which rune is worth the most based on margin and demand.
        return getTrainingScript(bot, level, zones)
    }
}
