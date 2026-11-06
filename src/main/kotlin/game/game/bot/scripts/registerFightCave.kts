package game.bot.scripts

import api.predef.*
import game.bot.scripts.FightCaveBotScript.FightCaveData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<FightCaveData>(FightCaveBotScript::class) { bot, data ->
        FightCaveBotScript(bot, data)
    }
}
