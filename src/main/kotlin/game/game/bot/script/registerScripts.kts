package game.bot.script

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Register all scripts.
 */
on(ServerLaunchEvent::class) {
    val scriptManager = world.botManager.scriptManager
    scriptManager.addScript(IdleBotScript::class) { bot, data -> IdleBotScript(bot, data) }
    scriptManager.addScript(LogoutBotScript::class) { bot, data -> LogoutBotScript(bot, data) }
}