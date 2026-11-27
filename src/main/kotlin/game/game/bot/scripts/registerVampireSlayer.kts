package game.bot.scripts

import api.predef.*
import game.bot.scripts.VampireSlayerBotScript.VampireSlayerData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<VampireSlayerData>(VampireSlayerBotScript::class) { bot, data ->
        VampireSlayerBotScript(bot, data)
    }
}
