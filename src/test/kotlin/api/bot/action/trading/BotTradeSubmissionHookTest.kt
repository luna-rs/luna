package api.bot.action.trading

import api.bot.action.BotInteractionActionHandler
import api.bot.action.BotTradingActionHandler
import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.trade.OfferTradeInterface
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises one-time trade submission prerequisites through real interaction and trade handlers.
 *
 * @author lare96
 */
class BotTradeSubmissionHookTest {

    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()

        private val runTask = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }

        private fun bot(): Bot = InventoryProductionFixtures.bot().also {
            `when`(it.overlays.player).thenReturn(it)
            `when`(it.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
        }

        private fun open(bot: Bot, other: Player): OfferTradeInterface {
            val screen = mock(OfferTradeInterface::class.java)
            `when`(screen.other).thenReturn(other)
            `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(screen)
            return screen
        }

        private fun ready(): Pair<Bot, Player> {
            val bot = bot()
            val other = mock(Player::class.java)
            `when`(bot.state).thenReturn(EntityState.ACTIVE)
            `when`(bot.type).thenReturn(EntityType.PLAYER)
            `when`(bot.isAlive).thenReturn(true)
            `when`(bot.isLocked).thenReturn(false)
            `when`(bot.position).thenReturn(Position(3200, 3200))
            `when`(bot.walking.isLocked).thenReturn(false)
            `when`(bot.walking.isEmpty).thenReturn(true)
            `when`(bot.contextMenu.contains(TRADE)).thenReturn(true)
            `when`(other.state).thenReturn(EntityState.ACTIVE)
            `when`(other.type).thenReturn(EntityType.PLAYER)
            `when`(other.isAlive).thenReturn(true)
            `when`(other.username).thenReturn("Seller")
            `when`(other.position).thenReturn(Position(3201, 3200))
            `when`(other.isViewableFrom(bot)).thenReturn(true)
            `when`(world.collisionManager.reached(bot, other, InteractionPolicy.STANDARD_SIZE)).thenReturn(true)
            val handler = bot.actionHandler
            `when`(handler.interactions).thenReturn(BotInteractionActionHandler(bot, handler))
            `when`(handler.trading).thenReturn(BotTradingActionHandler(bot, handler))
            return bot to other
        }
    }

    @BeforeEach fun executeConditionTasksImmediately() {
        doAnswer {
            runTask.invoke(it.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
    }

    @AfterEach fun restoreConditionTaskScheduling() {
        doNothing().`when`(world).schedule(any(Task::class.java))
    }

    @Test fun repeatedPermissionChecksRunThePrerequisiteOnceBeforeTheNativeClick(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var permissionChecks = 0
            var prerequisites = 0
            val order = ArrayList<String>()
            `when`(bot.output.sendPlayerInteraction(TRADE.index, other)).thenAnswer {
                order += "click"
                true
            }

            assertTrue(bot.actionHandler.interactions.interactBeforeSubmit(TRADE.index, other,
                allowed = { permissionChecks++; true },
                beforeSubmit = { prerequisites++; order += "prerequisite"; true }))

            assertTrue(permissionChecks > 1)
            assertEquals(1, prerequisites)
            assertEquals(listOf("prerequisite", "click"), order)
            verify(bot.output, times(1)).sendPlayerInteraction(TRADE.index, other)
        }
    }

    @Test fun aFailedPrerequisitePreventsTheNativeClick(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var prerequisites = 0

            assertFalse(bot.actionHandler.interactions.interactBeforeSubmit(TRADE.index, other,
                allowed = { true }, beforeSubmit = { prerequisites++; false }))

            assertEquals(1, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }

    @Test fun permissionLostBeforeApproachSkipsThePrerequisiteAndClick(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var permissionChecks = 0
            var prerequisites = 0

            assertFalse(bot.actionHandler.interactions.interactBeforeSubmit(TRADE.index, other,
                allowed = { ++permissionChecks == 1 }, beforeSubmit = { prerequisites++; true }))

            assertTrue(permissionChecks > 1)
            assertEquals(0, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }

    @Test fun permissionLostWhenReachCompletesSkipsThePrerequisiteAndClick(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var permitted = true
            var prerequisites = 0
            `when`(world.collisionManager.reached(bot, other, InteractionPolicy.STANDARD_SIZE)).thenAnswer {
                permitted = false
                true
            }

            assertFalse(bot.actionHandler.interactions.interactBeforeSubmit(TRADE.index, other,
                allowed = { permitted }, beforeSubmit = { prerequisites++; true }))

            assertEquals(0, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }

    @Test fun losingTheNativeTradeMenuPermissionBeforeSubmissionSkipsThePrerequisite(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var prerequisites = 0
            `when`(world.collisionManager.reached(bot, other, InteractionPolicy.STANDARD_SIZE)).thenAnswer {
                `when`(bot.contextMenu.contains(TRADE)).thenReturn(false)
                true
            }

            assertFalse(bot.actionHandler.trading.sendTradeRequest(other,
                beforeRequest = { prerequisites++; true }, canContinue = { true }))

            assertEquals(0, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }

    @Test fun theFullStageRunsItsPrerequisiteOnceAndObservesTheMatchingTrade(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var prerequisites = 0
            val order = ArrayList<String>()
            `when`(bot.output.sendPlayerInteraction(TRADE.index, other)).thenAnswer {
                order += "click"
                open(bot, other)
                true
            }

            val stage = BotTradeRequestStage(bot, other).sendAndAwait(
                beforeRequest = { prerequisites++; order += "prerequisite"; true }, canContinue = { true })

            assertSame(other, stage?.other)
            assertEquals(1, prerequisites)
            assertEquals(listOf("prerequisite", "click"), order)
            verify(bot.output, times(1)).sendPlayerInteraction(TRADE.index, other)
        }
    }

    @Test fun aFailedStagePrerequisiteDoesNotSendAnUnansweredTradeRequest(): Unit = runBlocking {
        withTimeout(5_000) {
            val (bot, other) = ready()
            var prerequisites = 0

            assertNull(BotTradeRequestStage(bot, other).sendAndAwait(
                beforeRequest = { prerequisites++; false }, canContinue = { true }))

            assertEquals(1, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }

    @Test fun anExistingMatchingTradeSkipsThePrerequisiteAndWinsOverCancellation(): Unit = runBlocking {
        withTimeout(5_000) {
            val bot = bot()
            val other = mock(Player::class.java)
            val screen = open(bot, other)
            var prerequisites = 0

            val stage = BotTradeRequestStage(bot, other).sendAndAwait(
                beforeRequest = { prerequisites++; false }, canContinue = { false })

            assertSame(other, stage?.other)
            assertSame(screen, bot.overlays.getOverlay(OfferTradeInterface::class.java))
            assertEquals(0, prerequisites)
            verifyNoInteractions(bot.actionHandler.trading)
        }
    }

    @Test fun priorityCancellationBeforeApproachSkipsThePrerequisiteAndTradingHelper(): Unit = runBlocking {
        withTimeout(5_000) {
            val bot = bot()
            val other = mock(Player::class.java)
            var prerequisites = 0

            assertNull(BotTradeRequestStage(bot, other).sendAndAwait(
                beforeRequest = { prerequisites++; true }, canContinue = { false }))

            assertEquals(0, prerequisites)
            verifyNoInteractions(bot.actionHandler.trading)
        }
    }

    @Test fun aTradeOpenedDuringApproachSkipsThePrerequisiteAndSurvivesAFailedApproachResult(): Unit = runBlocking {
        withTimeout(5_000) {
            val bot = bot()
            val other = mock(Player::class.java)
            var prerequisites = 0
            var permitted = true
            var screen: OfferTradeInterface? = null
            `when`(bot.actionHandler.trading.sendTradeRequest(same(other) ?: other,
                any<() -> Boolean>() ?: { true }, any<() -> Boolean>() ?: { true })).thenAnswer {
                permitted = false
                screen = open(bot, other)
                assertTrue(it.getArgument<() -> Boolean>(1).invoke())
                assertTrue(it.getArgument<() -> Boolean>(2).invoke())
                false
            }

            val stage = BotTradeRequestStage(bot, other).sendAndAwait(
                beforeRequest = { prerequisites++; false }, canContinue = { permitted })

            assertSame(other, stage?.other)
            assertSame(screen, bot.overlays.getOverlay(OfferTradeInterface::class.java))
            assertEquals(0, prerequisites)
            verify(bot.output, never()).sendPlayerInteraction(anyInt(), any(Player::class.java))
        }
    }
}
