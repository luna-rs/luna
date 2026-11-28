package game.bot.scripts

import api.predef.*
import game.bot.scripts.ImpCatcherBotScript.ImpCatcherData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<ImpCatcherData>(ImpCatcherBotScript::class) { bot, data ->
        ImpCatcherBotScript(bot, data)
    }
}
