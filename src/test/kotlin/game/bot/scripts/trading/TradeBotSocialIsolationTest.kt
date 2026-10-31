package game.bot.scripts.trading

import api.bot.action.trading.BotTradeManager.tradeRequests
import api.bot.action.trading.BotTradePurpose
import api.bot.script.InventoryProductionFixtures
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps prepared marketplace sessions available for native trades instead of paired social activities.
 *
 * @author lare96
 */
class TradeBotSocialIsolationTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initialize()

        private fun trader(purpose: BotTradePurpose, index: Int): Bot {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.index).thenReturn(index)
            `when`(bot.username).thenReturn("Trader$index")
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
            `when`(bot.position).thenReturn(Position(3180, 3450))
            `when`(bot.walking.isEmpty).thenReturn(true)
            `when`(bot.combat.target).thenReturn(null)
            `when`(bot.reflex.activeReflex).thenReturn(null)
            `when`(bot.isWithinDistance(any(Bot::class.java), eq(8))).thenReturn(true)
            val script = InventoryProductionFixtures.active(TradeBotScript(bot, purpose, duration = 10.minutes))
            `when`(bot.scriptStack.current()).thenReturn(script)
            TradeBotScript::class.java.getDeclaredField("prepared").apply {
                isAccessible = true
                setBoolean(script, true)
            }
            return bot
        }

        private fun human(): Player = mock(Player::class.java, RETURNS_DEEP_STUBS).also {
            `when`(it.attributes()).thenReturn(AttributeMap())
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.username).thenReturn("Human")
            `when`(it.overlays.player).thenReturn(it)
            `when`(it.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
        }

        private fun accepts(script: TradeBotScript, other: Player): Boolean =
            TradeBotScript::class.java.getDeclaredMethod("canAcceptRequest", Player::class.java).run {
                isAccessible = true
                invoke(script, other) as Boolean
            }

        private fun hasHumanRequest(script: TradeBotScript): Boolean =
            TradeBotScript::class.java.getDeclaredMethod("hasHumanRequest").run {
                isAccessible = true
                invoke(script) as Boolean
            }
    }

    @ParameterizedTest
    @EnumSource(BotTradePurpose::class)
    fun marketplaceSessionsCannotReserveBotsForConversation(purpose: BotTradePurpose) {
        val bot = trader(purpose, 1)
        val other = trader(BotTradePurpose.SELL, 2)

        assertFalse(BotConversations.available(bot))
        assertFalse(BotConversations.available(bot, allowWorking = true))
        assertNull(BotConversations.start(bot, other, listOf("Hello there", "Hello")))
        assertFalse(BotConversations.engaged(bot))
        assertFalse(BotConversations.engaged(other))
        assertFalse(BotSocialInteractions.engaged(bot))
        verify(bot.speechStack, never()).reserveConversation(any(), any())
    }

    @Test
    fun rejectedConversationLeavesHumanRequestEligible() {
        val bot = trader(BotTradePurpose.MERCHANT_BUY, 1)
        val other = trader(BotTradePurpose.SELL, 2)
        val human = human()
        human.tradingWith = bot.index

        assertNull(BotConversations.start(bot, other, listOf("Hello there", "Hello")))
        bot.tradeRequests.addRequest(human)
        val script = bot.scriptStack.current() as TradeBotScript
        assertSame(human, bot.tradeRequests.pollRequests { accepts(script, it) })
        assertFalse(BotConversations.engaged(bot))
        assertFalse(BotSocialInteractions.engaged(bot))
    }

    @Test
    fun humanWhoRequestedAnotherBotCannotInterruptOrOpenTheOldTrade() {
        val bot = trader(BotTradePurpose.MERCHANT_BUY, 1)
        val human = human()
        val script = bot.scriptStack.current() as TradeBotScript
        human.tradingWith = bot.index
        bot.tradeRequests.addRequest(human)
        human.tradingWith = 2

        assertFalse(hasHumanRequest(script))
        assertNull(bot.tradeRequests.pollRequests { accepts(script, it) })
    }

    @Test
    fun liveHumanPriorityQueryDoesNotConsumeTheRequest() {
        val bot = trader(BotTradePurpose.MERCHANT_BUY, 1)
        val human = human()
        val script = bot.scriptStack.current() as TradeBotScript
        human.tradingWith = bot.index
        bot.tradeRequests.addRequest(human)

        assertTrue(hasHumanRequest(script))
        assertTrue(hasHumanRequest(script))
        assertSame(human, bot.tradeRequests.pollRequests { accepts(script, it) })
        assertFalse(hasHumanRequest(script))
    }

    @Test
    fun reciprocalOfferRemainsEligibleAfterNativeRequestStateIsCleared() {
        val bot = trader(BotTradePurpose.MERCHANT_BUY, 1)
        val human = human()
        val script = bot.scriptStack.current() as TradeBotScript
        val screen = mock(OfferTradeInterface::class.java)
        `when`(screen.other).thenReturn(bot)
        `when`(human.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(screen)
        `when`(human.overlays.hasWindow()).thenReturn(true)
        human.tradingWith = -1
        bot.tradeRequests.addRequest(human)

        assertTrue(hasHumanRequest(script))
        assertSame(human, bot.tradeRequests.pollRequests { accepts(script, it) })
    }
}
