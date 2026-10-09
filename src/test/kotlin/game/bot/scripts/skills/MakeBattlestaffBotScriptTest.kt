package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.MakeBattlestaffBotScript.Companion.BattlestaffData
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.battlestaffCrafting.MakeBattlestaffActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakeBattlestaffBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun pairedMaterialsUseTheSmallerBankStock() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Battlestaff.BATTLESTAFF, 100), Item(Battlestaff.WATER.orb, 3))
        val script = MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes)
        assertEquals(listOf(Item(Battlestaff.BATTLESTAFF, 3), Item(Battlestaff.WATER.orb, 3)), script.bankBatch())
        assertInstanceOf(MakeBattlestaffBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 99, true))
        assertInstanceOf(MakeBattlestaffBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 99, false))
    }

    @ParameterizedTest @EnumSource(Battlestaff::class)
    fun playerActionAssemblesTheExistingRecipe(recipe: Battlestaff) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Battlestaff.BATTLESTAFF_ITEM, recipe.orbItem)
        InventoryProductionFixtures.execute(bot, MakeBattlestaffActionItem(bot, recipe, 1))
        assertTrue(bot.inventory.contains(recipe.staff))
        assertFalse(bot.inventory.contains(Battlestaff.BATTLESTAFF))
        assertFalse(bot.inventory.contains(recipe.orb))
        verify(bot.crafting).addExperience(recipe.exp)
    }

    @Test fun batchesRespectInventoryCapacityAndRequireBothInputs() {
        val bot = InventoryProductionFixtures.bot()
        val script = MakeBattlestaffBotScript(bot, Battlestaff.AIR, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(Battlestaff.BATTLESTAFF, 100))
        assertTrue(script.bankBatch().isEmpty())
        assertFalse(script.isEligible())
        InventoryProductionFixtures.bank(bot, Item(Battlestaff.AIR.orb, 100))
        assertEquals(listOf(Item(Battlestaff.BATTLESTAFF, 14), Item(Battlestaff.AIR.orb, 14)), script.bankBatch())
        `when`(bot.crafting.staticLevel).thenReturn(Battlestaff.AIR.level - 1)
        assertFalse(script.isEligible())
        assertNull(CraftingScriptFactory.getProductionScript(bot, Battlestaff.AIR.level - 1, true))
    }

    @Test fun sharedRegistrationRestoresRecipeZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = BattlestaffData().apply {
                recipe = Battlestaff.FIRE.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(MakeBattlestaffBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(MakeBattlestaffBotScript::class.java, restored)
            val snapshot = (restored as MakeBattlestaffBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun missingOrbIsRequestedButOwnedInventoryInputsQualify() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Battlestaff.BATTLESTAFF_ITEM, Battlestaff.WATER.orbItem)
        assertTrue(InventoryProductionFixtures.active(
            MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes)).onInit(false))
        bot.inventory.remove(Battlestaff.WATER.orb)
        val script = InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Battlestaff.WATER.orb, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(Battlestaff.BATTLESTAFF, 1_000)
    }

    @Test fun startupRejectsDeathCombatAndInsufficientLevel() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Battlestaff.BATTLESTAFF_ITEM, Battlestaff.WATER.orbItem)
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.crafting.staticLevel).thenReturn(Battlestaff.WATER.level - 1)
        assertFalse(InventoryProductionFixtures.active(
            MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes)).onInit(false))
    }

    @Test fun fullProcessableInventoryAvoidsBankingAndCurrentLevelLossStopsProduction() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Battlestaff.BATTLESTAFF, 14), Item(Battlestaff.WATER.orb, 14))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes))
        assertFalse(script.onBankRequested(false))
        `when`(bot.crafting.level).thenReturn(Battlestaff.WATER.level - 1)
        script.executeInZone()
        assertTrue(script.isTerminated())
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Battlestaff.BATTLESTAFF_ITEM, Battlestaff.WATER.orbItem)
        val script = InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = BattlestaffData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Battlestaff.BATTLESTAFF_ITEM, Battlestaff.WATER.orbItem)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, Battlestaff.WATER, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakeBattlestaffBotScript(bot, script.snapshot())).onInit(true))
    }
}
