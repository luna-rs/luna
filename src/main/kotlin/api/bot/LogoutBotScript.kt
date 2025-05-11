package api.bot

import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotScript

/**
 * A [BotScript] implementation used by bots when they are scheduled for logout.
 */
class LogoutBotScript(bot: Bot) : BotScript(bot) {

    override fun process(): Int {
        /* TODO exit any minigames, if in combat  */
        bot.output.clickLogout()
        return 2
    }
}