package api.bot.action.trading

import api.bot.isRealBot
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

/**
 * Checks human priority, group-preserving polls, expiry and non-consuming interruption queries.
 *
 * @author lare96
 */
class BotTradeRequestHandlerTest {

    private fun autonomousBot(): Bot = mock(Bot::class.java).also {
        `when`(it.isBot).thenReturn(true)
    }

    private fun connectedHuman(): Bot = mock(Bot::class.java).also {
        `when`(it.isBot).thenReturn(false)
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
        val bot = autonomousBot()
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        assertFalse(requests.hasRequest { !it.isRealBot() })
        requests.addRequest(player)

        assertTrue(requests.hasRequest { !it.isRealBot() })
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
        val bot = autonomousBot()
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

    @Test fun humanPollingDoesNotVisitAnUnselectedBotGroup() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val bot = autonomousBot()
        requests.addRequest(bot)

        assertNull(requests.pollHumanRequests { fail("Human polling must not inspect an autonomous bot.") })

        assertTrue(requests.hasRequest { it === bot })
        assertSame(bot, requests.pollBotRequests { true })
    }

    @Test fun humanPollingRetainsBotsWhileAcceptingAHuman() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val bot = autonomousBot()
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        now = now.plusSeconds(1)
        requests.addRequest(player)
        val inspected = ArrayList<Player>()

        assertSame(player, requests.pollHumanRequests {
            inspected += it
            true
        })

        assertEquals(listOf(player), inspected)
        assertSame(bot, requests.pollBotRequests { true })
    }

    @Test fun rejectingTheHumanGroupRetainsPendingBotRequests() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val bot = autonomousBot()
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        requests.addRequest(player)
        val inspected = ArrayList<Player>()

        assertNull(requests.pollHumanRequests {
            inspected += it
            false
        })

        assertEquals(listOf(player), inspected)
        assertFalse(requests.hasRequest { it === player })
        assertTrue(requests.hasRequest { it === bot })
        assertSame(bot, requests.pollBotRequests { true })
    }

    @Test fun botPollingPreservesOrdinaryAndConnectedHumanRequests() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val player = mock(Player::class.java)
        val connected = connectedHuman()
        val bot = autonomousBot()
        requests.addRequest(player)
        now = now.plusSeconds(1)
        requests.addRequest(connected)
        now = now.plusSeconds(1)
        requests.addRequest(bot)
        val inspected = ArrayList<Player>()

        assertSame(bot, requests.pollBotRequests {
            inspected += it
            true
        })

        assertEquals(listOf(bot), inspected)
        assertSame(player, requests.pollHumanRequests { true })
        assertSame(connected, requests.pollHumanRequests { true })
    }

    @Test fun connectedHumansTakePriorityOverEarlierAutonomousBots() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val bot = autonomousBot()
        val connected = connectedHuman()
        val player = mock(Player::class.java)
        requests.addRequest(bot)
        now = now.plusSeconds(1)
        requests.addRequest(connected)
        now = now.plusSeconds(1)
        requests.addRequest(player)

        assertFalse(connected.isRealBot())
        assertTrue(bot.isRealBot())
        assertSame(connected, requests.pollRequests { true })
        assertSame(player, requests.pollRequests { true })
        assertSame(bot, requests.pollRequests { true })
    }

    @Test fun connectedHumansCanInterruptWithoutConsumingTheirRequest() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val connected = connectedHuman()
        requests.addRequest(autonomousBot())
        requests.addRequest(connected)

        assertTrue(requests.hasRequest { !it.isRealBot() })
        assertTrue(requests.hasRequest { !it.isRealBot() })

        assertSame(connected, requests.pollHumanRequests { true })
    }

    @Test fun groupPollingExpiresBothKindsAtTheExactBoundary() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        requests.addRequest(autonomousBot())
        requests.addRequest(connectedHuman())
        requests.addRequest(mock(Player::class.java))
        now = now.plusSeconds(10)

        assertNull(requests.pollHumanRequests { fail("Expired human requests must not be inspected.") })
        assertNull(requests.pollBotRequests { fail("Expired bot requests must not be inspected.") })
        assertFalse(requests.hasRequest { true })
    }

    @Test fun humanPollingExpiresStaleHumansButLeavesAFreshBot() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val bot = autonomousBot()
        requests.addRequest(connectedHuman())
        now = now.plusSeconds(1)
        requests.addRequest(bot)
        now = Instant.EPOCH.plusSeconds(10)

        assertNull(requests.pollHumanRequests { fail("Expired humans and unselected bots must not be inspected.") })

        assertSame(bot, requests.pollBotRequests { true })
    }

    @Test fun refreshingMovesARequestBehindOlderLiveRequestsInItsGroup() {
        var now = Instant.EPOCH
        val requests = BotTradeRequestHandler { now }
        val first = connectedHuman()
        val second = mock(Player::class.java)
        requests.addRequest(first)
        now = now.plusSeconds(1)
        requests.addRequest(second)
        now = now.plusSeconds(1)
        requests.addRequest(first)

        assertSame(second, requests.pollHumanRequests { true })
        assertSame(first, requests.pollHumanRequests { true })
        assertNull(requests.pollHumanRequests { true })
    }

    @Test fun equalTimestampRequestsKeepFifoOrderWithinEachGroup() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val firstBot = autonomousBot()
        val secondBot = autonomousBot()
        val firstHuman = connectedHuman()
        val secondHuman = mock(Player::class.java)
        requests.addRequest(firstBot)
        requests.addRequest(firstHuman)
        requests.addRequest(secondBot)
        requests.addRequest(secondHuman)

        assertSame(firstHuman, requests.pollRequests { true })
        assertSame(secondHuman, requests.pollRequests { true })
        assertSame(firstBot, requests.pollRequests { true })
        assertSame(secondBot, requests.pollRequests { true })
    }

    @Test fun anEqualTimestampRefreshMovesToTheTailWithoutDuplicatingTheRequest() {
        val requests = BotTradeRequestHandler { Instant.EPOCH }
        val first = connectedHuman()
        val second = mock(Player::class.java)
        requests.addRequest(first)
        requests.addRequest(second)
        requests.addRequest(first)

        assertSame(second, requests.pollHumanRequests { true })
        assertSame(first, requests.pollHumanRequests { true })
        assertNull(requests.pollHumanRequests { true })
    }
}
