package game.bot.scripts

import api.bot.BotScript
import api.bot.Suspendable.naturalDelay
import io.luna.game.model.mob.bot.Bot

/**
 * A simple script that will make a [Bot] logout.
 *
 * @author lare96
 */
class LogoutBotScript(bot: Bot, private var urgent: Boolean) : BotScript<Boolean>(bot) {

    override suspend fun run(): Boolean {
        if (!urgent) {
            bot.output.clickLogout()
            bot.naturalDelay()
        } else {
            bot.forceLogout()
            bot.naturalDelay()
        }
        // Script itself cancels co-routine by logging the bot out.
        return false
    }

    override fun snapshot(): Boolean = urgent
}