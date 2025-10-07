package api.bot.scripts

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import world.player.skill.firemaking.Log

/**
 * Register all speech injectors.
 */
on(ServerLaunchEvent::class) {
    val scriptManager = world.botManager.scriptManager
    scriptManager.addScript(IdleBotScript::class) { bot, data -> IdleBotScript(bot, data) }
    scriptManager.addScript(LogoutBotScript::class) { bot, data -> LogoutBotScript(bot, data) }
}