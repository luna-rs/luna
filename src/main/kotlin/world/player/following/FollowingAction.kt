package world.player.following;

import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.util.RandomUtils

/**
 * A [RepeatingAction] that makes a [Mob] follow another mob.
 */
class FollowingAction(mob: Mob, private val target: Mob) : RepeatingAction<Mob>(mob, true, 1) {

    /**
     * The last position of the mob.
     */
    private var lastPosition: Position = mob.position

    override fun start() = true

    override fun repeat() {
        if (target.state == EntityState.INACTIVE) {
            stop()
            return
        }

        mob.interact(target)
        if (target.position == lastPosition) {
            return
        }
        val distance = mob.position.getEuclideanDistance(target.position)
        if (distance >= 15) {
            stop()
            return
        }

        if (mob.position == target.position) {
            val direction = RandomUtils.random(Direction.NESW)
            mob.walking.walk(target.position.translate(1, direction))
        } else if (!mob.position.isWithinDistance(target.position, 1)) {
            mob.walking.walkBehind(target)
            lastPosition = target.position
        } else {
            mob.walking.clear()
        }
    }

    override fun ignoreIf(other: Action<*>?): Boolean = when (other) {
        is FollowingAction -> target == other.target
        else -> false
    }
}
