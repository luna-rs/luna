package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.gson.JsonObject
import game.bot.scripts.OpenCrystalChestBotScript.Companion.ChestData
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class OpenCrystalChestBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun successfulCycleUsesOneKeyAndRequestsLootBanking() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val position = Position(2918, 3450)
        val chest = mock(GameObject::class.java)
        InventoryProductionFixtures.inventory(bot, Item(989))
        `when`(world.locator.findObjectsOnTile(eq(position), any())).thenReturn(setOf(chest))
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(true)
        `when`(bot.actionHandler.banking.withdrawAll(anyList())).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(bot.actionHandler.inventory.useItem(989).onObject(chest)).thenAnswer {
            bot.inventory.remove(989)
            true
        }
        val runTask = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation ->
            runTask.invoke(invocation.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            assertTrue(withTimeout(2_000) { OpenCrystalChestBotScript(bot, position, 10.minutes).run() })
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
        verify(bot.actionHandler.banking).withdrawAll(listOf(Item(989)))
        verify(bot.actionHandler.inventory.useItem(989)).onObject(chest)
        verify(bot.actionHandler.banking, times(2)).travelToBankDepositAll()
    }

    @Test fun failedBankingIsBoundedAndSavedBudgetCannotBeReset() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(false)
        InventoryProductionFixtures.bank(bot, Item(989))
        val script = OpenCrystalChestBotScript(bot, Position(2918, 3450), 10.minutes)
        repeat(2) { assertFalse(script.run()) }
        assertTrue(script.run())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = ChestData().apply { load(json) }
        assertEquals(3, data.failures)
        clearInvocations(bot.actionHandler.banking)
        assertTrue(OpenCrystalChestBotScript(bot, data).run())
        verify(bot.actionHandler.banking, never()).travelToBankDepositAll()
    }

    @Test fun noKeysAndUnsafeBotsDoNotInteract() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val script = OpenCrystalChestBotScript(bot, Position(2918, 3450), 10.minutes)
        assertTrue(script.run())
        InventoryProductionFixtures.bank(bot, Item(989))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(script.run())
        verify(bot.actionHandler.banking, never()).travelToBankDepositAll()
    }

    @Test fun sharedRegistrationRestoresChestDestinationRemainingTimeAndRetryState() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = ChestData().apply {
                position = Position(2918, 3450)
                duration = 7.minutes
                failures = 2
            }
            val restored = registry.loadScript(OpenCrystalChestBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data) as OpenCrystalChestBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.position, snapshot.position)
            assertTrue(snapshot.duration <= data.duration && snapshot.duration > 6.minutes)
            assertEquals(data.failures, snapshot.failures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }
}
