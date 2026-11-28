package game.bot.scripts

import api.predef.*
import game.bot.scripts.DruidicRitualBotScript.Data
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<Data>(DruidicRitualBotScript::class) { bot, data ->
        DruidicRitualBotScript(bot, data)
    }
}
