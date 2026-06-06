package game.bot.scripts

import api.bot.Suspendable.naturalDelay
import api.bot.script.DynamicBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * A simple script that will make a [Bot] logout.
 *
 * @author lare96
 */
class LogoutBotScript(bot: Bot) : DynamicBotScript(bot) {

    override suspend fun run(): Boolean {
        handler.widgets.clickLogout()
        bot.naturalDelay()
        // Script itself cancels co-routine by logging the bot out.
        return false
    }
}