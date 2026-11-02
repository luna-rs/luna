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
import engine.bot.coordinator.RuneRunningCoordinator

/**
 * Creates Runecrafting scripts for skilling bots.
 *
 * Funded skiller accounts may host essence runners at an accessible altar. Other training selects the highest-level
 * altar the bot can use, then creates a [CraftRuneBotScript]. Healthy bots with owned healing supplies may choose the
 * Abyss shortcut, weighted by confidence; its script
 * validates the packed death-loss budget before departure. Other trips retain ordinary talisman entry. Profit
 * behaviour uses ordinary rune production until margins are implemented; hosting runners spends wealth for XP.
 *
 * @author lare96
 */
object RunecraftingScriptFactory : SkillingScriptFactory(SKILL_RUNECRAFTING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        RuneRunningCoordinator.crafterScript(bot, getDuration(bot))?.let { return it }
        return ordinaryScript(bot, level)
    }

    /**
     * Keeps ordinary rune production available independently of wealth spent hosting runners for experience.
     */
    private fun ordinaryScript(bot: Bot, level: Int): BotScript {
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
        return ordinaryScript(bot, level)
    }
}
