package game.bot.scripts

import game.bot.scripts.skills.FarmingBotScript
import game.bot.scripts.skills.FarmingBotScript.Companion.FarmingData

import game.bot.scripts.GnomeBallBotScript.GnomeBallData
import game.bot.scripts.RechargeGloryBotScript.Companion.GloryData
import game.bot.scripts.BrimhavenArenaBotScript.ArenaData
import game.bot.scripts.RangingGuildBotScript.ArcheryData

import game.bot.scripts.skills.TeleportTrainingBotScript
import game.bot.scripts.DuelArenaBotScript.DuelData
import game.bot.scripts.skills.TeleportTrainingBotScript.Companion.TeleportTrainingData

import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.skills.SlayerBotScript
import game.bot.scripts.skills.SlayerBotScript.SlayerData
import game.bot.scripts.WildernessScavengeBotScript.ScavengeData
import game.bot.scripts.combat.PkBotScript.Companion.PkData

import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import engine.bot.coordinator.ProgressiveCoordinator.ProgressiveScript
import engine.bot.coordinator.ProgressiveCoordinator.ProgressiveScript.ProgressionData
import engine.bot.speech.BotConversationPool
import engine.bot.speech.BotScamSpeechPool
import game.bot.scripts.DepositPartyRoomItemBotScript.DonationData
import game.bot.scripts.CastleWarsBotScript.MatchData
import game.bot.scripts.GamesRoomBotScript.BoardGameData
import game.bot.scripts.TreasureTrailBotScript.Companion.TrailData
import game.bot.scripts.DropPartyBotScript.PartyData
import game.bot.scripts.IdleBotScript.IdleData
import game.bot.scripts.skills.AgilityBotScript
import game.bot.scripts.skills.AgilityBotScript.Companion.AgilityData
import game.bot.scripts.skills.WeaveTextileBotScript
import game.bot.scripts.skills.WeaveTextileBotScript.Companion.TextileData
import game.bot.scripts.UseLampBotScript.Companion.LampData
import game.bot.scripts.HarvestBotScript.Companion.HarvestData
import game.bot.scripts.skills.BuryBoneBotScript
import game.bot.scripts.skills.BuryBoneBotScript.Companion.BoneData
import game.bot.scripts.skills.SuperheatItemBotScript
import game.bot.scripts.skills.SuperheatItemBotScript.Companion.SuperheatItemData
import game.bot.scripts.skills.EnchantJewelleryBotScript
import game.bot.scripts.skills.EnchantJewelleryBotScript.Companion.EnchantJewelleryData
import game.bot.scripts.skills.ChargeOrbBotScript
import game.bot.scripts.skills.ChargeOrbBotScript.Companion.ChargeOrbData
import game.bot.scripts.skills.BonesToFoodBotScript
import game.bot.scripts.skills.BonesToFoodBotScript.Companion.BonesToFoodData
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
import game.bot.scripts.skills.MillFlourBotScript
import game.bot.scripts.skills.MillFlourBotScript.Companion.MillData
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
import game.bot.scripts.skills.MakeCannonballBotScript
import game.bot.scripts.skills.MakeCannonballBotScript.Companion.CannonballData
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.SmithBarBotScript
import game.bot.scripts.skills.SpinTextileBotScript
import game.bot.scripts.skills.StealBotScript
import game.bot.scripts.skills.StealBotScript.Companion.StealData
import game.bot.scripts.skills.StringBowBotScript
import game.bot.scripts.skills.StringJewelleryBotScript
import game.bot.scripts.SearchNestBotScript.Companion.NestData
import game.bot.scripts.OpenCrystalChestBotScript.Companion.ChestData
import game.bot.scripts.FillWaterBotScript.Companion.FillWaterData
import game.bot.scripts.MakeCrystalKeyBotScript.Companion.CrystalKeyData
import game.bot.scripts.skills.StringJewelleryBotScript.Companion.JewelleryData
import game.bot.scripts.skills.StringBowBotScript.Companion.StringBowData
import game.bot.scripts.skills.HerbGatheringScript
import game.bot.scripts.skills.HerbGatheringScript.Companion.GatheringData
import game.bot.scripts.skills.TelegrabBotScript
import game.bot.scripts.skills.TelegrabBotScript.Companion.TelegrabData
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
    BotConversationPool.load()
    BotScamSpeechPool.load()
    val scriptManager = world.botManager.scriptManager
    scriptManager.addScript<GloryData>(RechargeGloryBotScript::class) { bot, data -> RechargeGloryBotScript(bot, data) }
    scriptManager.addScript<GnomeBallData>(GnomeBallBotScript::class) { bot, data -> GnomeBallBotScript(bot, data) }
    scriptManager.addScript<ArenaData>(BrimhavenArenaBotScript::class) { bot, data -> BrimhavenArenaBotScript(bot, data) }
    scriptManager.addScript<ArcheryData>(RangingGuildBotScript::class) { bot, data -> RangingGuildBotScript(bot, data) }
    scriptManager.addScript<DuelData>(DuelArenaBotScript::class) { bot, data -> DuelArenaBotScript(bot, data) }
    scriptManager.addScript<TeleportTrainingData>(TeleportTrainingBotScript::class) { bot, data -> TeleportTrainingBotScript(bot, data) }
    scriptManager.addScript<TrailData>(TreasureTrailBotScript::class) { bot, data -> TreasureTrailBotScript(bot, data) }
    scriptManager.addScript<GatheringData>(HerbGatheringScript::class) { bot, data -> HerbGatheringScript(bot, data) }
    scriptManager.addScript<TelegrabData>(TelegrabBotScript::class) { bot, data -> TelegrabBotScript(bot, data) }
    scriptManager.addScript<PkData>(PkBotScript::class) { bot, data -> PkBotScript(bot, data) }
    scriptManager.addScript<ProgressionData>(ProgressiveScript::class) { bot, data -> ProgressiveScript(bot, data) }
    scriptManager.addScript<IdleData>(IdleBotScript::class) { bot, data -> IdleBotScript(bot, data) }
    scriptManager.addScript<DonationData>(DepositPartyRoomItemBotScript::class) { bot, data -> DepositPartyRoomItemBotScript(bot, data) }
    scriptManager.addScript<PartyData>(DropPartyBotScript::class) { bot, data -> DropPartyBotScript(bot, data) }
    scriptManager.addScript<MatchData>(CastleWarsBotScript::class) { bot, data -> CastleWarsBotScript(bot, data) }
    scriptManager.addScript<BoardGameData>(GamesRoomBotScript::class) { bot, data -> GamesRoomBotScript(bot, data) }
    scriptManager.addScript<AgilityData>(AgilityBotScript::class) { bot, data -> AgilityBotScript(bot, data) }
    scriptManager.addScript<TextileData>(WeaveTextileBotScript::class) { bot, data -> WeaveTextileBotScript(bot, data) }
    scriptManager.addScript<LampData>(UseLampBotScript::class) { bot, data -> UseLampBotScript(bot, data) }
    scriptManager.addScript<CutTreeData>(CutTreeBotScript::class) { bot, data -> CutTreeBotScript(bot, data) }
    scriptManager.addScript<MineData>(MineBotScript::class) { bot, data -> MineBotScript(bot, data) }
    scriptManager.addScript<PickpocketData>(PickpocketBotScript::class) { bot, data -> PickpocketBotScript(bot, data) }
    scriptManager.addScript<StealData>(StealBotScript::class) { bot, data -> StealBotScript(bot, data) }
    scriptManager.addScript<HarvestData>(HarvestBotScript::class) { bot, data -> HarvestBotScript(bot, data) }
    scriptManager.addScript<CutLogData>(CutLogBotScript::class) { bot, data -> CutLogBotScript(bot, data) }
    scriptManager.addScript<StringBowData>(StringBowBotScript::class) { bot, data -> StringBowBotScript(bot, data) }
    scriptManager.addScript<SearchData>(SearchBotScript::class) { bot, data -> SearchBotScript(bot, data) }
    scriptManager.addScript<CannonballData>(MakeCannonballBotScript::class) { bot, data -> MakeCannonballBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SmeltOreBotScript::class) { bot, data -> SmeltOreBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SpinTextileBotScript::class) { bot, data -> SpinTextileBotScript(bot, data) }
    // Older flax snapshots keep their original class name but restore through the generalized constructor.
    scriptManager.scriptMap["game.bot.scripts.skills.SpinFlaxBotScript"] =
        scriptManager.scriptMap.getValue(SpinTextileBotScript::class.qualifiedName!!)
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
    scriptManager.addScript<MillData>(MillFlourBotScript::class) { bot, data -> MillFlourBotScript(bot, data) }
    scriptManager.addScript<SoftClayData>(MakeSoftClayBotScript::class) { bot, data -> MakeSoftClayBotScript(bot, data) }
    scriptManager.addScript<FillWaterData>(FillWaterBotScript::class) { bot, data -> FillWaterBotScript(bot, data) }
    scriptManager.addScript<NestData>(SearchNestBotScript::class) { bot, data -> SearchNestBotScript(bot, data) }
    scriptManager.addScript<ChestData>(OpenCrystalChestBotScript::class) { bot, data -> OpenCrystalChestBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(TanHideBotScript::class) { bot, data -> TanHideBotScript(bot, data) }
    scriptManager.addScript<CollectHidesData>(CollectHidesBotScript::class) { bot, data -> CollectHidesBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(NpcCombatScript::class) { bot, data -> NpcCombatScript(bot, data) }
    scriptManager.addScript<SlayerData>(SlayerBotScript::class) { bot, data -> SlayerBotScript(bot, data) }
    scriptManager.addScript<ScavengeData>(WildernessScavengeBotScript::class) { bot, data -> WildernessScavengeBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(CookFoodBotScript::class) { bot, data -> CookFoodBotScript(bot, data) }
    scriptManager.addScript<ZonedBotScriptData>(SmithBarBotScript::class) { bot, data -> SmithBarBotScript(bot, data) }
    scriptManager.addScript<FishData>(FishBotScript::class) { bot, data -> FishBotScript(bot, data) }
    scriptManager.addScript<CraftRuneData>(CraftRuneBotScript::class) { bot, data -> CraftRuneBotScript(bot, data) }
    scriptManager.addScript<FiremakingData>(FiremakingBotScript::class) { bot, data -> FiremakingBotScript(bot, data) }
    scriptManager.addScript<MakeArrowData>(MakeArrowBotScript::class) { bot, data -> MakeArrowBotScript(bot, data) }
    scriptManager.addScript<BoneData>(BuryBoneBotScript::class) { bot, data -> BuryBoneBotScript(bot, data) }
    scriptManager.addScript<FarmingData>(FarmingBotScript::class) { bot, data -> FarmingBotScript(bot, data) }
    scriptManager.addScript<SuperheatItemData>(SuperheatItemBotScript::class) { bot, data -> SuperheatItemBotScript(bot, data) }
    scriptManager.addScript<EnchantJewelleryData>(EnchantJewelleryBotScript::class) { bot, data -> EnchantJewelleryBotScript(bot, data) }
    scriptManager.addScript<ChargeOrbData>(ChargeOrbBotScript::class) { bot, data -> ChargeOrbBotScript(bot, data) }
    scriptManager.addScript<BonesToFoodData>(BonesToFoodBotScript::class) { bot, data -> BonesToFoodBotScript(bot, data) }
    scriptManager.addScript<AlchemyData>(AlchemyBotScript::class) { bot, data -> AlchemyBotScript(bot, data) }
}
