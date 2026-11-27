package game.bot.scripts

import api.predef.*
import game.bot.scripts.RuneMysteriesBotScript.RuneMysteriesData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<RuneMysteriesData>(RuneMysteriesBotScript::class) { bot, data ->
        RuneMysteriesBotScript(bot, data)
    }
}
