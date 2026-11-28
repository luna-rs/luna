package game.bot.scripts

import api.predef.*
import game.bot.scripts.quests.HeroesQuestBotScript
import game.bot.scripts.quests.MerlinsCrystalBotScript
import game.bot.scripts.quests.QuestAttemptData
import game.bot.scripts.quests.ShieldOfArravBotScript
import game.bot.scripts.quests.ShieldOfArravBotScript.ArravData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    val manager = world.botManager.scriptManager
    manager.addScript<ArravData>(ShieldOfArravBotScript::class) { bot, data -> ShieldOfArravBotScript(bot, data) }
    manager.addScript<QuestAttemptData>(MerlinsCrystalBotScript::class) { bot, data -> MerlinsCrystalBotScript(bot, data) }
    manager.addScript<QuestAttemptData>(HeroesQuestBotScript::class) { bot, data -> HeroesQuestBotScript(bot, data) }
}
