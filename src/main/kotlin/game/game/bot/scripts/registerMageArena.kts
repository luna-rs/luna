package game.bot.scripts

import api.predef.*
import game.bot.scripts.MageArenaBotScript.ArenaData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.botManager.scriptManager.addScript<ArenaData>(MageArenaBotScript::class) { bot, data -> MageArenaBotScript(bot, data) }
}
