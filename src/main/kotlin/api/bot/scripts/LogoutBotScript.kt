package api.bot.scripts

import api.bot.BotScript
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptSnapshot

/**
 * A simple script that will make a [Bot] logout.
 *
 * @author lare96
 */
class LogoutBotScript(bot: Bot, private var urgent: Boolean) : BotScript<Boolean>(bot) {

    override suspend fun run() {
        if (!urgent) {
            bot.naturalDecisionDelay()
        }
        bot.output.clickLogout()
    }

    override fun snapshot(): Boolean = urgent
    override fun load(snapshot: BotScriptSnapshot<Boolean>) {
        urgent = snapshot.data
    }
}