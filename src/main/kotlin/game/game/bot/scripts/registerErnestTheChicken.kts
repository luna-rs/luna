package game.bot.scripts

import api.predef.*
import game.bot.scripts.ErnestTheChickenBotScript.ErnestData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<ErnestData>(ErnestTheChickenBotScript::class) { bot, data ->
        ErnestTheChickenBotScript(bot, data)
    }
}
