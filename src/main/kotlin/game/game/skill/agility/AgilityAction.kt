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
 * other legs use exact movement with tick durations converted to client cycles.
 *
 * @author lare96
 */
data class AgilityStep(val destination: Position, val ticks: Int,
                       val walkingAnimation: Int = -1, val animation: Int = -1,
                       val jump: Boolean = false,
                       val sounds: List<AgilitySound> = emptyList()) {
    init {
        require(ticks > 0)
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
 * Interrupted traversals restore animations and never award XP or course credit.
 *
 * @author lare96
 */
class AgilityAction(plr: Player, private val steps: List<AgilityStep>,
                    private val message: String,
                    private val start: () -> Unit = {}) : LockedAction(plr) {

    internal var onSuccess: () -> Unit = {}
    private var previousAnimation: PlayerModelAnimation? = null
    private var leg = 0
    private var elapsed = 0
    private var legPrepared = false

    init {
        require(steps.isNotEmpty())
    }

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        mob.sendMessage(message)
        start()
    }

    override fun run(): Boolean {
        val step = steps[leg]
        if (!legPrepared) {
            val model = if (step.walkingAnimation == -1) previousAnimation else
                PlayerModelAnimation.Builder().setStandingId(step.walkingAnimation)
                    .setStandingTurnId(step.walkingAnimation).setWalkingId(step.walkingAnimation).build()
            val changed = mob.model != model
            mob.model = model
            mob.flags.flag(UpdateFlag.APPEARANCE)
            legPrepared = true
            if (step.walkingAnimation != -1 && !step.jump && changed) {
                // The client keeps its selected movement sequence during exact movement. Give its normal
                // movement loop one update to select the new idle/turn sequence before the route starts.
                mob.animation(Animation(-1))
                return false
            }
        }
        step.sounds.filter { it.tick == elapsed }.forEach {
            mob.queue(SoundMessageWriter(it.sound.id, it.loops, it.delayCycles))
        }
        if (elapsed == 0) {
            mob.animation(Animation(step.animation))
            if (!step.jump) {
                val from = mob.position
                mob.exactMove(ExactMovement(mob.lastRegion, from, step.destination, 0, step.ticks * 20,
                    Direction.between(from, step.destination)))
                mob.move(step.destination)
            }
        }
        if (elapsed++ < step.ticks) return false
        if (step.jump) mob.move(step.destination)
        leg++
        elapsed = 0
        legPrepared = false
        if (leg < steps.size) return false
        onSuccess()
        return true
    }

    override fun onUnlock() {
        previousAnimation?.let { mob.model = it }
        mob.flags.flag(UpdateFlag.APPEARANCE)
        mob.animation(Animation(-1))
    }
}
