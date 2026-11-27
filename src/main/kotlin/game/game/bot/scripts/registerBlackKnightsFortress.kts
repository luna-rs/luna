package game.bot.scripts

import api.predef.*
import game.bot.scripts.BlackKnightsFortressBotScript.Data
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<Data>(BlackKnightsFortressBotScript::class) { bot, data ->
        BlackKnightsFortressBotScript(bot, data)
    }
}
