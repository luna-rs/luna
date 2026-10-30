package game.bot.scripts

import api.predef.*
import game.bot.scripts.BlastFurnaceBotScript.FurnaceData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<FurnaceData>(BlastFurnaceBotScript::class) { bot, data ->
        BlastFurnaceBotScript(bot, data)
    }
}
