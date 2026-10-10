package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.HerbloreScriptFactory
import game.bot.scripts.skills.MakeUnfPotionBotScript.Companion.UnfPotionData
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.skill.herblore.makeUnfPotion.MakeUnfActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.bot.brain.BotPreference
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakeUnfPotionBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun preparationRespectsLevelsAndNeverClaimsToTrainHerblore() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(UnfPotion.VIAL_OF_WATER, 100), Item(UnfPotion.GUAM.herb, 5))
        assertNull(HerbloreScriptFactory.getProductionScript(bot, 99, true))
        assertInstanceOf(MakeUnfPotionBotScript::class.java,
            HerbloreScriptFactory.getProductionScript(bot, 99, false))
        val script = MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)
        assertEquals(listOf(Item(UnfPotion.GUAM.herb, 5), Item(UnfPotion.VIAL_OF_WATER, 5)), script.bankBatch())
        `when`(bot.herblore.staticLevel).thenReturn(2)
        assertFalse(script.isEligible())
    }

    @ParameterizedTest @EnumSource(UnfPotion::class)
    fun playerActionMakesUnfinishedPotionsWithoutExperience(potion: UnfPotion) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, potion.herbItem, Item(UnfPotion.VIAL_OF_WATER))
        InventoryProductionFixtures.execute(bot, MakeUnfActionItem(bot, potion, 1))
        assertTrue(bot.inventory.contains(potion.id))
        assertFalse(bot.inventory.contains(potion.herb))
        assertFalse(bot.inventory.contains(UnfPotion.VIAL_OF_WATER))
        verify(bot.herblore, never()).addExperience(anyDouble())
    }

    @Test fun batchesRequireBothInputsAndRespectInventoryCapacity() {
        val bot = InventoryProductionFixtures.bot()
        val script = MakeUnfPotionBotScript(bot, UnfPotion.TORSTOL, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(UnfPotion.TORSTOL.herb, 100))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(UnfPotion.VIAL_OF_WATER, 100))
        assertEquals(listOf(Item(UnfPotion.TORSTOL.herb, 14), Item(UnfPotion.VIAL_OF_WATER, 14)), script.bankBatch())
        bot.bank.remove(Item(UnfPotion.VIAL_OF_WATER, 97))
        assertEquals(listOf(Item(UnfPotion.TORSTOL.herb, 3), Item(UnfPotion.VIAL_OF_WATER, 3)), script.bankBatch())
    }

    @Test fun sharedRegistrationRestoresRecipeZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = UnfPotionData().apply {
                recipe = UnfPotion.RANARR.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(MakeUnfPotionBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(MakeUnfPotionBotScript::class.java, restored)
            val snapshot = (restored as MakeUnfPotionBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun inventoryInputsQualifyAndMissingWaterIsRequested() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, UnfPotion.GUAM.herbItem, Item(UnfPotion.VIAL_OF_WATER))
        assertTrue(InventoryProductionFixtures.active(
            MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        bot.inventory.remove(UnfPotion.VIAL_OF_WATER)
        assertFalse(InventoryProductionFixtures.active(
            MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(UnfPotion.VIAL_OF_WATER, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(UnfPotion.GUAM.herb, 1_000)
    }

    @Test fun supplyRequestsRaiseTotalTargetsAndPreserveLargerTargetsAndMetadata() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        val herb = WantedItemDefinition(UnfPotion.GUAM.herb, 100, 500, SKILL_HERBLORE, 50)
        val water = WantedItemDefinition(UnfPotion.VIAL_OF_WATER, 250, 2_000, SKILL_HERBLORE, 75)
        val preferences = BotPreference(mutableMapOf(), mutableSetOf(),
            mutableMapOf(herb.id() to herb, water.id() to water), mutableSetOf(), mutableMapOf())
        `when`(bot.preferences).thenReturn(preferences)

        repeat(2) {
            assertFalse(InventoryProductionFixtures.active(
                MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        }

        val requestedHerb = preferences.getWantedItem(herb.id())
        assertEquals(1_000, requestedHerb.target())
        assertEquals(herb.min(), requestedHerb.min())
        assertEquals(herb.skill(), requestedHerb.skill())
        assertEquals(herb.maxLevel(), requestedHerb.maxLevel())
        assertSame(water, preferences.getWantedItem(water.id()))
    }

    @Test fun unsafeStartupAndInsufficientLevelsPreventProcessing() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, UnfPotion.GUAM.herbItem, Item(UnfPotion.VIAL_OF_WATER))
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.herblore.staticLevel).thenReturn(2)
        assertFalse(InventoryProductionFixtures.active(
            MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes)).onInit(false))
        `when`(bot.herblore.staticLevel).thenReturn(99)
        `when`(bot.herblore.level).thenReturn(2)
        val script = InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes))
        script.executeInZone()
        assertTrue(script.isTerminated())
    }

    @Test fun fullProcessableInventoryDoesNotRequestBanking() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(UnfPotion.GUAM.herb, 14), Item(UnfPotion.VIAL_OF_WATER, 14))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, UnfPotion.GUAM.herbItem, Item(UnfPotion.VIAL_OF_WATER))
        val script = InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = UnfPotionData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, UnfPotion.GUAM.herbItem, Item(UnfPotion.VIAL_OF_WATER))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, UnfPotion.GUAM, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakeUnfPotionBotScript(bot, script.snapshot())).onInit(true))
    }
}
