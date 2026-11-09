package game.bot.scripts

import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradePurpose
import api.bot.script.BotScript
import api.bot.script.InventoryProductionFixtures
import api.predef.world
import game.bot.scripts.skills.EctofuntusBotScript
import game.bot.scripts.trading.TradeBotScript
import game.content.ectofuntus.EctofuntusBone
import io.luna.game.model.EntityState
import io.luna.game.model.WorldLocator
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.Continuation
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises saturation backoff, local occupancy and bounded combat deferral from the November soak.
 *
 * @author lare96
 */
class SoakRegressionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun deferredTraderWaitsBeforeAnyCapacityOrTravelWork() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val script = TradeBotScript(bot, BotTradePurpose.BUY, duration = 10.minutes,
            retryAt = System.currentTimeMillis() + 120_000)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { script.onInit(false) }
        assertFalse(pending.isCompleted)
        verifyNoInteractions(bot.actionHandler)
        pending.cancelAndJoin()
    }

    @Test fun resumedTraderRetainsItsCooldown() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val script = TradeBotScript(bot, BotTradePurpose.BUY, duration = 10.minutes,
            retryAt = System.currentTimeMillis() + 120_000)
        repeat(2) {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { script.onInit(it > 0) }
            assertFalse(pending.isCompleted)
            pending.cancelAndJoin()
        }
        verifyNoInteractions(bot.actionHandler)
    }

    @Test fun ectofuntusAllowsOrdinaryThreatResponse() {
        val script = EctofuntusBotScript(InventoryProductionFixtures.bot(), EctofuntusBone.entries.first(),
            System.currentTimeMillis() + 120_000)
        assertTrue(script.allowsCombatResponse)
    }

    private suspend fun await(script: EctofuntusBotScript): Boolean = suspendCoroutineUninterceptedOrReturn { cont ->
        EctofuntusBotScript::class.java.getDeclaredMethod("awaitActionSafety",
            BotScript.ActionSafety::class.java, Continuation::class.java).apply { isAccessible = true }
            .invoke(script, BotScript.ActionSafety.NON_COMBAT, cont)
    }

    @Test fun expiredEctofuntusVisitDoesNotWaitForeverInCombat() = runTest {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.combat.inCombat()).thenReturn(true)
        val script = EctofuntusBotScript(bot, EctofuntusBone.entries.first(), System.currentTimeMillis() - 1)
        assertFalse(await(script))
        verify(bot.combat, never()).reset()
    }

    @Test fun ectofuntusCombatWaitExpiresAtVisitDeadline() = runTest {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.combat.inCombat()).thenReturn(true)
        val script = EctofuntusBotScript(bot, EctofuntusBone.entries.first(), System.currentTimeMillis() + 5_000)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { await(script) }
        assertFalse(pending.isCompleted)
        assertFalse(pending.await())
    }

    @Test fun marketplaceOccupancyQueriesCoverGeometryAndExcludeRequester() {
        val bot = InventoryProductionFixtures.bot()
        val previous = world.locator
        val locator = mock(WorldLocator::class.java)
        `when`(world.locator).thenReturn(locator)
        try {
            for (market in BotMarketplace.entries) {
                val all = market.anchors.flatMap { it.computePositions() }.distinct()
                val playerTile = all[0]
                val npcTile = all[1]
                val outsideEast = Position(all.maxOf { it.x } + 1, playerTile.y)
                val outsideWest = Position(all.minOf { it.x } - 1, playerTile.y)
                fun player(position: Position, state: EntityState = EntityState.ACTIVE): Player =
                    mock(Player::class.java).also {
                        `when`(it.position).thenReturn(position)
                        `when`(it.state).thenReturn(state)
                    }
                fun npc(position: Position, state: EntityState = EntityState.ACTIVE): Npc =
                    mock(Npc::class.java).also {
                        `when`(it.position).thenReturn(position)
                        `when`(it.state).thenReturn(state)
                    }
                val players = listOf(bot, player(playerTile), player(all[2], EntityState.INACTIVE),
                    player(Position(all[3].x, all[3].y, 1)), player(outsideEast))
                val npcs = listOf(npc(npcTile), npc(all[4], EntityState.INACTIVE),
                    npc(Position(all[5].x, all[5].y, 1)), npc(outsideWest))
                `when`(bot.position).thenReturn(all.last())
                doAnswer { call ->
                    val center = call.getArgument<Position>(0)
                    val radius = call.getArgument<Int>(1)
                    assertTrue(all.all { it.isWithinDistance(center, radius) })
                    val filter = call.getArgument<Predicate<Player>>(2)
                    players.filter { filter.test(it) }.toSet()
                }.`when`(locator).findPlayers(any(Position::class.java), anyInt(), any())
                doAnswer { call ->
                    val center = call.getArgument<Position>(0)
                    val radius = call.getArgument<Int>(1)
                    assertTrue(all.all { it.isWithinDistance(center, radius) })
                    val filter = call.getArgument<Predicate<Npc>>(2)
                    npcs.filter { filter.test(it) }.toSet()
                }.`when`(locator).findNpcs(any(Position::class.java), anyInt(), any())
                val occupied = BotMarketplace::class.java.getDeclaredMethod("occupiedPositions", Bot::class.java)
                    .apply { isAccessible = true }.invoke(market, bot)
                assertEquals(setOf(playerTile, npcTile), occupied, market.name)
            }
        } finally {
            `when`(world.locator).thenReturn(previous)
        }
    }
}
