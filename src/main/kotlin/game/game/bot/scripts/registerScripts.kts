package game.bot.scripts

import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import game.bot.scripts.HarvestBotScript.Companion.HarvestData
import game.bot.scripts.skills.CraftArmorBotScript
import game.bot.scripts.skills.CraftArmorBotScript.Companion.CraftArmorData
import game.bot.scripts.skills.CutLogBotScript
import game.bot.scripts.skills.CutLogBotScript.Companion.CutLogData
import game.bot.scripts.skills.CutTreeBotScript
import game.bot.scripts.skills.CutTreeBotScript.Companion.CutTreeData
import game.bot.scripts.skills.MineBotScript
import game.bot.scripts.skills.MineBotScript.Companion.MineData
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.PickpocketBotScript.Companion.PickpocketData
import game.bot.scripts.skills.SearchBotScript
import game.bot.scripts.skills.SearchBotScript.Companion.SearchData
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.SmeltOreBotScript.Companion.SmeltOreData
import game.bot.scripts.skills.SpinFlaxBotScript
import game.bot.scripts.skills.StealBotScript
import game.bot.scripts.skills.StealBotScript.Companion.StealData
import game.bot.scripts.skills.StringBowBotScript
import game.bot.scripts.skills.StringBowBotScript.Companion.StringBowData
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Register all scripts.
 */
on(ServerLaunchEvent::class) {
    val scriptManager = world.botManager.scriptManager
    scriptManager.addScript<CutTreeData>(CutTreeBotScript::class) { bot, data -> CutTreeBotScript(bot, data) }
    scriptManager.addScript<MineData>(MineBotScript::class) { bot, data -> MineBotScript(bot, data) }
    scriptManager.addScript<PickpocketData>(PickpocketBotScript::class) { bot, data -> PickpocketBotScript(bot, data) }
    scriptManager.addScript<StealData>(StealBotScript::class) { bot, data -> StealBotScript(bot, data) }
    scriptManager.addScript<HarvestData>(HarvestBotScript::class) { bot, data -> HarvestBotScript(bot, data) }
    scriptManager.addScript<CutLogData>(CutLogBotScript::class) { bot, data -> CutLogBotScript(bot, data) }
    scriptManager.addScript<StringBowData>(StringBowBotScript::class) { bot, data -> StringBowBotScript(bot, data) }
    scriptManager.addScript<SearchData>(SearchBotScript::class) { bot, data -> SearchBotScript(bot, data) }
    scriptManager.addScript<SmeltOreData>(SmeltOreBotScript::class) { bot, data -> SmeltOreBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SpinFlaxBotScript::class) { bot, data -> SpinFlaxBotScript(bot, data) }
    scriptManager.addScript<CraftArmorData>(CraftArmorBotScript::class) { bot, data -> CraftArmorBotScript(bot, data) }

}
