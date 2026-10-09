package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.HerbloreScriptFactory
import game.bot.scripts.skills.GrindIngredientBotScript.Companion.IngredientData
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.grindIngredient.GrindActionItem
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

class GrindIngredientBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun grindingIsPreparationRatherThanExperienceTraining() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Ingredient.PESTLE_AND_MORTAR), Item(Ingredient.CHOCOLATE_DUST.id, 100))
        assertNull(HerbloreScriptFactory.getProductionScript(bot, 99, true))
        val script = HerbloreScriptFactory.getProductionScript(bot, 99, false)
        assertInstanceOf(GrindIngredientBotScript::class.java, script)
        assertEquals(listOf(Item(Ingredient.PESTLE_AND_MORTAR), Item(Ingredient.CHOCOLATE_DUST.id, 27)),
            (script as GrindIngredientBotScript).bankBatch())
    }

    @ParameterizedTest @EnumSource(Ingredient::class)
    fun playerActionKeepsTheMortarAndConsumesTheIngredient(ingredient: Ingredient) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Ingredient.PESTLE_AND_MORTAR), ingredient.oldItem)
        InventoryProductionFixtures.execute(bot, GrindActionItem(bot, ingredient, 1))
        assertTrue(bot.inventory.contains(ingredient.newId))
        assertTrue(bot.inventory.contains(Ingredient.PESTLE_AND_MORTAR))
        assertFalse(bot.inventory.contains(ingredient.id))
        verify(bot.herblore, never()).addExperience(anyDouble())
    }

    @Test fun partialStockAndMissingToolsLimitBankBatches() {
        val bot = InventoryProductionFixtures.bot()
        val script = GrindIngredientBotScript(bot, Ingredient.UNICORN_HORN_DUST, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(Ingredient.UNICORN_HORN_DUST.id, 3))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(Ingredient.PESTLE_AND_MORTAR))
        assertEquals(listOf(Item(Ingredient.PESTLE_AND_MORTAR), Item(Ingredient.UNICORN_HORN_DUST.id, 3)),
            script.bankBatch())
        bot.bank.remove(Item(Ingredient.UNICORN_HORN_DUST.id, 3))
        assertTrue(script.bankBatch().isEmpty())
    }

    @Test fun sharedRegistrationRestoresRecipeZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = IngredientData().apply {
                recipe = Ingredient.CRUSHED_NEST.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(GrindIngredientBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(GrindIngredientBotScript::class.java, restored)
            val snapshot = (restored as GrindIngredientBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun ownedInventorySuppliesQualifyAndMissingMortarIsRequested() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Ingredient.PESTLE_AND_MORTAR), Ingredient.CHOCOLATE_DUST.oldItem)
        assertTrue(InventoryProductionFixtures.active(
            GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes)).onInit(false))
        bot.inventory.remove(Ingredient.PESTLE_AND_MORTAR)
        assertFalse(InventoryProductionFixtures.active(
            GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Ingredient.PESTLE_AND_MORTAR, 3)
        verify(bot.preferences, never()).raiseWantedItemTarget(Ingredient.CHOCOLATE_DUST.id, 1_000)
    }

    @Test fun deathCombatAndLocksRejectStartup() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Ingredient.PESTLE_AND_MORTAR), Ingredient.CHOCOLATE_DUST.oldItem)
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(
            GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(
            GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes)).onInit(false))
    }

    @Test fun fullProcessableInventoryDoesNotRequestBanking() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Ingredient.PESTLE_AND_MORTAR), Item(Ingredient.CHOCOLATE_DUST.id, 27))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Ingredient.PESTLE_AND_MORTAR), Ingredient.CHOCOLATE_DUST.oldItem)
        val script = InventoryProductionFixtures.active(GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = IngredientData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(GrindIngredientBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Ingredient.PESTLE_AND_MORTAR), Ingredient.CHOCOLATE_DUST.oldItem)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(GrindIngredientBotScript(bot, Ingredient.CHOCOLATE_DUST, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(GrindIngredientBotScript(bot, script.snapshot())).onInit(true))
    }
}
