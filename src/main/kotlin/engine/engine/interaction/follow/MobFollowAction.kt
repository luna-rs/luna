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

    // todo use this in combat for player following instead of whatever you have now
    // todo different types of following. what you have now is escort based following (walk behind)
    // todo need a combat based following where direction doesn't matter (based on optional.empty)
    // todo need line-of-sight as well
    // todo this should also be managed through the navigator

    // todo can probably be removed now?
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
            lastPosition = target.position
        }
        return false
    }

    override fun onFinished() {
        mob.interact(null)
    }
}
