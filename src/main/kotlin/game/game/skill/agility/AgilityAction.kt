package game.skill.agility

import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag

/**
 * One timed leg of an agility traversal. Jumps change position after the animation;
 * other legs use exact movement with tick durations converted to client cycles.
 *
 * @author lare96
 */
data class AgilityStep(val destination: Position, val ticks: Int,
                       val walkingAnimation: Int = -1, val animation: Int = -1,
                       val jump: Boolean = false) {
    init {
        require(ticks > 0)
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
        if (elapsed == 0) {
            mob.model = if (step.walkingAnimation == -1) previousAnimation else
                PlayerModelAnimation.Builder().setStandingId(step.walkingAnimation)
                    .setWalkingId(step.walkingAnimation).build()
            mob.flags.flag(UpdateFlag.APPEARANCE)
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
