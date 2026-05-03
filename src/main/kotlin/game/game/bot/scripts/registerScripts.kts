package game.bot.scripts

import api.bot.scripts.IdleBotScript
import api.predef.*
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.skills.MiningBotScript
import game.bot.scripts.skills.WoodcuttingBotScript
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Register all scripts.
 */
on(ServerLaunchEvent::class) {
    val scriptManager = world.botManager.scriptManager
    scriptManager.addScript(IdleBotScript::class) { bot, data -> IdleBotScript(bot, data) }
    scriptManager.addScript(PkBotScript::class) { bot, data -> PkBotScript(bot, data) }
    scriptManager.addScript(WoodcuttingBotScript::class) { bot, data -> WoodcuttingBotScript(bot, data) }
    scriptManager.addScript(MiningBotScript::class) { bot, data -> MiningBotScript(bot, data) }
    scriptManager.addScript(HarvestResourceBotScript::class) { bot, data -> HarvestResourceBotScript(bot, data) }
    //scriptManager.addScript(FishingBotScript::class) { bot, data -> FishingBotScript(bot, data) }
    //scriptManager.addScript(GoldScamBotScript::class) { bot, data -> FishingBotScript(bot, data) }
    //scriptManager.addScript(GearScamBotScript::class) { bot, data -> FishingBotScript(bot, data) }
    //scriptManager.addScript(PkScamBotScript::class) { bot, data -> FishingBotScript(bot, data) }
    /*scriptManager.addScript(AgilityBotScript::class) { bot, data -> AgilityBotScript(bot, data) }
    scriptManager.addScript(ThievingBotScript::class) { bot, data -> ThievingBotScript(bot, data) }
    scriptManager.addScript(PrayerBotScript::class) { bot, data -> PrayerBotScript(bot, data) }
    scriptManager.addScript(SmeltingBotScript::class) { bot, data -> SmeltingBotScript(bot, data) }
    scriptManager.addScript(SmithingBotScript::class) { bot, data -> SmithingBotScript(bot, data) }
    scriptManager.addScript(HighAlchBotScript::class) { bot, data -> HighAlchBotScript(bot, data) }
    scriptManager.addScript(RunecraftingBotScript::class) { bot, data -> RunecraftingBotScript(bot, data) }
    scriptManager.addScript(TelegrabWinesBotScript::class) { bot, data -> TeleGrabWinesBotScript(bot, data) }
    scriptManager.addScript(IdentifyHerbsBotScript::class) { bot, data -> IdentifyHerbsBotScript(bot, data) }
    scriptManager.addScript(MakeUnfPotionBotScript::class) { bot, data -> MakeUnfPotionBotScript(bot, data) }
    scriptManager.addScript(MakePotionBotScript::class) { bot, data -> MakePotionBotScript(bot, data) }
    scriptManager.addScript(CookFoodBotScript::class) { bot, data -> CookFoodBotScript(bot, data) }
    scriptManager.addScript(FiremakingBotScript::class) { bot, data -> FiremakingBotScript(bot, data) } */
}
