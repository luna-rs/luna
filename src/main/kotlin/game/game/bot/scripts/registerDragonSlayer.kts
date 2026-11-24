package game.bot.scripts

import api.predef.*
import game.bot.scripts.DragonSlayerBotScript.DragonSlayerData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<DragonSlayerData>(DragonSlayerBotScript::class) { bot, data ->
        DragonSlayerBotScript(bot, data)
    }
}
