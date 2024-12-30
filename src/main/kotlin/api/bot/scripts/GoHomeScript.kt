package api.bot.scripts

import api.bot.CoroutineBotScript
import api.bot.Signals
import api.bot.SuspendableFuture
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.mob.bot.Bot

/**
 * A [CoroutineBotScript] that makes a [Bot] return to the ::home area. If they are nearby, they will walk. Otherwise,
 * the ::home command will be used.
 */
class GoHomeScript(bot: Bot) : CoroutineBotScript(bot) {
    override suspend fun run() {
        // TODO Check if in wilderness, minigame, etc.
        val home = Luna.settings().game().startingPosition()
        if (bot.position.isWithinDistance(home, Region.SIZE)) {
            botActions.walk(home).signalWhen(600, Signals.within(bot, home, 5)).await()
        } else {
            val future = SuspendableFuture()
            botActions.sendCommand("home")
            future.signalWhen(600, Signals.within(bot, home, 5)).await()
        }
    }
}