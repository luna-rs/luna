package game.skill.agility

import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * The ordinary stile traversal from the revision 377 shortcuts script, with no level check or experience reward.
 *
 * @author lare96
 */
object Stile {

    const val ID = 993

    /**
     * The two arrival tiles, one step clear of each end of the stile.
     */
    fun sides(position: Position, direction: ObjectDirection): Pair<Position, Position> {
        val dx = if (direction == ObjectDirection.NORTH || direction == ObjectDirection.SOUTH) 1 else 0
        val dy = 1 - dx
        return position.translate(-dx, -dy) to position.translate(2 * dx, 2 * dy)
    }

    /**
     * Aligns to the near end, climbs over, then steps clear on the far side.
     */
    fun action(plr: Player, stile: GameObject): AgilityAction? {
        val position = stile.position
        if (plr.state != EntityState.ACTIVE || !plr.isAlive || plr.isLocked || plr.z != position.z ||
            !plr.position.isWithinDistance(position, 2)) {
            return null
        }
        val (low, high) = sides(position, stile.direction)
        val horizontal = low.x != high.x
        val returning = if (horizontal) plr.x > position.x else plr.y > position.y
        val dx = if (horizontal) 1 else 0
        val dy = 1 - dx
        val start = if (returning) position.translate(dx, dy) else position
        val end = if (returning) position else position.translate(dx, dy)
        val destination = if (returning) low else high
        return object : AgilityAction(plr, listOf(
            AgilityStep(start, 1),
            AgilityStep(end, 4, animation = 839, moveStart = 30, moveEnd = 94, animationTick = 1),
            AgilityStep(destination, 1)
        ), null, valid = { plr.state == EntityState.ACTIVE && plr.isAlive }), DeathInterruptible {}
    }
}
