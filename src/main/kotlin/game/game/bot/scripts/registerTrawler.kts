package game.bot.scripts

import api.predef.*
import game.bot.scripts.FishingTrawlerBotScript.VoyageData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<VoyageData>(FishingTrawlerBotScript::class) { bot, data ->
        FishingTrawlerBotScript(bot, data)
    }
}
