package game.bot.scripts

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<PrinceAliRescueBotScript.Data>(PrinceAliRescueBotScript::class) { bot, data ->
        PrinceAliRescueBotScript(bot, data)
    }
}
