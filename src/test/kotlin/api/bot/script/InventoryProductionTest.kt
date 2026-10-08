package api.bot.script

import api.bot.zone.SubZone
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class InventoryProductionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun finalPartialBatchReservesTheToolSlot() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623, 100), Item(1755))
        assertEquals(listOf(Item(1755), Item(1623, 27)), bot.productionBatch(listOf(Item(1623)), setOf(1755)))
        bot.bank.remove(Item(1623, 97))
        assertEquals(listOf(Item(1755), Item(1623, 3)), bot.productionBatch(listOf(Item(1623)), setOf(1755)))
    }

    @Test fun pairedInputsUseSmallerStockAndInventoryCapacity() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(985, 100), Item(987, 3))
        val materials = listOf(Item(985), Item(987))
        assertEquals(listOf(Item(985, 3), Item(987, 3)), bot.productionBatch(materials))
        InventoryProductionFixtures.bank(bot, Item(987, 100))
        assertEquals(listOf(Item(985, 14), Item(987, 14)), bot.productionBatch(materials))
    }

    @Test fun missingToolsOrExhaustedMaterialsProduceNoWithdrawal() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623, 3))
        assertTrue(bot.productionBatch(listOf(Item(1623)), setOf(1755)).isEmpty())
        bot.bank.remove(Item(1623, 3))
        assertTrue(bot.productionBatch(listOf(Item(1623))).isEmpty())
    }

    @Test fun invalidRecipeQuantitiesAndDuplicateInputsAreRejected() {
        val bot = InventoryProductionFixtures.bot()
        assertThrows(IllegalArgumentException::class.java) { bot.productionBatch(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            bot.productionBatch(listOf(Item(1623), Item(1623)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bot.productionBatch(listOf(Item(1623)), setOf(1623))
        }
    }

    @Test fun ownershipIncludesInventoryAndBankAndRequiresEveryInput() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1755))
        InventoryProductionFixtures.bank(bot, Item(1623, 3))
        assertTrue(bot.ownsProductionSupplies(listOf(Item(1623, 3)), setOf(1755)))
        assertFalse(bot.ownsProductionSupplies(listOf(Item(1623, 4)), setOf(1755)))
        assertFalse(bot.ownsProductionSupplies(listOf(Item(1623)), setOf(233)))
    }

    private open class Recipe(bot: Bot) :
        InventoryBotScript(bot, 10.minutes, mutableListOf(SubZone.HOME)) {
        init { progress = Job() }
        override fun withdraw() = listOf(Item(1623))
        override fun snapshot(): BotScriptData? = null
    }

    @Test fun successfulDefaultWithdrawalRetainsExistingBankingBehavior() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        val script = Recipe(bot)
        assertTrue(script.onInit(false))
        val items = listOf(Item(1623))
        `when`(bot.actionHandler.banking.withdrawAll(items)).thenReturn(true)
        script.onBankOpen(true)
        verify(bot.actionHandler.banking).withdrawAll(items)
        assertFalse(script.isTerminated())
    }

    @Test fun unsuccessfulDefaultWithdrawalEndsTheScript() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        val script = Recipe(bot)
        assertTrue(script.onInit(false))
        `when`(bot.actionHandler.banking.withdrawAll(listOf(Item(1623)))).thenReturn(false)
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
    }

    @Test fun inventoryBaseUsesTheSubclassWithdrawalHook() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        var attempted: List<Item>? = null
        val script = object : Recipe(bot) {
            override suspend fun withdrawBankItems(items: List<Item>): Boolean {
                attempted = items
                return true
            }
        }
        assertTrue(script.onInit(false))
        script.onBankOpen(true)
        assertEquals(listOf(Item(1623)), attempted)
        verify(bot.actionHandler.banking, never()).withdrawAll(anyList())
        assertFalse(script.isTerminated())
    }
}
