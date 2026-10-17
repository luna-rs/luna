package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class BotTradeStageTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()

        private fun bot(): Bot = InventoryProductionFixtures.bot().also {
            `when`(it.overlays.player).thenReturn(it)
            `when`(it.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
            `when`(it.overlays.getOverlay(ConfirmTradeInterface::class.java)).thenReturn(null)
        }

        private fun screen(bot: Bot, other: Player): OfferTradeInterface {
            val screen = mock(OfferTradeInterface::class.java)
            `when`(screen.other).thenReturn(other)
            `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(screen)
            return screen
        }
    }

    @Test fun cleanupOnlyClosesTheTradeOwnedByTheStage() {
        val bot = bot()
        val other = mock(Player::class.java)
        val unrelated = mock(Player::class.java)
        screen(bot, unrelated)
        val stage = BotTradeStage(bot, other)
        stage.decline()
        verify(bot.overlays, never()).closeWindows()
        screen(bot, other)
        stage.decline()
        verify(bot.overlays).closeWindows()
    }

    @Test fun aChangedOfferIsRejectedAfterTheAcceptanceDelay(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        screen(bot, other)
        val stage = BotTradeOfferStage(bot, other, BotTradeRequestStage(bot, other))
        assertNull(stage.offerAndAwait { false })
        verify(bot.output, never()).clickButton(3420)
    }

    @Test fun confirmationCannotAcceptAnUnrelatedTrade(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        val unrelated = mock(Player::class.java)
        val offer = screen(bot, unrelated)
        val confirm = mock(ConfirmTradeInterface::class.java)
        `when`(confirm.offer).thenReturn(offer)
        `when`(bot.overlays.getOverlay(ConfirmTradeInterface::class.java)).thenReturn(confirm)
        val stage = BotTradeConfirmStage(bot, other,
            BotTradeOfferStage(bot, other, BotTradeRequestStage(bot, other)))
        assertFalse(stage.confirmAndAwait())
        verify(bot.output, never()).clickButton(3546)
    }

    @Test fun anAlreadyOpenedRequestUsesTheCorrectExistingTrade(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        screen(bot, other)
        val stage = BotTradeRequestStage(bot, other).sendAndAwait()
        assertSame(other, stage?.other)
        verifyNoInteractions(bot.actionHandler.interactions)
    }

    @Test fun zeroAndDynamicOfferRequestsCannotSendInventoryClicks(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        val stage = BotTradeOfferStage(bot, other, BotTradeRequestStage(bot, other))
        assertFalse(stage.offer(Item(314, 0)))
        assertFalse(stage.offer(DynamicItem(314)))
        assertFalse(stage.remove(Item(314, 0)))
        verifyNoInteractions(bot.output)
    }

    @Test fun paymentStockExcludesEquipmentAndDynamicItems() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(995, 100))
        InventoryProductionFixtures.inventory(bot, Item(995, 20), DynamicItem(995))
        val items = BotTradeManager.computeOfferItems(bot, BotTradePrice.EXACT, BotTradePurpose.BUY)
        assertEquals(1, items.size)
        assertEquals(Item(995, 120), items.single().item)
    }
}
