package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class TanningWithdrawalBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.crafting.staticLevel).thenReturn(1)
        val inventory = spy(it.inventory)
        doReturn(true).`when`(inventory).stackable(995)
        `when`(it.inventory).thenReturn(inventory)
    }

    private fun script(bot: Bot) = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))

    @ParameterizedTest @EnumSource(value = Hide::class, names = ["HARD_LEATHER"], mode = EnumSource.Mode.EXCLUDE)
    fun actualCoinAndHideTransfersSupportPartialAndFullInventories(hide: Hide) = runBlocking<Unit> {
        for (amount in listOf(1, 27)) {
            val bot = bot()
            InventoryProductionFixtures.bank(bot, Item(hide.hide, amount), Item(995, hide.cost * amount))
            val script = script(bot)
            assertTrue(script.onInit(false))
            val batch = listOf(Item(995, hide.cost * amount), Item(hide.hide, amount))
            val banking = bot.actionHandler.banking
            doAnswer {
                for (item in batch) {
                    assertTrue(bot.bank.remove(item))
                    assertTrue(bot.inventory.add(item))
                }
                true
            }.`when`(banking).withdrawAll(batch)
            script.onBankOpen(true)
            assertFalse(script.isTerminated())
            assertEquals(amount, bot.inventory.computeAmountForId(hide.hide))
            assertEquals(hide.cost * amount, bot.inventory.computeAmountForId(995))
            verify(banking).clickBankingMode(false)
            assertFalse(script.onBankRequested(false))
        }
    }

    @Test fun reportedSuccessWithoutAnyItemsEndsTheSession() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        val script = script(bot)
        `when`(bot.actionHandler.banking.withdrawAll(listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3)))).thenReturn(true)
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
        assertFalse(bot.inventory.contains(Hide.SOFT_LEATHER.hide))
    }

    @Test fun receivingHidesWithoutTheRequestedCoinsAlsoEndsTheSession() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        val script = script(bot)
        val banking = bot.actionHandler.banking
        doAnswer {
            assertTrue(bot.bank.remove(Item(Hide.SOFT_LEATHER.hide, 3)))
            assertTrue(bot.inventory.add(Item(Hide.SOFT_LEATHER.hide, 3)))
            true
        }.`when`(banking).withdrawAll(listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3)))
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
        assertTrue(bot.inventory.contains(Hide.SOFT_LEATHER.hide))
        assertFalse(bot.inventory.contains(995))
    }
}
