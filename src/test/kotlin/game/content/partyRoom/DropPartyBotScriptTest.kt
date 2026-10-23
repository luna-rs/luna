package game.content.partyRoom

import api.bot.script.InventoryProductionFixtures
import com.google.gson.JsonObject
import game.bot.scripts.DropPartyBotScript
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Verifies that stale or expired party visits cannot resume against a different party.
 *
 * @author lare96
 */
class DropPartyBotScriptTest {
    /**
     * Initializes the native fixtures and resets the state used by each test.
     */
    @BeforeEach
    fun setup() {
        InventoryProductionFixtures.initialize()
    }

    /**
     * Verifies that expired attendance completes before travelling or changing inventory.
     */
    @Test
    fun expiredAttendanceCompletesBeforeTravellingOrChangingInventory() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val script = DropPartyBotScript(bot, 123L, 0L)
        assertTrue(script.init(true))
        verifyNoInteractions(bot.actionHandler)
        assertEquals(0, bot.inventory.size())
    }

    /**
     * Verifies that stale party attendance completes and lets the stack resume its parent.
     */
    @Test
    fun stalePartyAttendanceCompletesAndLetsTheStackResumeItsParent() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val script = DropPartyBotScript(bot, -1L, System.currentTimeMillis() + 60_000)
        assertTrue(script.init(true))
        verifyNoInteractions(bot.actionHandler)
    }

    /**
     * Verifies that saved attendance preserves both party identity and its original deadline.
     */
    @Test
    fun snapshotPreservesPartyIdentityAndAttendanceDeadline() {
        val bot = InventoryProductionFixtures.bot()
        val script = DropPartyBotScript(bot, 123L, 456L)
        val json = JsonObject()
        script.snapshot().save(json)
        val restored = DropPartyBotScript.PartyData().also { it.load(json) }
        assertEquals(123L, restored.partyId)
        assertEquals(456L, restored.deadline)
        assertTrue(DropPartyBotScript(bot, restored).ended())
    }
}
