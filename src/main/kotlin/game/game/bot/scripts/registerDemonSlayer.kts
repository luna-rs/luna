package game.bot.scripts

import api.predef.*
import game.bot.scripts.DemonSlayerBotScript.DemonSlayerData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<DemonSlayerData>(DemonSlayerBotScript::class) { bot, data ->
        DemonSlayerBotScript(bot, data)
    }
}
