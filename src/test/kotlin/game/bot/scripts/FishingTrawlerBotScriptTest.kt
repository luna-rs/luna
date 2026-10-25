package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.content.fishingTrawler.FishingTrawler
import game.content.fishingTrawler.FishingTrawler.unclaimedCatch
import io.luna.game.model.item.Item
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks trawler dispatch prerequisites, persisted attempts and interruption cleanup.
 *
 * @author lare96
 */
class FishingTrawlerBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private fun bot() = InventoryProductionFixtures.bot().also {
        `when`(it.asPlr()).thenReturn(it)
        it.wildernessLevel = 0
    }

    @Test fun selectionRequiresFishingSuppliesAndSafeState() {
        val bot = bot()
        `when`(bot.fishing.level).thenReturn(15)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
        InventoryProductionFixtures.bank(bot, Item(1941), Item(954), Item(583))
        assertTrue(FishingTrawlerBotScript.eligible(bot))
        `when`(bot.fishing.level).thenReturn(14)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
        `when`(bot.fishing.level).thenReturn(15)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
    }

    @Test fun earnedCatchCanBeCollectedWithoutSupplies() {
        val bot = bot()
        `when`(bot.fishing.level).thenReturn(15)
        bot.unclaimedCatch.add(317)
        assertTrue(FishingTrawlerBotScript.eligible(bot))
    }

    @Test fun loadoutCombinesSplitSuppliesCapsRopesAndAcceptsAFullBucket() {
        val bot = bot()
        bot.inventory.add(Item(1941, 5))
        bot.inventory.add(Item(954, 3))
        InventoryProductionFixtures.bank(bot, Item(1941, 250), Item(954, 30), Item(585))
        val supplies = FishingTrawlerBotScript.supplies(bot)!!.associate { it.id to it.amount }
        assertEquals(mapOf(1941 to 200, 954 to 20, 585 to 1), supplies)
        assertEquals(5, bot.inventory.computeAmountForId(1941))
        assertEquals(250, bot.bank.computeAmountForId(1941))
    }

    @Test fun expiredAttemptExitsWithoutBoarding() = runBlocking {
        val bot = bot()
        assertTrue(FishingTrawlerBotScript(bot, false, 1).init(true))
        verify(bot, never()).move(any())
    }

    @Test fun restoredAttemptDoesNotJoinAnotherVoyage() = runBlocking {
        val bot = bot()
        val original = FishingTrawlerBotScript(bot, true, System.currentTimeMillis() + 60_000)
        val encoded = JsonObject().also { original.snapshot().save(it) }
        val restored = FishingTrawlerBotScript.VoyageData().also { it.load(encoded) }
        assertTrue(restored.boarded)
        assertEquals(encoded.get("deadline").asLong, restored.deadline)
        assertTrue(FishingTrawlerBotScript(bot, restored).init(true))
        verify(bot, never()).move(any())
    }

    @Test fun interruptionReturnsStrandedBotToShoreAndCancelsMovement() {
        val bot = bot()
        `when`(bot.position).thenReturn(FishingTrawler.START)
        bot.unclaimedCatch.add(317)
        FishingTrawlerBotScript(bot).paused()
        verify(bot).move(FishingTrawler.ESCAPE)
        verify(bot.navigator).cancel()
        verify(bot.walking).clear()
        assertEquals(listOf(317), bot.unclaimedCatch)
    }
}
