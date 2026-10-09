package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import game.bot.scripts.skills.CutGemBotScript.Companion.CutGemData
import kotlinx.coroutines.runBlocking
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.skill.crafting.gemCutting.Gem
import game.skill.crafting.gemCutting.CutGemActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CutGemBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun preciousGemsUseOwnedToolsAndDirectFactorySelection() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Gem.CHISEL), Item(Gem.SAPPHIRE.uncut, 3))
        val selected = CraftingScriptFactory.getProductionScript(bot, 99, true)
        assertInstanceOf(CutGemBotScript::class.java, selected)
        val script = selected as CutGemBotScript
        assertEquals(Gem.SAPPHIRE, script.gem)
        assertEquals(listOf(Item(Gem.CHISEL), Item(Gem.SAPPHIRE.uncut, 3)), script.bankBatch())
        assertThrows(IllegalArgumentException::class.java) { CutGemBotScript(bot, Gem.OPAL, 10.minutes) }
    }

    @Test fun sharedRegistrationRestoresTheSavedRecipeAndBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val bot = InventoryProductionFixtures.bot()
            val data = CutGemData().apply {
                recipe = Gem.RUBY.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(CutGemBotScript::class.qualifiedName, bot, data)
            assertInstanceOf(CutGemBotScript::class.java, restored)
            val snapshot = (restored as CutGemBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun playerActionConsumesOneGemAndAwardsItsExperience() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Gem.CHISEL), Gem.SAPPHIRE.uncutItem)
        InventoryProductionFixtures.execute(bot, CutGemActionItem(bot, Gem.SAPPHIRE, 1))
        assertFalse(bot.inventory.contains(Gem.SAPPHIRE.uncut))
        assertTrue(bot.inventory.contains(Gem.SAPPHIRE.cut))
        assertTrue(bot.inventory.contains(Gem.CHISEL))
        verify(bot.crafting).addExperience(Gem.SAPPHIRE.exp)
    }

    @Test fun missingToolsAreRequestedAndInventorySuppliesQualify() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Gem.CHISEL), Gem.SAPPHIRE.uncutItem)
        assertTrue(InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes)).onInit(false))
        bot.inventory.remove(Gem.CHISEL)
        val script = InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.preferences).addWantedItem(Gem.CHISEL, 3)
    }

    @Test fun deathCombatAndInsufficientLevelsRejectStartup() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Gem.CHISEL), Gem.SAPPHIRE.uncutItem)
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.crafting.staticLevel).thenReturn(19)
        assertFalse(InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes)).onInit(false))
    }

    @Test fun fullUsableInventoryDoesNotRequestAnotherBankTrip() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Gem.CHISEL), Item(Gem.SAPPHIRE.uncut, 27))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun bankFailureBudgetAndRecipeStateSurviveSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Gem.CHISEL), Gem.SAPPHIRE.uncutItem)
        val script = InventoryProductionFixtures.active(
            CutGemBotScript(bot, Gem.SAPPHIRE, 7.minutes, mutableListOf(SubZone.DRAYNOR_MAIN)))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = CutGemData().apply { load(json) }
        assertEquals("SAPPHIRE", data.recipe)
        assertEquals(7.minutes, data.duration)
        assertEquals(listOf(SubZone.DRAYNOR_MAIN), data.zones)
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(CutGemBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Gem.CHISEL), Gem.SAPPHIRE.uncutItem)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(CutGemBotScript(bot, Gem.SAPPHIRE, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(CutGemBotScript(bot, script.snapshot())).onInit(true))
    }
}
