package game.bot.scripts

import api.bot.Suspendable.naturalDecisionDelay
import io.luna.game.model.mob.bot.Bot
import api.bot.script.DynamicBotScript

/**
 * A simple script that will make a [io.luna.game.model.mob.bot.Bot] logout.
 *
 * @author lare96
 */
class LogoutBotScript(bot: Bot) : DynamicBotScript(bot) {

    override suspend fun run(): Boolean {
        bot.logout(false)
        bot.naturalDecisionDelay()
        return false
    }
}