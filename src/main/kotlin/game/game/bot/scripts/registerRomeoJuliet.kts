package game.bot.scripts

import game.bot.scripts.RomeoJulietBotScript.RomeoJulietData
import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<RomeoJulietData>(RomeoJulietBotScript::class) { bot, _ ->
        RomeoJulietBotScript(bot)
    }
}
