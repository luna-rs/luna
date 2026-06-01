package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.TanHideBotScript
import game.skill.smithing.BarType
import io.luna.game.model.mob.bot.Bot

/**
 * Creates smithing scripts for bots.
 *
 * Smithing currently focuses on smelting bars. Training selects a level-appropriate [BarType], while profit mode either
 * attempts a temporary placeholder activity or smelts any bar type the bot has ore for.
 *
 * @author lare96
 */
object SmithingScriptFactory : SkillingScriptFactory(SKILL_CRAFTING) {

    /**
     * Creates a smithing training script for the bot's current level.
     *
     * The factory selects the best available [BarType] for the bot's level and sends the bot to a furnace zone. Smelting
     * starts at level 1, so a valid bar should normally always be available.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current smithing level.
     * @param zones The candidate zones available to the factory.
     * @return A smithing script suitable for training.
     * @throws IllegalStateException If no smithing activity can be selected.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        // TODO Try smithing training first.

        // Smelting starts at level 1 so it's always the fallback.
        val smeltBar = getBestActivity(bot, level, { it.level }, BarType.VALUES)
        if (smeltBar == null) {
            throw IllegalStateException("No Smithing activity could be generated.")
        }

        // TODO Add more zones, dexterous bots use better zones.
        zones += SubZone.AL_KHARID_BANK
        return SmeltOreBotScript(bot, smeltBar, getDuration(bot), zones)
    }

    /**
     * Creates a smithing profit script for the bot's current level and bank contents.
     *
     * Profit mode currently uses smelting as the main fallback. When no fixed [BarType] is supplied to
     * [SmeltOreBotScript], the script scans the bot's bank and chooses a bar type whose ore requirements are available.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current smithing level.
     * @param zones The candidate zones available to the factory.
     * @return A smithing script suitable for profit-oriented activity.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        // TODO Smithing profit is making any weapon, and pieces of armor sets that we have.
        if (randBoolean()) {
            return TanHideBotScript(bot, getDuration(bot))
        }

        // TODO Add more zones, dexterous bots use better zones.
        zones += SubZone.AL_KHARID_BANK
        return SmeltOreBotScript(bot, null, getDuration(bot), zones)
    }
}