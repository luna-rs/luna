package engine.interaction.follow;

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob

/**
 * An [Action] implementation that makes a mob follow the target mob.
 *
 * @author lare96
 */
class MobFollowAction(mob: Mob, private val target: Mob) : Action<Mob>(mob, ActionType.WEAK) {

    /**
     * The last position of this mob.
     */
    private var lastPosition: Position? = null

    override fun run(): Boolean {
        val distance = mob.position.computeLongestDistance(target.position)
        if (distance >= 15) {
            return true
        }
        mob.interact(target)
        if (target.position.equals(lastPosition)) {
            return false
        }
        if (mob.position == target.position) {
            mob.navigator.stepRandom(false)
        } else {
            mob.navigator.walkBehind(target, false)
            lastPosition = target.position
        }
        return false
    }

    override fun onFinished() {
        mob.interact(null)
    }
}
