package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.StringJewelleryBotScript.Companion.JewelleryData
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.crafting.jewelleryMaking.StringJewelleryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class StringJewelleryBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun pairedMaterialsUseTheSmallerBankStock() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1673, 100), Item(1759, 3))
        val script = StringJewelleryBotScript(bot, 1673, 10.minutes)
        assertEquals(listOf(Item(1673, 3), Item(1759, 3)), script.bankBatch())
        assertInstanceOf(StringJewelleryBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 99, true))
        assertInstanceOf(StringJewelleryBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 99, false))
    }

    @ParameterizedTest
    @CsvSource("1673,1692", "1675,1694", "1677,1696", "1679,1698", "1681,1700",
        "1683,1702", "6579,6581", "1714,1716", "1720,1722")
    fun supportedRecipesUseExistingConversionsAtLevelOne(unstrung: Int, strung: Int) {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.crafting.staticLevel).thenReturn(1)
        `when`(bot.crafting.level).thenReturn(1)
        InventoryProductionFixtures.inventory(bot, Item(unstrung), Item(1759))
        val script = CraftingScriptFactory.getProductionScript(bot, 1, true)
        assertInstanceOf(StringJewelleryBotScript::class.java, script)
        assertEquals(unstrung, (script as StringJewelleryBotScript).unstrung)
        InventoryProductionFixtures.execute(bot, StringJewelleryAction(bot, 1, 1759, unstrung, strung))
        assertTrue(bot.inventory.contains(strung))
        assertFalse(bot.inventory.contains(unstrung))
        assertFalse(bot.inventory.contains(1759))
        verify(bot.crafting).addExperience(4.0)
    }

    @Test fun unsupportedJewelleryIsRejectedAndMissingWoolPreventsFactorySelection() {
        val bot = InventoryProductionFixtures.bot()
        assertEquals(9, StringJewelleryBotScript.UNSTRUNG_IDS.size)
        for (id in listOf(1635, 1654, 2961, 5525)) {
            assertThrows(IllegalArgumentException::class.java) { StringJewelleryBotScript(bot, id, 10.minutes) }
        }
        InventoryProductionFixtures.bank(bot, Item(GoldJewelleryTable.AMULETS.jewelleryItems.first().id))
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, true))
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, false))
    }

    @Test fun batchesRespectInventoryCapacityAndRequireBothInputs() {
        val bot = InventoryProductionFixtures.bot()
        val script = StringJewelleryBotScript(bot, 1673, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(1673, 100))
        assertTrue(script.bankBatch().isEmpty())
        assertFalse(script.isEligible())
        InventoryProductionFixtures.bank(bot, Item(1759, 100))
        assertEquals(listOf(Item(1673, 14), Item(1759, 14)), script.bankBatch())
        `when`(bot.crafting.staticLevel).thenReturn(0)
        assertFalse(script.isEligible())
        assertNull(CraftingScriptFactory.getProductionScript(bot, 0, true))
    }

    @Test fun sharedRegistrationRestoresRecipeZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = JewelleryData().apply {
                recipe = "1673"
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(StringJewelleryBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(StringJewelleryBotScript::class.java, restored)
            val snapshot = (restored as StringJewelleryBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun missingWoolIsRequestedButOwnedInventoryInputsQualify() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1673), Item(1759))
        assertTrue(InventoryProductionFixtures.active(
            StringJewelleryBotScript(bot, 1673, 10.minutes)).onInit(false))
        bot.inventory.remove(1759)
        val script = InventoryProductionFixtures.active(StringJewelleryBotScript(bot, 1673, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(1759, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(1673, 1_000)
    }

    @Test fun startupRejectsDeathCombatAndInsufficientLevel() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1673), Item(1759))
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            StringJewelleryBotScript(bot, 1673, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            StringJewelleryBotScript(bot, 1673, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.crafting.staticLevel).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            StringJewelleryBotScript(bot, 1673, 10.minutes)).onInit(false))
    }

    @Test fun fullProcessableInventoryAvoidsBankingAndCurrentLevelLossStopsProduction() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1673, 14), Item(1759, 14))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(StringJewelleryBotScript(bot, 1673, 10.minutes))
        assertFalse(script.onBankRequested(false))
        `when`(bot.crafting.level).thenReturn(0)
        script.executeInZone()
        assertTrue(script.isTerminated())
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1673), Item(1759))
        val script = InventoryProductionFixtures.active(StringJewelleryBotScript(bot, 1673, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = JewelleryData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(StringJewelleryBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1673), Item(1759))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(StringJewelleryBotScript(bot, 1673, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(StringJewelleryBotScript(bot, script.snapshot())).onInit(true))
    }
}
