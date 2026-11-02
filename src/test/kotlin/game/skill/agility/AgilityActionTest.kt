package game.skill.agility

import api.bot.script.InventoryProductionFixtures
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.bot.Bot
import io.luna.net.msg.out.SoundMessageWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Deque

/**
 * Exercises walking requests, exact movement, traversal timing and interruption through the native action queue.
 *
 * @author lare96
 */
class AgilityActionTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private var model = PlayerModelAnimation.DEFAULT
    private val start = Position(2474, 3436)
    private val destination = start.translate(0, -3)
    private var position = start
    private val paths = mutableListOf<List<Position>>()

    @BeforeEach
    fun player() {
        bot = InventoryProductionFixtures.bot()
        model = PlayerModelAnimation.DEFAULT
        position = start
        paths.clear()
        `when`(bot.model).thenAnswer { model }
        doAnswer { model = it.getArgument(0); null }.`when`(bot).setModel(any())
        `when`(bot.position).thenAnswer { position }
        doAnswer { position = it.getArgument(0); null }.`when`(bot).move(any())
        `when`(bot.lastRegion).thenReturn(start)
        `when`(bot.isRunning).thenReturn(true)
        val walking = bot.walking
        doAnswer {
            paths += it.getArgument<Deque<Position>>(0).toList()
            null
        }.`when`(walking).replacePath(any())
    }

    private fun tick() {
        bot.actions.process()
        bot.actions.normalize()
    }

    private fun balance(sounds: List<AgilitySound> = emptyList()) = AgilityAction(bot,
        listOf(AgilityStep(destination, 3, walkingAnimation = 762, sounds = sounds)), "Crossing the log")

    @Test
    fun balancingStartsWithItsReadyModelWalkingPathAndOpeningSound() {
        val action = balance(listOf(AgilitySound(Sound.LOG_BALANCE), AgilitySound(Sound.LOG_BALANCE, tick = 2)))
        bot.actions.submit(action)

        assertEquals(763, model.standingId)
        assertEquals(762, model.standingTurnId)
        assertEquals(762, model.walkingId)
        assertEquals(listOf(listOf(destination)), paths)
        verify(bot.walking).setRunning(false)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())
        verify(bot).queue(any(SoundMessageWriter::class.java))

        tick()
        assertEquals(start, position)
        verify(bot, never()).move(any())
        verify(bot).queue(any(SoundMessageWriter::class.java))

        tick()
        verify(bot, times(2)).queue(any(SoundMessageWriter::class.java))
        verify(bot, never()).move(any())

        tick()
        assertEquals(destination, position)
        verify(bot).move(destination)
        verify(bot, never()).exactMove(any())
        assertEquals(763, model.standingId)
    }

    @Test
    fun traversalCompletesOnItsLastTickAndSettlesBeforeUnlocking() {
        var completed = false
        val action = balance().also { it.onSuccess = { completed = true } }
        bot.actions.submit(action)
        repeat(2) { tick() }
        assertFalse(completed)
        assertFalse(action.isFinished)
        assertEquals(start, position)
        assertEquals(763, model.standingId)

        tick()

        assertTrue(completed)
        assertFalse(action.isFinished)
        assertEquals(destination, position)
        assertEquals(763, model.standingId)
        verify(bot, never()).unlock()

        tick()

        assertTrue(action.isFinished)
        assertSame(PlayerModelAnimation.DEFAULT, model)
        verify(bot.walking).setRunning(true)
        verify(bot).unlock()
    }

    @Test
    fun interruptingWalkingRestoresTheOriginalModelWithoutCompleting() {
        var completed = false
        val original = PlayerModelAnimation.Builder().setStandingId(809).build()
        model = original
        val action = balance().also { it.onSuccess = { completed = true } }
        bot.actions.submit(action)
        tick()
        assertEquals(listOf(listOf(destination)), paths)
        assertEquals(763, model.standingId)
        clearInvocations(bot.walking)

        action.interrupt()

        assertSame(original, model)
        assertFalse(completed)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())
        verify(bot.walking).clear()
        verify(bot).unlock()
    }

    @Test
    fun exactMovementStartsOnSubmissionButChangesPositionOnlyWhenTheLegEnds() {
        val action = AgilityAction(bot,
            listOf(AgilityStep(destination, 2, animation = 749)), "Entering the pipe")
        bot.actions.submit(action)

        val animation = ArgumentCaptor.forClass(Animation::class.java)
        verify(bot).animation(animation.capture())
        assertEquals(749, animation.value.id)
        val route = ArgumentCaptor.forClass(ExactMovement::class.java)
        verify(bot).exactMove(route.capture())
        assertEquals(start, route.value.startPosition)
        assertEquals(destination, route.value.endPosition)
        assertEquals(0, route.value.durationStart)
        assertEquals(60, route.value.durationEnd)
        assertTrue(paths.isEmpty())
        verify(bot, never()).move(any())

        tick()

        assertEquals(start, position)
        assertFalse(action.isFinished)
        verify(bot, never()).move(any())

        tick()

        assertEquals(destination, position)
        verify(bot).move(destination)
        assertTrue(action.isFinished)
        verify(bot).unlock()
    }

    @Test
    fun failedLogApproachWalksInItsBalanceModelAndRestoresItOnInterruption() {
        val failure = AgilityFailure.BARBARIAN_LOG
        position = failure.approach!!.translate(0, 3)
        val action = AgilityFailureAction(bot, failure)
        bot.actions.submit(action)
        verify(bot, never()).exactMove(any())

        assertEquals(763, model.standingId)
        assertEquals(762, model.standingTurnId)
        assertEquals(762, model.walkingId)
        assertEquals(listOf(listOf(failure.approach)), paths)
        verify(bot.walking).setRunning(false)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())

        repeat(2) { tick() }

        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())
        verify(bot, never()).damage(anyInt())
        clearInvocations(bot.walking)
        action.interrupt()
        assertSame(PlayerModelAnimation.DEFAULT, model)
        verify(bot.walking).clear()
        verify(bot).unlock()
    }

    @Test
    fun failuresWithoutAnApproachStillLandOnTheirFirstTick() {
        val failure = AgilityFailure.WILDERNESS_ROPE
        val action = AgilityFailureAction(bot, failure)
        bot.actions.submit(action)

        tick()

        assertTrue(action.isFinished)
        verify(bot).move(failure.landing)
        verify(bot).damage(anyInt())
        verify(bot, never()).exactMove(any())
    }

    @Test
    fun consecutiveLegsWithTheSameMovementModelDoNotAddAnotherPreparationPause() {
        val first = start.translate(0, -1)
        val next = first.translate(0, -2)
        var completed = false
        val action = AgilityAction(bot, listOf(
            AgilityStep(first, 1, walkingAnimation = 762),
            AgilityStep(next, 2, walkingAnimation = 762)), "Crossing the bridge").also {
            it.onSuccess = { completed = true }
        }
        bot.actions.submit(action)

        assertEquals(listOf(listOf(first)), paths)
        tick()
        assertEquals(first, position)
        assertEquals(listOf(listOf(first), listOf(next)), paths)
        assertFalse(completed)
        tick()
        assertEquals(first, position)
        assertFalse(completed)
        tick()

        assertTrue(completed)
        assertFalse(action.isFinished)
        assertEquals(next, position)
        verify(bot, never()).exactMove(any())
        verify(bot).move(first)
        verify(bot).move(next)
        assertEquals(763, model.standingId)
        verify(bot, never()).unlock()

        tick()

        assertTrue(action.isFinished)
        assertSame(PlayerModelAnimation.DEFAULT, model)
        verify(bot).unlock()
    }
}
