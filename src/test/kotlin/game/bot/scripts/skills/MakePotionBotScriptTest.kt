package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.HerbloreScriptFactory
import game.bot.scripts.skills.MakePotionBotScript.Companion.PotionData
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.herblore.makePotion.MakePotionActionItem
import io.luna.game.action.ActionState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakePotionBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun allRecipesRequireTheirLevelAndOwnedInputsAndCanBeSelectedForBothModes() {
        for (potion in FinishedPotion.entries) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.herblore.staticLevel).thenReturn(potion.level - 1)
            InventoryProductionFixtures.bank(bot, Item(potion.unf, 100), Item(potion.secondary, 3))
            val script = MakePotionBotScript(bot, potion, 10.minutes)
            assertFalse(script.isEligible(), potion.name)
            assertNull(HerbloreScriptFactory.getProductionScript(bot, potion.level - 1, true))
            `when`(bot.herblore.staticLevel).thenReturn(potion.level)
            assertTrue(script.isEligible(), potion.name)
            assertEquals(listOf(Item(potion.unf, 3), Item(potion.secondary, 3)), script.bankBatch())
            for (training in listOf(false, true)) {
                val selected = HerbloreScriptFactory.getProductionScript(bot, potion.level, training)
                assertInstanceOf(MakePotionBotScript::class.java, selected)
                assertEquals(potion, (selected as MakePotionBotScript).potion)
            }
            InventoryProductionFixtures.bank(bot, Item(potion.secondary, 97))
            assertEquals(listOf(Item(potion.unf, 14), Item(potion.secondary, 14)), script.bankBatch())
        }
    }

    @Test fun missingInputsRequestBulkTargetsAndOwnedInventoryQualifies() = runBlocking<Unit> {
        val potion = FinishedPotion.ATTACK_POTION
        for (missing in listOf(potion.unf, potion.secondary)) {
            val bot = InventoryProductionFixtures.bot()
            val owned = if (missing == potion.unf) potion.secondaryItem else potion.unfItem
            InventoryProductionFixtures.inventory(bot, owned)
            val script = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
            assertFalse(script.onInit(false))
            assertTrue(script.bankBatch().isEmpty())
            verify(bot.preferences).raiseWantedItemTarget(missing, 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(owned.id, 1_000)
            InventoryProductionFixtures.inventory(bot, Item(missing))
            assertTrue(MakePotionBotScript(bot, potion, 10.minutes).isEligible())
        }
    }

    @Test fun unsafeStartupAndCurrentLevelDrainPreventProduction() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val potion = FinishedPotion.PRAYER_POTION
        InventoryProductionFixtures.inventory(bot, potion.unfItem, potion.secondaryItem)
        for (state in 0..3) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            `when`(bot.herblore.staticLevel).thenReturn(if (state == 3) potion.level - 1 else 99)
            assertFalse(InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes)).onInit(false))
        }
        `when`(bot.herblore.staticLevel).thenReturn(99)
        `when`(bot.herblore.level).thenReturn(potion.level - 1)
        val script = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
        assertTrue(script.executeInZone())
        assertTrue(script.isTerminated())
        assertTrue(bot.inventory.contains(potion.unf))
        assertTrue(bot.inventory.contains(potion.secondary))
        verify(bot.herblore, never()).addExperience(anyDouble())
    }

    @Test fun fullProcessableInventoryAvoidsPrematureBankingAndMissingInputsRequestIt() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val potion = FinishedPotion.SARADOMIN_BREW
        InventoryProductionFixtures.inventory(bot, Item(potion.unf, 14), Item(potion.secondary, 14))
        val script = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
        assertTrue(bot.inventory.isFull)
        assertFalse(script.onBankRequested(false))
        bot.inventory.remove(Item(potion.secondary, 14))
        assertTrue(script.executeInZone())
        assertTrue(script.onBankRequested(false))
    }

    @Test fun normalInteractionUsesPlayerActionAndConfirmsInputConsumption() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (potion in listOf(FinishedPotion.ATTACK_POTION, FinishedPotion.ANTIPOISON,
                                 FinishedPotion.ZAMORAK_BREW, FinishedPotion.SARADOMIN_BREW)) {
                val bot = InventoryProductionFixtures.bot()
                InventoryProductionFixtures.inventory(bot, potion.unfItem, potion.secondaryItem)
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(potion.unf).onItem(potion.secondary)).thenReturn(true)
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    InventoryProductionFixtures.execute(bot, MakePotionActionItem(bot, potion, 1))
                    null
                }.`when`(widgets).clickMakeItem(0, 1)
                val script = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertTrue(bot.inventory.contains(potion.id))
                assertFalse(bot.inventory.contains(potion.unf))
                assertFalse(bot.inventory.contains(potion.secondary))
                assertEquals(0, script.snapshot().failures)
                verify(widgets).clickMakeItem(0, 1)
                verify(bot.herblore).addExperience(potion.exp)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun unresolvedBankingAndInteractionBudgetsSurviveSnapshots() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val potion = FinishedPotion.SUPER_RESTORE
        InventoryProductionFixtures.bank(bot, potion.unfItem, potion.secondaryItem)
        val banking = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        val json = JsonObject()
        banking.snapshot().save(json)
        val data = PotionData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakePotionBotScript(bot, data)).onInit(true))
        InventoryProductionFixtures.inventory(bot, potion.unfItem, potion.secondaryItem)
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        val failed = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
        repeat(3) { failed.executeInZone() }
        assertEquals(3, failed.snapshot().failures)
        assertTrue(failed.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakePotionBotScript(bot, failed.snapshot())).onInit(true))
    }

    @Test fun bankingUsesUnnotedModeAndStopsOnIncompleteWithdrawal() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val potion = FinishedPotion.ENERGY_POTION
        InventoryProductionFixtures.bank(bot, potion.unfItem, potion.secondaryItem)
        val script = InventoryProductionFixtures.active(MakePotionBotScript(bot, potion, 10.minutes))
        assertTrue(script.onInit(false))
        `when`(bot.actionHandler.banking.withdrawAll(listOf(potion.unfItem, potion.secondaryItem))).thenReturn(true)
        script.onBankOpen(false)
        verify(bot.actionHandler.banking).clickBankingMode(false)
        assertTrue(script.isTerminated())
    }

    @Test fun sharedRegistryRestoresRecipeZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = PotionData().apply {
                recipe = FinishedPotion.SUPER_STRENGTH.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(MakePotionBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data) as MakePotionBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }

    @Test fun finishingInterruptsOnlyTheConfiguredPotionAction() = runBlocking<Unit> {
        for (sameRecipe in listOf(false, true)) {
            val bot = InventoryProductionFixtures.bot()
            val action = MakePotionActionItem(bot, FinishedPotion.PRAYER_POTION, 1)
            bot.actions.submit(action)
            val recipe = if (sameRecipe) FinishedPotion.PRAYER_POTION else FinishedPotion.ATTACK_POTION
            MakePotionBotScript(bot, recipe, 10.minutes).finish()
            assertEquals(if (sameRecipe) ActionState.INTERRUPTED else ActionState.PROCESSING, action.state)
        }
    }
}
