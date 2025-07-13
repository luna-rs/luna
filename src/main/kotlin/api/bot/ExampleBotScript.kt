package api.bot

import api.bot.scripts.ExampleSubScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotScript

/**
 * An example bot script that makes use of a [CoroutineBotScript] subscript.
 */
class ExampleBotScript(bot: Bot) : BotScript(bot) {

    /**
     * Our coroutine script here. Will wait for our bot to return ::home.
     */
    private val exampleSubScript = ExampleSubScript(bot)

    override fun start(): Boolean {
        // Start our coroutine subscript.
        exampleSubScript.start()
        return true
    }

    override fun process(): Int {
        // Coroutine will log the bot out, nothing to process.
        return 1
    }
}