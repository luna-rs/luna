package engine.bot.coordinator.skill

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.*
import game.skill.prayer.Bone
import game.skill.smithing.BarType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Covers migrated factory dispatch, production priority, and fallback behavior across the supported skills.
 *
 * @author lare96
 */
class SkillingFactoryMigrationTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private fun bot(level: Int): Bot = InventoryProductionFixtures.bot().also {
        for (skill in 0..20) {
            `when`(it.skill(skill).staticLevel).thenReturn(level)
            `when`(it.skill(skill).level).thenReturn(level)
        }
        `when`(it.position).thenReturn(Position(3013, 3360))
        `when`(it.username).thenReturn("factory_test")
        doReturn(java.util.EnumSet.noneOf(SubZone::class.java)).`when`(it).subZones
        `when`(it.personality.isIntelligent).thenReturn(true)
        `when`(it.personality.intelligence).thenReturn(1.0)
    }

    @Test
    fun allFourteenFactoriesDispatchBothObjectivesWithoutEmptyGatheringTargets() {
        val factories = listOf(MiningScriptFactory, WoodcuttingScriptFactory, FishingScriptFactory,
            ThievingScriptFactory, FiremakingScriptFactory, FletchingScriptFactory, CookingScriptFactory,
            CraftingScriptFactory, HerbloreScriptFactory, SmithingScriptFactory, MagicScriptFactory,
            PrayerScriptFactory, RunecraftingScriptFactory, AgilityScriptFactory)
        for (level in listOf(1, 30, 60, 99)) {
            val bot = bot(level)
            for (factory in factories) for (training in listOf(true, false)) {
                val zones = mutableListOf<SubZone>()
                val script = factory.getScript(bot, level, zones, training)
                when (script) {
                    is MineBotScript -> assertTrue(script.ores.isNotEmpty())
                    is CutTreeBotScript -> assertTrue(script.trees.isNotEmpty())
                    is PickpocketBotScript -> assertTrue(script.npcs.isNotEmpty())
                    is StealBotScript -> assertTrue(script.stalls.isNotEmpty())
                    is SearchBotScript -> assertTrue(script.chests.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun ownedCannonballsKeepPriorityAndMissingMouldFallsBackToSmithing() {
        val bot = bot(99)
        InventoryProductionFixtures.bank(bot, Item(BarType.STEEL.id, 10), Item(4))
        for (training in listOf(true, false)) {
            assertTrue(SmithingScriptFactory.getScript(bot, 99, mutableListOf(), training) is MakeCannonballBotScript)
        }
        bot.bank.remove(Item(4))
        assertTrue(SmithingScriptFactory.getTrainingScript(bot, 99, mutableListOf()) is SmithBarBotScript)
    }

    @Test
    fun prayerUsesOwnedBonesOnlyForTraining() {
        val bot = bot(99)
        InventoryProductionFixtures.bank(bot, Item(Bone.BONES.id, 10))
        assertTrue(PrayerScriptFactory.getTrainingScript(bot, 99, mutableListOf()) is BuryBoneBotScript)
        assertFalse(PrayerScriptFactory.getProfitScript(bot, 99, mutableListOf()) is BuryBoneBotScript)
    }

    @Test
    fun unavailableLogsDelegateFiremakingToWoodcuttingAndOwnedLogsEnableBurning() {
        val bot = bot(60)
        assertTrue(FiremakingScriptFactory.getTrainingScript(bot, 60, mutableListOf()) is CutTreeBotScript)
        InventoryProductionFixtures.bank(bot, Item(1511, 10))
        assertTrue(FiremakingScriptFactory.getTrainingScript(bot, 60, mutableListOf()) is FiremakingBotScript)
        assertTrue(FiremakingScriptFactory.getProfitScript(bot, 60, mutableListOf()) is CutTreeBotScript)
    }

    @Test
    fun fletchingUsesOwnedMatchingBowsThenArrowsBeforeLogAcquisition() {
        val bot = bot(60)
        val bow = game.skill.fletching.stringBow.Bow.OAK_LONGBOW
        InventoryProductionFixtures.bank(bot, Item(bow.unstrung), Item(1777))
        for (training in listOf(true, false)) {
            val script = FletchingScriptFactory.getScript(bot, 60, mutableListOf(), training)
            assertTrue(script is StringBowBotScript)
            assertEquals(bow, (script as StringBowBotScript).bow)
        }
        bot.bank.remove(Item(1777))
        InventoryProductionFixtures.bank(bot, Item(52, 15), Item(314, 15))
        for (training in listOf(true, false)) {
            assertTrue(FletchingScriptFactory.getScript(bot, 60, mutableListOf(), training) is MakeArrowBotScript)
        }
        assertNull(FletchingScriptFactory.getOwnedBowScript(bot, 60))
    }

    @Test
    fun thievingAtIntermediateLevelsAlwaysHasAnEligibleProfitMethod() {
        for (level in listOf(1, 2, 26, 27, 40, 49, 50, 65, 74, 75, 99)) {
            val bot = bot(level)
            `when`(bot.personality.intelligence).thenReturn(0.0)
            `when`(bot.personality.isIntelligent).thenReturn(false)
            repeat(10) {
                val script = ThievingScriptFactory.getProfitScript(bot, level, mutableListOf())
                when (script) {
                    is StealBotScript -> assertTrue(script.stalls.isNotEmpty() && script.stalls.all { it.level <= level })
                    is PickpocketBotScript -> assertTrue(script.npcs.isNotEmpty() && script.npcs.all { it.level <= level })
                    is SearchBotScript -> assertTrue(script.chests.isNotEmpty() && script.chests.all { it.level <= level })
                    else -> fail("Unexpected thieving fallback at level $level")
                }
            }
        }
    }
}
