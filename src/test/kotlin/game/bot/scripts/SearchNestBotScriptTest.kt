package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.GeneralActivityCoordinator
import game.bot.scripts.SearchNestBotScript.Companion.NestData
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class SearchNestBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()

        // Invokes the existing player handler; no reward or search logic is duplicated in this fixture.
        private val gameplay by lazy {
            Class.forName("game.skill.woodcutting.searchNest.SearchNest")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
        }
        private fun search(bot: Bot, nest: Nest) {
            gameplay.javaClass.getMethod("searchNest", Player::class.java, Nest::class.java)
                .invoke(gameplay, bot, nest)
        }
    }

    @Test fun generalActivitiesSelectOnlyOwnedUnsearchedNests() {
        val bot = InventoryProductionFixtures.bot()
        assertNull(GeneralActivityCoordinator.getScript(bot))
        InventoryProductionFixtures.bank(bot, Item(5075, 100))
        assertNull(GeneralActivityCoordinator.getScript(bot))
        for (nest in Nest.VALUES) {
            InventoryProductionFixtures.bank(bot, Item(nest.id, 3))
            val selected = GeneralActivityCoordinator.getScript(bot) as SearchNestBotScript
            assertTrue(selected.isEligible())
            assertTrue(bot.bank.contains(selected.nest.id))
        }
    }

    @Test fun everyGameplayNestFitsFourteenSearchesWithOriginalRewardsAndEmptyNests() {
        for (nest in Nest.VALUES) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(nest.id, 14))
            repeat(14) { search(bot, nest) }
            assertFalse(bot.inventory.contains(nest.id))
            assertEquals(14, bot.inventory.computeAmountForId(5075))
            assertEquals(14, nest.items.sumOf { bot.inventory.computeAmountForId(it) })
            assertTrue(bot.inventory.isFull)
            verify(bot.woodcutting, never()).addExperience(anyDouble())
        }
    }

    @Test fun fullInventoryDoesNotConsumeNestAndScriptRequestsBanking() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Nest.RED_EGG_NEST.id, 28))
        search(bot, Nest.RED_EGG_NEST)
        assertEquals(28, bot.inventory.computeAmountForId(Nest.RED_EGG_NEST.id))
        assertFalse(bot.inventory.contains(5075))
        val script = InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.RED_EGG_NEST, 10.minutes))
        assertTrue(script.onBankRequested(false))
        script.executeInZone()
        verify(bot.actionHandler.inventory, never()).clickItem(anyInt(), anyInt(), anyInt())
    }

    @Test fun batchesReserveWorstCaseRewardSpaceAndLimitAvailableStock() {
        val bot = InventoryProductionFixtures.bot()
        val script = SearchNestBotScript(bot, Nest.RINGS_NEST, 10.minutes)
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(Nest.RINGS_NEST.id, 3))
        assertEquals(listOf(Item(Nest.RINGS_NEST.id, 3)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(Nest.RINGS_NEST.id, 100))
        assertEquals(listOf(Item(Nest.RINGS_NEST.id, 14)), script.bankBatch())
    }

    @Test fun firstItemOptionInvokesGameplayHandlerAndVerifiedProgressAllowsAnotherSearch() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation ->
            scheduler.invoke(invocation.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(Nest.RINGS_NEST.id, 2))
            `when`(bot.personality.isDextrous).thenReturn(true)
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            `when`(bot.actionHandler.inventory.clickItem(1, Nest.RINGS_NEST.id)).thenAnswer {
                search(bot, Nest.RINGS_NEST)
                true
            }
            val script = InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.RINGS_NEST, 10.minutes))
            assertTrue(withTimeout(5_000) { script.executeInZone() })
            assertEquals(1, bot.inventory.computeAmountForId(Nest.RINGS_NEST.id))
            assertEquals(1, bot.inventory.computeAmountForId(5075))
            assertEquals(0, script.snapshot().failures)
            assertFalse(script.onBankRequested(false))
            assertTrue(withTimeout(5_000) { script.executeInZone() })
            assertTrue(script.onBankRequested(false))
            verify(bot.actionHandler.inventory, times(2)).clickItem(1, Nest.RINGS_NEST.id)
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun exhaustedStockStopsWithoutDemandAndUnsafeBotsCannotStart() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        assertFalse(InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.SEEDS_NEST, 10.minutes))
            .onInit(false))
        verifyNoInteractions(bot.preferences)
        InventoryProductionFixtures.bank(bot, Item(Nest.SEEDS_NEST.id))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.SEEDS_NEST, 10.minutes))
                .onInit(false))
        }
    }

    @Test fun failedSearchAndBankingBudgetsSurviveSnapshots() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Nest.SEEDS_NEST.id))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.SEEDS_NEST, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = NestData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(SearchNestBotScript(bot, data)).onInit(true))
        val banking = InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.SEEDS_NEST, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        assertEquals(3, banking.snapshot().bankFailures)
        assertFalse(InventoryProductionFixtures.active(SearchNestBotScript(bot, banking.snapshot())).onInit(true))
    }

    @Test fun bankingUsesUnnotedModeAndRejectsUnverifiedWithdrawals() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(Nest.BLUE_EGG_NEST.id, 14))
        val banking = bot.actionHandler.banking
        `when`(banking.withdrawAll(anyList())).thenReturn(true)
        val script = InventoryProductionFixtures.active(SearchNestBotScript(bot, Nest.BLUE_EGG_NEST, 10.minutes))
        assertTrue(script.onInit(false))
        script.onBankOpen(false)
        verify(banking).clickBankingMode(false)
        assertTrue(script.isTerminated())
    }

    @Test fun centralRegistrationRestoresNestDurationZonesAndRetryState() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = NestData().apply {
                recipe = Nest.GREEN_EGG_NEST.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(SearchNestBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data) as SearchNestBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}