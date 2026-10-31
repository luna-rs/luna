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

/**
 * Exercises animation preparation, traversal timing and interruption through the native action queue.
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
    private val destination = Position(2474, 3429)

    @BeforeEach
    fun player() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.model).thenAnswer { model }
        doAnswer { model = it.getArgument(0); null }.`when`(bot).setModel(any())
        `when`(bot.position).thenReturn(start)
        `when`(bot.lastRegion).thenReturn(start)
    }

    private fun tick() {
        bot.actions.process()
        bot.actions.normalize()
    }

    private fun balance(sounds: List<AgilitySound> = emptyList()) = AgilityAction(bot,
        listOf(AgilityStep(destination, 3, walkingAnimation = 762, sounds = sounds)), "Crossing the log")

    @Test
    fun balancingAppearanceArrivesBeforeForcedMovementAndSound() {
        val action = balance(listOf(AgilitySound(Sound.LOG_BALANCE)))
        bot.actions.submit(action)

        tick()

        assertEquals(762, model.standingId)
        assertEquals(762, model.standingTurnId)
        assertEquals(762, model.walkingId)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())
        verify(bot, never()).queue(any(SoundMessageWriter::class.java))

        tick()

        val route = ArgumentCaptor.forClass(ExactMovement::class.java)
        verify(bot).exactMove(route.capture())
        assertEquals(start, route.value.startPosition)
        assertEquals(destination, route.value.endPosition)
        assertEquals(60, route.value.durationEnd)
        verify(bot).move(destination)
        verify(bot).queue(any(SoundMessageWriter::class.java))
        assertEquals(762, model.standingId)
    }

    @Test
    fun preparationDoesNotSpendTraversalTicksOrAwardCompletionEarly() {
        var completed = false
        val action = balance().also { it.onSuccess = { completed = true } }
        bot.actions.submit(action)
        tick()
        repeat(3) { tick() }
        assertFalse(completed)
        assertFalse(action.isFinished)
        assertEquals(762, model.standingId)

        tick()

        assertTrue(completed)
        assertTrue(action.isFinished)
        assertSame(PlayerModelAnimation.DEFAULT, model)
        verify(bot).unlock()
    }

    @Test
    fun interruptedPreparationRestoresTheOriginalModelWithoutMovingOrCompleting() {
        var completed = false
        val original = PlayerModelAnimation.Builder().setStandingId(809).build()
        model = original
        val action = balance().also { it.onSuccess = { completed = true } }
        bot.actions.submit(action)
        tick()

        action.interrupt()

        assertSame(original, model)
        assertFalse(completed)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())
        verify(bot).unlock()
    }

    @Test
    fun explicitActionAnimationsStartWithoutASeparateModelPreparationTick() {
        val action = AgilityAction(bot,
            listOf(AgilityStep(destination, 2, animation = 749)), "Entering the pipe")
        bot.actions.submit(action)

        tick()

        val animation = ArgumentCaptor.forClass(Animation::class.java)
        verify(bot).animation(animation.capture())
        assertEquals(749, animation.value.id)
        verify(bot).exactMove(any())
        verify(bot).move(destination)
    }

    @Test
    fun failedLogApproachAlsoPreparesItsAnimationBeforeMoving() {
        val failure = AgilityFailure.BARBARIAN_LOG
        `when`(bot.position).thenReturn(failure.approach!!.translate(0, 3))
        val action = AgilityFailureAction(bot, failure)
        bot.actions.submit(action)
        verify(bot, never()).exactMove(any())

        tick()

        assertEquals(762, model.standingId)
        assertEquals(762, model.standingTurnId)
        verify(bot, never()).exactMove(any())
        verify(bot, never()).move(any())

        tick()

        verify(bot).exactMove(any())
        verify(bot).move(failure.approach)
        verify(bot, never()).damage(anyInt())
        action.interrupt()
        assertSame(PlayerModelAnimation.DEFAULT, model)
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
        val next = destination.translate(0, -2)
        val action = AgilityAction(bot, listOf(
            AgilityStep(destination, 1, walkingAnimation = 762),
            AgilityStep(next, 2, walkingAnimation = 762)), "Crossing the bridge")
        bot.actions.submit(action)

        repeat(4) { tick() }

        verify(bot, times(2)).exactMove(any())
        verify(bot).move(destination)
        verify(bot).move(next)
        assertEquals(762, model.standingId)
    }
}
