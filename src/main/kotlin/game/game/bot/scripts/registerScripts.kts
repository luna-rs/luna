package game.bot.scripts

import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import game.bot.scripts.HarvestBotScript.Companion.HarvestData
import game.bot.scripts.skills.AlchemyBotScript
import game.bot.scripts.skills.AlchemyBotScript.Companion.AlchemyData
import game.bot.scripts.skills.CollectHidesBotScript
import game.bot.scripts.skills.CollectHidesBotScript.Companion.CollectHidesData
import game.bot.scripts.skills.CookFoodBotScript
import game.bot.scripts.skills.CraftArmorBotScript
import game.bot.scripts.skills.CraftArmorBotScript.Companion.CraftArmorData
import game.bot.scripts.skills.CraftRuneBotScript
import game.bot.scripts.skills.CraftRuneBotScript.Companion.CraftRuneData
import game.bot.scripts.skills.MakePotteryBotScript
import game.bot.scripts.skills.MakePotteryBotScript.Companion.PotteryData
import game.bot.scripts.skills.CraftJewelleryBotScript
import game.bot.scripts.skills.CraftJewelleryBotScript.Companion.CraftJewelleryData
import game.bot.scripts.skills.MakeMoltenGlassBotScript
import game.bot.scripts.skills.MakeMoltenGlassBotScript.Companion.MoltenGlassData
import game.bot.scripts.skills.BlowGlassBotScript
import game.bot.scripts.skills.BlowGlassBotScript.Companion.GlassData
import game.bot.scripts.skills.CutGemBotScript
import game.bot.scripts.skills.CutGemBotScript.Companion.CutGemData
import game.bot.scripts.skills.CutLogBotScript
import game.bot.scripts.skills.CutLogBotScript.Companion.CutLogData
import game.bot.scripts.skills.CutTreeBotScript
import game.bot.scripts.skills.CutTreeBotScript.Companion.CutTreeData
import game.bot.scripts.skills.FiremakingBotScript
import game.bot.scripts.skills.FiremakingBotScript.Companion.FiremakingData
import game.bot.scripts.skills.FishBotScript
import game.bot.scripts.skills.FishBotScript.Companion.FishData
import game.bot.scripts.skills.IdentifyHerbBotScript
import game.bot.scripts.skills.IdentifyHerbBotScript.Companion.HerbData
import game.bot.scripts.skills.GrindIngredientBotScript
import game.bot.scripts.skills.GrindIngredientBotScript.Companion.IngredientData
import game.bot.scripts.skills.MakeArrowBotScript
import game.bot.scripts.skills.MakeArrowBotScript.Companion.MakeArrowData
import game.bot.scripts.skills.MakeBattlestaffBotScript
import game.bot.scripts.skills.MakeSoftClayBotScript
import game.bot.scripts.skills.MakeDoughBotScript
import game.bot.scripts.skills.CutFoodBotScript
import game.bot.scripts.skills.AssembleFoodBotScript
import game.bot.scripts.skills.AssembleFoodBotScript.Companion.AssemblyData
import game.bot.scripts.skills.CutFoodBotScript.Companion.CutFoodData
import game.bot.scripts.skills.MakeDoughBotScript.Companion.DoughData
import game.bot.scripts.skills.MakeSoftClayBotScript.Companion.SoftClayData
import game.bot.scripts.skills.MakeBattlestaffBotScript.Companion.BattlestaffData
import game.bot.scripts.skills.MakePotionBotScript
import game.bot.scripts.skills.MakePotionBotScript.Companion.PotionData
import game.bot.scripts.skills.MakeUnfPotionBotScript
import game.bot.scripts.skills.MakeUnfPotionBotScript.Companion.UnfPotionData
import game.bot.scripts.skills.MineBotScript
import game.bot.scripts.skills.MineBotScript.Companion.MineData
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.PickpocketBotScript.Companion.PickpocketData
import game.bot.scripts.skills.SearchBotScript
import game.bot.scripts.skills.SearchBotScript.Companion.SearchData
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.SmithBarBotScript
import game.bot.scripts.skills.SpinFlaxBotScript
import game.bot.scripts.skills.StealBotScript
import game.bot.scripts.skills.StealBotScript.Companion.StealData
import game.bot.scripts.skills.StringBowBotScript
import game.bot.scripts.skills.StringJewelleryBotScript
import game.bot.scripts.MakeCrystalKeyBotScript
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.SearchNestBotScript
import game.bot.scripts.SearchNestBotScript.Companion.NestData
import game.bot.scripts.OpenCrystalChestBotScript
import game.bot.scripts.OpenCrystalChestBotScript.Companion.ChestData
import game.bot.scripts.FillWaterBotScript.Companion.FillWaterData
import game.bot.scripts.MakeCrystalKeyBotScript.Companion.CrystalKeyData
import game.bot.scripts.skills.StringJewelleryBotScript.Companion.JewelleryData
import game.bot.scripts.skills.StringBowBotScript.Companion.StringBowData
import game.bot.scripts.skills.TanHideBotScript
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Registers bot-script constructors once during server launch.
 *
 * Each callback reconstructs its script from the corresponding saved data type when the script stack is loaded.
 * Activity selection remains in the existing coordinators and factories; these callbacks restore saved sessions.
 * Recipe-specific scripts such as gem cutting retain their recipe and retry counters through their data classes.
 *
 * @author lare96
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
    scriptManager.addScript<ZonedBotScriptData>(SmeltOreBotScript::class) { bot, data -> SmeltOreBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SpinFlaxBotScript::class) { bot, data -> SpinFlaxBotScript(bot, data) }
    scriptManager.addScript<CraftArmorData>(CraftArmorBotScript::class) { bot, data -> CraftArmorBotScript(bot, data) }
    scriptManager.addScript<PotteryData>(MakePotteryBotScript::class) { bot, data -> MakePotteryBotScript(bot, data) }
    scriptManager.addScript<CraftJewelleryData>(CraftJewelleryBotScript::class) { bot, data -> CraftJewelleryBotScript(bot, data) }
    scriptManager.addScript<MoltenGlassData>(MakeMoltenGlassBotScript::class) { bot, data -> MakeMoltenGlassBotScript(bot, data) }
    scriptManager.addScript<GlassData>(BlowGlassBotScript::class) { bot, data -> BlowGlassBotScript(bot, data) }
    scriptManager.addScript<CutGemData>(CutGemBotScript::class) { bot, data -> CutGemBotScript(bot, data) }
    scriptManager.addScript<BattlestaffData>(MakeBattlestaffBotScript::class) { bot, data -> MakeBattlestaffBotScript(bot, data) }
    scriptManager.addScript<HerbData>(IdentifyHerbBotScript::class) { bot, data -> IdentifyHerbBotScript(bot, data) }
    scriptManager.addScript<IngredientData>(GrindIngredientBotScript::class) { bot, data -> GrindIngredientBotScript(bot, data) }
    scriptManager.addScript<PotionData>(MakePotionBotScript::class) { bot, data -> MakePotionBotScript(bot, data) }
    scriptManager.addScript<UnfPotionData>(MakeUnfPotionBotScript::class) { bot, data -> MakeUnfPotionBotScript(bot, data) }
    scriptManager.addScript<JewelleryData>(StringJewelleryBotScript::class) { bot, data -> StringJewelleryBotScript(bot, data) }
    scriptManager.addScript<CrystalKeyData>(MakeCrystalKeyBotScript::class) { bot, data -> MakeCrystalKeyBotScript(bot, data) }
    scriptManager.addScript<AssemblyData>(AssembleFoodBotScript::class) { bot, data -> AssembleFoodBotScript(bot, data) }
    scriptManager.addScript<CutFoodData>(CutFoodBotScript::class) { bot, data -> CutFoodBotScript(bot, data) }
    scriptManager.addScript<DoughData>(MakeDoughBotScript::class) { bot, data -> MakeDoughBotScript(bot, data) }
    scriptManager.addScript<SoftClayData>(MakeSoftClayBotScript::class) { bot, data -> MakeSoftClayBotScript(bot, data) }
    scriptManager.addScript<FillWaterData>(FillWaterBotScript::class) { bot, data -> FillWaterBotScript(bot, data) }
    scriptManager.addScript<NestData>(SearchNestBotScript::class) { bot, data -> SearchNestBotScript(bot, data) }
    scriptManager.addScript<ChestData>(OpenCrystalChestBotScript::class) { bot, data -> OpenCrystalChestBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(TanHideBotScript::class) { bot, data -> TanHideBotScript(bot, data) }
    scriptManager.addScript<CollectHidesData>(CollectHidesBotScript::class) { bot, data -> CollectHidesBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(NpcCombatScript::class) { bot, data -> NpcCombatScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(CookFoodBotScript::class) { bot, data -> CookFoodBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SmithBarBotScript::class) { bot, data -> SmithBarBotScript(bot, data) }
    scriptManager.addScript<FishData>(FishBotScript::class) { bot, data -> FishBotScript(bot, data) }
    scriptManager.addScript<CraftRuneData>(CraftRuneBotScript::class) { bot, data -> CraftRuneBotScript(bot, data) }
    scriptManager.addScript<FiremakingData>(FiremakingBotScript::class) { bot, data -> FiremakingBotScript(bot, data) }
    scriptManager.addScript<MakeArrowData>(MakeArrowBotScript::class) { bot, data -> MakeArrowBotScript(bot, data) }
    scriptManager.addScript<AlchemyData>(AlchemyBotScript::class) { bot, data -> AlchemyBotScript(bot, data) }
}
