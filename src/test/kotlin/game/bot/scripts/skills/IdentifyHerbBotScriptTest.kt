package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.Iterators
import com.google.gson.JsonObject
import game.skill.herblore.identifyHerb.Herb
import game.bot.scripts.skills.IdentifyHerbBotScript.Companion.HerbData
import engine.bot.coordinator.skill.HerbloreScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator
import kotlinx.coroutines.runBlocking
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class IdentifyHerbBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun factoryChoosesOwnedLowLevelHerbsForHighLevelBots() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Herb.GUAM_LEAF.id, 5))
        val script = HerbloreScriptFactory.getTrainingScript(bot, 99, mutableListOf()) as IdentifyHerbBotScript
        assertEquals(Herb.GUAM_LEAF, script.herb)
        assertEquals(listOf(Item(Herb.GUAM_LEAF.id, 5)), script.bankBatch())
        assertEquals(Herb.GUAM_LEAF, IdentifyHerbBotScript(bot, script.snapshot()).herb)
        assertInstanceOf(IdentifyHerbBotScript::class.java,
            HerbloreScriptFactory.getProfitScript(bot, 99, mutableListOf()))
        `when`(bot.herblore.staticLevel).thenReturn(2)
        assertFalse(script.isEligible())
    }

    @Test fun emptyFactoryFallbackRecordsWantedHerbsAndEnds() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        val script = HerbloreScriptFactory.getTrainingScript(bot, 3, mutableListOf())
        assertTrue(script.run())
        verify(bot.preferences).raiseWantedItemTarget(Herb.GUAM_LEAF.id, 1_000)
    }

    @ParameterizedTest @EnumSource(Herb::class)
    fun existingIdentificationHandlerConsumesAndAwardsExperience(herb: Herb) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, herb.idItem)
        val handler = Class.forName("game.skill.herblore.identifyHerb.IdentifyHerb")
            .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
        handler.javaClass.getMethod("identify", io.luna.game.model.mob.Player::class.java, Herb::class.java)
            .invoke(handler, bot, herb)
        assertTrue(bot.inventory.contains(herb.identified))
        assertFalse(bot.inventory.contains(herb.id))
        verify(bot.herblore).addExperience(herb.exp)
    }

    @Test fun coordinatorDispatchesHerbloreInBothModes() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Herb.GUAM_LEAF.idItem)
        `when`(bot.skills.iterator()).thenAnswer { Iterators.singletonIterator(bot.herblore) }
        `when`(bot.herblore.id).thenReturn(SKILL_HERBLORE)
        `when`(bot.preferences.skills).thenReturn(emptySet())
        SkillingCoordinator(true).accept(bot)
        SkillingCoordinator(false).accept(bot)
        val scripts = ArgumentCaptor.forClass(BotScript::class.java)
        verify(bot.scriptStack, times(2)).push(scripts.capture())
        scripts.allValues.forEach {
            assertInstanceOf(IdentifyHerbBotScript::class.java, it)
            assertEquals(Herb.GUAM_LEAF, (it as IdentifyHerbBotScript).herb)
        }
    }

    @ParameterizedTest @ValueSource(booleans = [true, false])
    fun factoryRaisesLowLevelBotsBeforeSelectingOwnedHerbs(training: Boolean) {
        val bot = InventoryProductionFixtures.bot()
        val skill = realHerblore(bot, 100.0)
        val bank = spy(bot.bank)
        `when`(bot.bank).thenReturn(bank)
        InventoryProductionFixtures.bank(bot, Herb.GUAM_LEAF.idItem)
        val script = HerbloreScriptFactory.getScript(bot, 2, mutableListOf(), training)
        assertInstanceOf(IdentifyHerbBotScript::class.java, script)
        assertEquals(Herb.GUAM_LEAF, (script as IdentifyHerbBotScript).herb)
        assertEquals(3, skill.staticLevel)
        assertEquals(SkillSet.experienceForLevel(3).toDouble(), skill.experience)
        val missingXp = SkillSet.experienceForLevel(3) - 100.0
        val order = inOrder(skill, bank)
        order.verify(skill).addExperience(missingXp)
        order.verify(bank).computeAmountForId(Herb.GUAM_LEAF.id)
        HerbloreScriptFactory.getScript(bot, 2, mutableListOf(), training)
        verify(skill, times(1)).addExperience(missingXp)
    }

    @ParameterizedTest @ValueSource(booleans = [true, false])
    fun emptyStockStillRaisesLevelOneBotsAndRequestsGuam(training: Boolean) = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        val skill = realHerblore(bot, 0.0)
        assertTrue(HerbloreScriptFactory.getScript(bot, 1, mutableListOf(), training).run())
        assertEquals(3, skill.staticLevel)
        assertEquals(SkillSet.experienceForLevel(3).toDouble(), skill.experience)
        verify(skill, times(1)).addExperience(SkillSet.experienceForLevel(3).toDouble())
        verify(bot.preferences).raiseWantedItemTarget(Herb.GUAM_LEAF.id, 1_000)
    }

    @ParameterizedTest @ValueSource(booleans = [true, false])
    fun establishedLevelsAndExistingExperienceArePreserved(training: Boolean) {
        val bot = InventoryProductionFixtures.bot()
        val skill = realHerblore(bot, 500.0)
        val originalLevel = skill.staticLevel
        HerbloreScriptFactory.getScript(bot, originalLevel, mutableListOf(), training)
        assertEquals(originalLevel, skill.staticLevel)
        assertEquals(500.0, skill.experience)
        verify(skill, never()).addExperience(anyDouble())
    }

    private fun realHerblore(bot: Bot, experience: Double): Skill {
        val skills = mock(SkillSet::class.java)
        `when`(skills.mob).thenReturn(bot)
        val skill = spy(Skill(SKILL_HERBLORE, skills))
        skill.experience = experience
        `when`(bot.skill(SKILL_HERBLORE)).thenReturn(skill)
        return skill
    }

    @Test fun bankBatchRespectsCapacityAndAllowsRemainingPartialStock() {
        val bot = InventoryProductionFixtures.bot()
        val script = IdentifyHerbBotScript(bot, Herb.TORSTOL, 10.minutes)
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(Herb.TORSTOL.id, 100))
        assertEquals(listOf(Item(Herb.TORSTOL.id, 28)), script.bankBatch())
        bot.bank.remove(Item(Herb.TORSTOL.id, 95))
        assertEquals(listOf(Item(Herb.TORSTOL.id, 5)), script.bankBatch())
    }

    @Test fun sharedRegistrationRestoresHerbZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = HerbData().apply {
                recipe = Herb.RANARR_WEED.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(IdentifyHerbBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(IdentifyHerbBotScript::class.java, restored)
            val snapshot = (restored as IdentifyHerbBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun ownedInventoryHerbsQualifyAndMissingStockIsRequested() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Herb.GUAM_LEAF.idItem)
        assertTrue(InventoryProductionFixtures.active(
            IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes)).onInit(false))
        bot.inventory.remove(Herb.GUAM_LEAF.idItem)
        assertFalse(InventoryProductionFixtures.active(
            IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Herb.GUAM_LEAF.id, 1_000)
    }

    @Test fun unsafeStartupAndCurrentLevelLossPreventProcessing() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Herb.GUAM_LEAF.idItem)
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        val script = InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes))
        `when`(bot.herblore.level).thenReturn(2)
        script.executeInZone()
        verify(bot.actionHandler.inventory, never()).clickItem(anyInt(), anyInt(), anyInt())
        assertTrue(script.isTerminated())
    }

    @Test fun fullUsableInventoryAvoidsBanking() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Herb.GUAM_LEAF.id, 28))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Herb.GUAM_LEAF.idItem)
        val script = InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = HerbData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Herb.GUAM_LEAF.idItem)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(IdentifyHerbBotScript(bot, script.snapshot())).onInit(true))
    }
}
