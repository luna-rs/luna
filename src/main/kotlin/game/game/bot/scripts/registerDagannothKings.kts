package game.bot.scripts

import api.predef.*
import game.bot.scripts.DagannothKingsBotScript.DagannothKingsData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<DagannothKingsData>(DagannothKingsBotScript::class) { bot, data ->
        DagannothKingsBotScript(bot, data)
    }
}
