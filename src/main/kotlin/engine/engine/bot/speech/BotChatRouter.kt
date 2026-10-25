package engine.bot.speech

import api.predef.*
import io.luna.game.event.impl.ChatEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * One public chat message, as bot chat handlers see it.
 *
 * @property speaker The player who spoke, human or bot.
 * @property text The message in lowercase.
 * @author TheLining
 */
class BotChatMessage(val speaker: Player, val text: String) {

    /**
     * The active bots that can see the speaker, leaving out any bot that ignores the speaker or that the speaker
     * ignores. Looked up only when a handler asks, and only once per message.
     */
    val hearers: List<Bot> by lazy(LazyThreadSafetyMode.NONE) {
        world.locator.findViewablePlayers(speaker) {
            it is Bot && it !== speaker && it.state == EntityState.ACTIVE &&
                !speaker.ignores.contains(it.usernameHash) && !it.ignores.contains(speaker.usernameHash)
        }.map { it as Bot }
    }
}

/**
 * A bot behaviour that reacts to public chat.
 *
 * @author TheLining
 */
fun interface BotChatHandler {

    /**
     * Reacts to [message], or ignores it.
     *
     * @param message The chat message.
     * @return `true` if this handler claimed the message, so later handlers skip it.
     */
    fun handle(message: BotChatMessage): Boolean
}

/**
 * Hands each public chat message to the bot chat handlers once.
 *
 * The bot injector manager passes events to every bot in view, once per bot. A chat handler there would read the same
 * message once for each nearby bot. The router reads it once and lets the handler pick which bots answer, so an
 * ordinary message costs one lowercase copy plus each handler's own quick check.
 *
 * @author TheLining
 */
object BotChatRouter {

    /**
     * The handlers, in the order they get each message.
     */
    private val handlers = ArrayList<BotChatHandler>()

    /**
     * Adds a handler. Call this while scripts load.
     *
     * @param handler The handler.
     */
    fun add(handler: BotChatHandler) {
        handlers += handler
    }

    /**
     * Passes a public chat message to the handlers until one claims it.
     *
     * @param event The chat event.
     */
    fun route(event: ChatEvent) {
        if (handlers.isEmpty()) {
            return
        }
        val message = BotChatMessage(event.plr, event.unpackedMessage.lowercase())
        for (handler in handlers) {
            if (handler.handle(message)) {
                return
            }
        }
    }
}
