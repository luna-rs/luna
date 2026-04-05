package game.bot.scripts

import api.bot.BotScript
import api.bot.Suspendable.naturalDelay
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot

/**
 * A simple script that will make a [Bot] logout.
 *
 * @author lare96
 */
class LogoutBotScript(bot: Bot, private var urgent: Boolean) : BotScript<Boolean>(bot) {

    override suspend fun run() {
        if (!urgent) {
            while (bot.state == EntityState.ACTIVE) {
                bot.output.clickLogout()
                bot.naturalDelay()
            }
        } else {
            bot.forceLogout()
            bot.naturalDelay()
        }

    }

    override fun snapshot(): Boolean = urgent
}