package api.bot

import api.bot.scripts.GoHomeScript
import api.predef.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotScript

/**
 * An example bot script that makes use of a [CoroutineBotScript] subscript.
 */
class ExampleBotScript(bot: Bot) : BotScript(bot) {

    /**
     * Our coroutine script here. Will wait for our bot to return ::home.
     */
    private val goHomeScript = GoHomeScript(bot)

    override fun start(): Boolean {
        // Teleport bot to a random region, for the sake of the example.
        bot.move(ctx.cache.mapIndexTable.allRegions.random().absPosition)
        return true
    }

    override fun process(): Int {

        if (executions == 0) {
            // Start coroutine only on first execution of script.
            goHomeScript.start()
        } else if (goHomeScript.isDone()) {
            // Once coroutine completes, bot will send a chat.
            botActions.chat("Coroutine complete.")
        }
        return 2
    }
}