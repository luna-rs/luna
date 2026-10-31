package api.bot.action.trading

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant

/**
 * Checks request priority, expiry and non-consuming interruption queries.
 *
 * @author lare96
 */
class BotTradeRequestHandlerTest {

    @Test fun humansTakePriorityWithoutDiscardingPendingBotRequests() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val bot = mock(Bot::class.java)
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        now = now.plusSeconds(1)
        requests.addRequest(player)

        assertSame(player, requests.pollRequests { true })
        assertSame(bot, requests.pollRequests { true })
        assertNull(requests.pollRequests { true })
    }

    @Test fun olderRequestsComeFirstWithinTheSamePriority() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val first = mock(Player::class.java)
        val second = mock(Player::class.java)
        requests.addRequest(first)
        now = now.plusSeconds(1)
        requests.addRequest(second)

        assertSame(first, requests.pollRequests { true })
        assertSame(second, requests.pollRequests { true })
    }

    @Test fun priorityChecksDoNotConsumeRequests() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val bot = mock(Bot::class.java)
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        assertFalse(requests.hasRequest { it !is Bot })
        requests.addRequest(player)

        assertTrue(requests.hasRequest { it !is Bot })
        assertTrue(requests.hasRequest { it === player })
        assertSame(player, requests.pollRequests { true })
        assertSame(bot, requests.pollRequests { true })
    }

    @Test fun expiredRequestsCannotInterruptOrBeAccepted() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        requests.addRequest(mock(Player::class.java))
        now = now.plusSeconds(10)

        assertFalse(requests.hasRequest { fail("An expired request must not be inspected.") })
        assertNull(requests.pollRequests { fail("An expired request must not be accepted.") })
    }

    @Test fun repeatingARequestRefreshesItsLifetime() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val player = mock(Player::class.java)
        requests.addRequest(player)
        now = now.plusSeconds(9)
        requests.addRequest(player)
        now = now.plusSeconds(9)

        assertTrue(requests.hasRequest { it === player })
        assertSame(player, requests.pollRequests { true })
        assertNull(requests.pollRequests { true })
    }

    @Test fun rejectingAHumanStillPermitsAValidBotRequest() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val bot = mock(Bot::class.java)
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        requests.addRequest(player)
        val inspected = ArrayList<Player>()

        assertSame(bot, requests.pollRequests {
            inspected += it
            it === bot
        })
        assertEquals(listOf(player, bot), inspected)
        assertNull(requests.pollRequests { true })
    }
}
