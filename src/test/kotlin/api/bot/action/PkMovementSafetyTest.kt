package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.Zone
import api.predef.world
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.brain.BotReflex
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.model.mob.movement.WalkingNavigator
import io.luna.game.model.mob.movement.WalkingQueue
import io.luna.game.model.path.route.RouteStrategy
import io.luna.game.task.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Tests collision-checked idle retreat and Wilderness escape with native walking queues and bounded fixture waits.
 * Travel and teleport outcomes are controlled; no route generation, live server or forced teleport is exercised.
 *
 * @author lare96
 */
class PkMovementSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private lateinit var npc: Npc
    private lateinit var policy: BotCombatActionHandler
    private val tried = mutableListOf<Direction>()
    private var passable: (Direction) -> Boolean = { true }
    private var disabled = false
    private var wandering = true

    @BeforeEach fun setup() = runBlocking<Unit> {
        tried.clear()
        passable = { true }
        disabled = false
        wandering = true
        bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.size()).thenReturn(1)
        doReturn(RouteStrategy.NORMAL).`when`(bot).routeStrategy
        doReturn(Position(3100, 3540)).`when`(bot).position
        `when`(bot.combat.target).thenReturn(null)
        val combat = bot.combat
        `when`(combat.isDisabled).thenAnswer { disabled }
        doAnswer { disabled = it.getArgument(0); null }.`when`(combat).setDisabled(anyBoolean())
        `when`(bot.isWandering).thenAnswer { wandering }
        doAnswer { wandering = it.getArgument(0); null }.`when`(bot).setWandering(anyBoolean())
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.MAIN))
        val fixtureWorld = mock(World::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.world).thenReturn(fixtureWorld)
        val walking = WalkingQueue(bot)
        `when`(bot.walking).thenReturn(walking)
        val navigator = WalkingNavigator(bot)
        `when`(bot.navigator).thenReturn(navigator)
        `when`(fixtureWorld.collisionManager.traversable(any(), any(), any(), anyInt(), any(RouteStrategy::class.java))).thenAnswer {
            val direction = it.getArgument<Direction>(2)
            tried += direction
            passable(direction)
        }
        npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(npc.isAlive).thenReturn(true)
        `when`(npc.state).thenReturn(EntityState.ACTIVE)
        `when`(npc.size()).thenReturn(1)
        doReturn(Position(3102, 3540)).`when`(npc).position
        `when`(npc.isViewableFrom(bot)).thenReturn(true)
        policy = BotCombatActionHandler(bot, bot.actionHandler)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            val task = it.getArgument<Task>(0)
            repeat(20) { run.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))
        val fallback = Position(0, 0)
        val handler = bot.actionHandler
        doReturn(false).`when`(handler).webWalkTo(any(Position::class.java) ?: fallback,
            eq(0), eq(1), eq(BankAccess.NONE) ?: BankAccess.NONE)
        bot.wildernessLevel = 3
    }

    @AfterEach fun restoreScheduler() { doNothing().`when`(world).schedule(any(Task::class.java)) }

    @Test fun idleRetreatUsesNativeCollisionChecksAndQueuesOnlyOneStepAway() {
        assertTrue(policy.stepAwayFrom(npc))
        assertEquals(1, bot.walking.size())
        assertTrue(tried.single().translateX < 0)
        assertFalse(policy.stepAwayFrom(npc))
        assertEquals(1, tried.size)
    }

    @Test fun blockedDirectRetreatFallsBackToLateralSpaceAndNeverMovesCloser() {
        passable = { it == Direction.NORTH }
        assertTrue(policy.stepAwayFrom(npc))
        assertEquals(Direction.NORTH, tried.last())
        assertTrue(tried.all { it.translateX <= 0 })
    }

    @Test fun boxedOrLockedBotsDoNotClipOrEraseQueuedMovement() {
        passable = { false }
        assertFalse(policy.stepAwayFrom(npc))
        assertTrue(bot.walking.isEmpty)
        tried.clear()
        bot.walking.isLocked = true
        assertFalse(policy.stepAwayFrom(npc))
        assertTrue(tried.isEmpty())
    }

    @Test fun activeNavigationIsPreservedWithoutQueuingARetreatStep() {
        val navigator = mock(WalkingNavigator::class.java)
        `when`(bot.navigator).thenReturn(navigator)
        `when`(navigator.isActive).thenReturn(true)
        assertFalse(policy.stepAwayFrom(npc))
        verify(navigator, never()).cancel()
        verify(navigator, never()).step(any())
    }

    @Test fun retreatRespectsTheCallerBoundaryAndRejectsInvisibleOrDifferentPlaneThreats() {
        assertFalse(policy.stepAwayFrom(npc) { false })
        assertTrue(tried.isEmpty())
        `when`(npc.isViewableFrom(bot)).thenReturn(false)
        assertFalse(policy.stepAwayFrom(npc))
        `when`(npc.isViewableFrom(bot)).thenReturn(true)
        doReturn(Position(3102, 3540, 1)).`when`(npc).position
        assertFalse(policy.stepAwayFrom(npc))
    }

    @Test fun pkNpcReflexRetreatsWithoutStartingAnNpcFightButOtherActivitiesKeepTheirReflex() {
        val stack = BotScriptStack(bot, mock(BotScriptManager::class.java))
        `when`(bot.scriptStack).thenReturn(stack)
        val session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        stack.pushTail(session)
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.target).thenReturn(npc)
        assertTrue(BotReflex().process(bot))
        assertSame(session, stack.current())
        verify(bot.combat).setTarget(null)
        assertEquals(1, bot.walking.size())
        stack.pushHead(mock(api.bot.script.BotScript::class.java))
        `when`(bot.emotions.getCombatResponse(npc)).thenReturn(CombatBotScript.InitialState.ATTACK)
        assertFalse(BotReflex().process(bot))
        assertTrue(stack.current() is CombatBotScript)
    }

    @Test fun retreatDoesNotClearAnExistingPlayerTarget() {
        val player = mock(Player::class.java)
        `when`(bot.combat.target).thenReturn(player)
        PkBotScript(bot, 5.minutes).avoidNpcCombat(npc)
        verify(bot.combat, never()).setTarget(null)
        assertTrue(tried.isEmpty())
    }

    @Test fun teleblockedBotsWalkOutsideWildernessWithoutRepeatedHomeCommandsAndRestoreFlags() = runBlocking<Unit> {
        disabled = true
        `when`(bot.status.isTeleBlocked()).thenReturn(true)
        val handler = bot.actionHandler
        doAnswer {
            assertTrue(disabled)
            assertFalse(wandering)
            bot.wildernessLevel = 0
            true
        }.`when`(handler).webWalkTo(Zone.EDGEVILLE.anchor, 0, 1, BankAccess.NONE)
        assertTrue(policy.fleeWilderness())
        assertTrue(disabled)
        assertTrue(wandering)
        verify(bot.output, never()).sendCommand("home")
    }

    @Test fun levelTwentyCanTeleportAndFailedTeleportFallsBackToWalkingWithoutTrustingReportedSuccess() = runBlocking<Unit> {
        doReturn(Position(3100, 3679)).`when`(bot).position
        bot.wildernessLevel = 20
        assertFalse(withTimeout(5_000) { policy.fleeWilderness() })
        verify(bot.output, times(2)).sendCommand("home")
        verify(bot.actionHandler).webWalkTo(Zone.EDGEVILLE.anchor, 0, 1, BankAccess.NONE)
        assertFalse(disabled)
        assertTrue(wandering)
    }

    @Test fun deepWildernessDoesNotAttemptAnIllegalHomeTeleport() = runBlocking<Unit> {
        doReturn(Position(3100, 3800)).`when`(bot).position
        bot.wildernessLevel = 36
        assertFalse(policy.fleeWilderness())
        verify(bot.output, never()).sendCommand("home")
        assertFalse(disabled)
    }

    @Test fun cancellationDuringEscapeRestoresThePriorFlags() = runBlocking<Unit> {
        `when`(bot.status.isTeleBlocked()).thenReturn(true)
        val handler = bot.actionHandler
        doAnswer { throw CancellationException("fixture cancellation") }.`when`(handler)
            .webWalkTo(Zone.EDGEVILLE.anchor, 0, 1, BankAccess.NONE)
        try {
            policy.fleeWilderness()
            fail("Escape must propagate cancellation")
        } catch (_: CancellationException) {
            assertFalse(disabled)
            assertTrue(wandering)
        }
    }

    @Test fun aDeadOrInactiveBotCannotReportSuccessfulEscapeOrStartTravel() = runBlocking<Unit> {
        `when`(bot.isAlive).thenReturn(false)
        assertFalse(policy.fleeWilderness())
        verifyNoInteractions(bot.output)
        assertFalse(disabled)
    }
}
