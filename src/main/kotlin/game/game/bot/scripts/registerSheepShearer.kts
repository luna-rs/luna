package game.bot.scripts

import api.predef.*
import game.bot.scripts.SheepShearerBotScript.SheepShearerData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<SheepShearerData>(SheepShearerBotScript::class) { bot, _ ->
        SheepShearerBotScript(bot)
    }
}
