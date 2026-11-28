package game.bot.scripts

import api.predef.*
import game.bot.scripts.CooksAssistantBotScript.CooksAssistantData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<CooksAssistantData>(CooksAssistantBotScript::class) { bot, data ->
        CooksAssistantBotScript(bot, data)
    }
}
