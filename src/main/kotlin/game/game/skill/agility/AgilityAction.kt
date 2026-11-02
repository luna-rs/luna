package game.skill.agility

import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SoundMessageWriter

/**
 * One timed leg of an agility traversal. Jumps change position after the animation;
 * other legs use exact movement with tick durations converted to client cycles. [moveStart] and [moveEnd] place
 * that movement inside the leg in client cycles; by default it fills the leg.
 * [message] is sent and [begin] runs when the leg starts.
 *
 * @author lare96
 */
data class AgilityStep(val destination: Position, val ticks: Int,
                       val walkingAnimation: Int = -1, val animation: Int = -1,
                       val jump: Boolean = false,
                       val sounds: List<AgilitySound> = emptyList(),
                       val message: String? = null,
                       val begin: () -> Unit = {},
                       val moveStart: Int = 0, val moveEnd: Int = -1) {
    init {
        require(ticks > 0)
        require(moveEnd == -1 || moveEnd > moveStart)
        require(sounds.all { it.tick in 0..ticks })
    }
}

/**
 * A sound emitted on a specific tick of an agility leg, with the client's repeat count and cycle delay.
 *
 * @author lare96
 */
data class AgilitySound(val sound: Sound, val tick: Int = 0, val loops: Int = 1, val delayCycles: Int = 0) {
    init {
        require(tick >= 0)
        require(loops in 1..255)
        require(delayCycles in 0..65535)
    }
}

/**
 * Runs all legs under one lock and reports success only after the last leg finishes.
 * The first leg starts on the tick the lock is taken, and each later leg on the tick the one before it ends. An
 * exact-movement leg that changes the walk animation shows it for that tick and moves on the next.
 * [message] is sent on the first tick and [endMessage] when the last leg finishes.
 * Interrupted traversals restore animations and never award XP or course credit.
 *
 * @author lare96
 */
class AgilityAction(plr: Player, private val steps: List<AgilityStep>,
                    internal val message: String?,
                    private val start: () -> Unit = {},
                    private val endMessage: String? = null) : LockedAction(plr) {

    companion object {

        /**
         * Client cycles in one game tick. The 377 client runs a cycle every 20 ms.
         */
        const val CYCLES_PER_TICK = 30
    }

    internal var onSuccess: () -> Unit = {}
    private var previousAnimation: PlayerModelAnimation? = null
    private var leg = 0
    private var elapsed = 0

    /**
     * Whether the current leg waits this tick for the client to pick up its walk animation before it moves.
     */
    private var preparing = false

    init {
        require(steps.isNotEmpty())
    }

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        message?.let { mob.sendMessage(it) }
        start()
        beginLeg()
    }

    override fun run(): Boolean {
        val step = steps[leg]
        if (preparing) {
            preparing = false
            startLeg(step)
            return false
        }
        elapsed++
        step.sounds.filter { it.tick == elapsed }.forEach { play(it) }
        if (elapsed < step.ticks) return false
        // The position only changes as a leg ends, so the client never shows the player ahead of the move.
        if (step.destination != mob.position) mob.move(step.destination)
        leg++
        if (leg == steps.size) {
            endMessage?.let { mob.sendMessage(it) }
            onSuccess()
            return true
        }
        beginLeg()
        return false
    }

    override fun onUnlock() {
        previousAnimation?.let { mob.model = it }
        mob.flags.flag(UpdateFlag.APPEARANCE)
        mob.animation(Animation(-1))
    }

    /**
     * Sets the current leg's walk animation and starts the leg, or waits a tick first when its exact movement
     * needs the client to pick up a new walk animation.
     */
    private fun beginLeg() {
        val step = steps[leg]
        elapsed = 0
        val model = if (step.walkingAnimation == -1) previousAnimation else
            PlayerModelAnimation.Builder().setStandingId(step.walkingAnimation)
                .setStandingTurnId(step.walkingAnimation).setWalkingId(step.walkingAnimation).build()
        val changed = mob.model != model
        mob.model = model
        mob.flags.flag(UpdateFlag.APPEARANCE)
        preparing = step.walkingAnimation != -1 && !step.jump && changed
        if (preparing) {
            // The client keeps its selected movement sequence during exact movement. Give its normal
            // movement loop one update to select the new idle/turn sequence before the route starts.
            mob.animation(Animation(-1))
        } else {
            startLeg(step)
        }
    }

    /**
     * Starts the current leg: its message, opening sounds, animation and exact movement.
     */
    private fun startLeg(step: AgilityStep) {
        step.message?.let { mob.sendMessage(it) }
        step.begin()
        step.sounds.filter { it.tick == 0 }.forEach { play(it) }
        mob.animation(Animation(step.animation))
        if (!step.jump) {
            val from = mob.position
            val end = if (step.moveEnd == -1) step.ticks * CYCLES_PER_TICK else step.moveEnd
            mob.exactMove(ExactMovement(mob.lastRegion, from, step.destination, step.moveStart, end,
                Direction.between(from, step.destination)))
        }
    }

    private fun play(sound: AgilitySound) =
        mob.queue(SoundMessageWriter(sound.sound.id, sound.loops, sound.delayCycles))
}
