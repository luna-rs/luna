package api.bot.script

import api.bot.action.trading.BotMarketplace
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotGearLocator
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.Task
import io.luna.game.task.TaskState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes
import java.util.EnumSet

/**
 * Exercises the shared zoned lifecycle when joining and rejoining precise marketplace work rectangles.
 *
 * @author lare96
 */
class MarketplaceWorkAreaTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    /**
     * Minimal activity exercising the actual shared run loop without recipe or banking side effects.
     *
     * @author lare96
     */
    private class Activity(bot: Bot, zone: SubZone, private val banking: Boolean) : ZonedBotScript(bot, 8.minutes, mutableListOf(zone)) {
        var executions = 0
        var bankVisits = 0
        init { depositInventoryWhenBanking = false }
        fun requestBank() { forceBanking = true }
        override fun snapshot(): BotScriptData? = null
        override suspend fun onBankRequested(initial: Boolean) = banking && !initial
        override suspend fun onBankOpen(initial: Boolean) { bankVisits++ }
        override suspend fun equipment(): BotGearLocator? = null
        override suspend fun executeInZone(): Boolean { executions++; return true }
    }

    private lateinit var bot: Bot
    private var position = Position(2728, 3494)
    @BeforeEach fun fixture() {
        bot = InventoryProductionFixtures.bot()
        doReturn(EnumSet.noneOf(SubZone::class.java)).`when`(bot).subZones
        `when`(bot.position).thenAnswer { position }
        `when`(bot.abs()).thenAnswer { position }
        `when`(bot.walking.isEmpty).thenReturn(true)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            val task = it.getArgument<Task>(0)
            repeat(100) { if (task.state != TaskState.CANCELLED) run.invoke(task) }
            assertEquals(TaskState.CANCELLED, task.state)
            null
        }.`when`(world).schedule(any(Task::class.java))
    }
    @AfterEach fun restoreWorld() {
        doNothing().`when`(world).schedule(any(Task::class.java))
        val collision = world.collisionManager
        doReturn(false).`when`(collision).isBlocked(any(Position::class.java), eq(false))
    }

    private suspend fun script(zone: SubZone = SubZone.SEERS_VILLAGE_MARKETPLACE, banking: Boolean = false): Activity {
        val script = Activity(bot, zone, banking)
        assertFalse(script.init(false))
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, zone) }
        return script
    }

    private fun anyPosition(): Position = any(Position::class.java) ?: Position(0, 0)

    private suspend fun walk(success: Boolean = true, exact: Boolean = true) {
        val handler = bot.actionHandler
        `when`(handler.webWalkTo(anyPosition(), eq(0), eq(1))).thenAnswer {
            val target = it.getArgument<Position>(0)
            if (success) position = if (exact) target else target.translate(0, 0, 1)
            success
        }
    }

    @Test fun joinsMarketBeforeProductionAndRejoinsAfterReturningToTheBank() = runBlocking<Unit> {
        val market = BotMarketplace.SEERS_VILLAGE
        val script = script()
        walk()
        assertFalse(script.run())
        assertTrue(market.contains(position))
        assertEquals(1, script.executions)
        position = Position(2728, 3494)
        assertFalse(market.contains(position))
        assertFalse(script.run())
        assertTrue(market.contains(position))
        assertEquals(2, script.executions)
        verify(bot.actionHandler, times(2)).webWalkTo(anyPosition(), eq(0), eq(1))
    }

    @Test fun alreadyInsideAnExactRectangleDoesNotMoveOrInterruptProduction() = runBlocking<Unit> {
        position = Position(2723, 3485)
        val script = script()
        assertFalse(script.run())
        assertEquals(1, script.executions)
        verify(bot.actionHandler, never()).webWalkTo(anyPosition(), anyInt(), anyInt())
    }

    @Test fun broadBoundingBoxGapStillRequiresARealWorkTile() = runBlocking<Unit> {
        position = Position(2722, 3488)
        val script = script()
        assertTrue(position in BotMarketplace.SEERS_VILLAGE.activityZone.area)
        assertFalse(BotMarketplace.SEERS_VILLAGE.contains(position))
        walk()
        assertFalse(script.run())
        assertTrue(BotMarketplace.SEERS_VILLAGE.contains(position))
        assertEquals(1, script.executions)
    }

    @Test fun failedWalkRejectsTheMarketWithoutExecutingItsActivity() = runBlocking<Unit> {
        val script = script()
        walk(success = false)
        assertTrue(script.run())
        assertEquals(0, script.executions)
        assertTrue(script.zones.isEmpty())
    }

    @Test fun claimedNavigationSuccessOnTheWrongPlaneIsNotAccepted() = runBlocking<Unit> {
        val script = script()
        walk(exact = false)
        assertTrue(script.run())
        assertEquals(0, script.executions)
    }

    @Test fun fullyBlockedMarketIsRejectedWithoutCallingNavigation() = runBlocking<Unit> {
        val script = script()
        val collision = world.collisionManager
        doReturn(true).`when`(collision).isBlocked(any(Position::class.java), eq(false))
        assertTrue(script.run())
        assertEquals(0, script.executions)
        verify(bot.actionHandler, never()).webWalkTo(anyPosition(), anyInt(), anyInt())
    }

    @Test fun nativeActionOutsideMarketDefersWalkingAndProductionWithoutDroppingTheZone() = runBlocking<Unit> {
        val script = script()
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(script.run())
        assertEquals(0, script.executions)
        assertEquals(listOf(SubZone.SEERS_VILLAGE_MARKETPLACE), script.zones)
        verify(bot.actionHandler, never()).webWalkTo(anyPosition(), anyInt(), anyInt())
    }

    @Test fun weakProductionActionOutsideMarketIsAllowedToFinishBeforeRelocating() = runBlocking<Unit> {
        val script = script()
        bot.actions.submit(object : Action<Bot>(bot, ActionType.WEAK) {
            override fun run() = false
        })
        assertFalse(script.run())
        assertEquals(0, script.executions)
        verify(bot.actionHandler, never()).webWalkTo(anyPosition(), anyInt(), anyInt())
    }

    @Test fun alKharidParentArrivalOutsideMarketStillReachesAnExactWorkingRectangle() = runBlocking<Unit> {
        val market = BotMarketplace.AL_KHARID
        position = market.arrival
        assertFalse(market.contains(position))
        val script = script(market.activityZone)
        walk()
        assertFalse(script.run())
        assertTrue(market.contains(position))
        assertEquals(1, script.executions)
    }

    @Test fun actualBankCycleDoesNotDetourToArrivalBeforeRejoiningTheMarket() = runBlocking<Unit> {
        val script = script(banking = true)
        val bank = spy(bot.bank)
        doReturn(true).`when`(bank).isOpen
        `when`(bot.bank).thenReturn(bank)
        val cached = mock(GameObject::class.java)
        ZonedBotScript::class.java.getDeclaredField("cachedBank").apply { isAccessible = true; set(script, cached) }
        script.requestBank()
        assertFalse(script.run())
        assertEquals(1, script.bankVisits)
        assertEquals(0, script.executions)
        verify(bot.actionHandler, never()).travelTo(any(SubZone::class.java) ?: SubZone.HOME)
        walk()
        assertFalse(script.run())
        assertTrue(BotMarketplace.SEERS_VILLAGE.contains(position))
        assertEquals(1, script.executions)
        assertEquals(1, script.bankVisits)
    }

    @Test fun ordinaryFacilityZoneRetainsItsOriginalExecutionBehavior() = runBlocking<Unit> {
        position = SubZone.AL_KHARID_BANK.inside
        val script = script(SubZone.AL_KHARID_BANK)
        assertFalse(script.run())
        assertEquals(1, script.executions)
        verify(bot.actionHandler, never()).webWalkTo(anyPosition(), anyInt(), anyInt())
    }
}
