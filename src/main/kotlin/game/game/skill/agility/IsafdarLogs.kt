package game.skill.agility

import api.predef.*
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The three native log bridges through Isafdar, shown on the ground from their bridged map plane.
 *
 * @author lare96
 */
object IsafdarLogs {

    const val REQUIRED_LEVEL = 45
    val ENDS = mapOf(
        3931 to listOf(Position(2197, 3237, 1), Position(2201, 3237, 1)),
        3932 to listOf(Position(2259, 3250, 1), Position(2263, 3250, 1)),
        3933 to listOf(Position(2290, 3233, 1), Position(2290, 3238, 1)))

    /**
     * Finds the native approach and landing tiles for a log end, on the rendered ground plane.
     */
    fun sides(id: Int, end: Position): Pair<Position, Position> {
        val ground = Position(end.x, end.y)
        return if (id == 3933) {
            val direction = if (end.y < 3235) 1 else -1
            ground.translate(0, -direction) to ground.translate(0, 6 * direction)
        } else {
            val center = if (id == 3931) 2200 else 2261
            val direction = if (end.x < center) 1 else -1
            ground.translate(-direction, 0) to ground.translate(5 * direction, 0)
        }
    }

    /**
     * Crosses the selected end using the balance walk and native intermediate pauses.
     */
    fun cross(plr: Player, log: GameObject) {
        if (log.position !in (ENDS[log.id] ?: return) || plr.isLocked || !plr.canTraverseIsafdar() ||
            !world.collisionManager.isLoweredOnto(log, plr.z) || !Agility.checkLevel(plr, REQUIRED_LEVEL)) return
        val (start, destination) = sides(log.id, log.position)
        if (plr.position.computeLongestDistance(start) > AgilityApproach.RADIUS) return
        val ground = Position(log.position.x, log.position.y)
        val vertical = log.id == 3933
        val dx = if (vertical) 0 else if (destination.x > start.x) 1 else -1
        val dy = if (!vertical) 0 else if (destination.y > start.y) 1 else -1
        val first = ground.translate(if (vertical) 0 else dx, if (vertical) 2 * dy else 0)
        val second = first.translate(2 * dx, 2 * dy)
        val steps = listOf(
            AgilityStep(ground, 1, walkingAnimation = 762, sounds = listOf(AgilitySound(Sound.LOG_BALANCE, loops = 2))),
            AgilityStep(first, if (vertical) 2 else 1, walkingAnimation = 762),
            AgilityStep(second, 2, walkingAnimation = 762, sounds = listOf(AgilitySound(Sound.LOG_BALANCE, loops = 2))),
            AgilityStep(second, 1, walkingAnimation = 762, jump = true),
            AgilityStep(destination, 2, walkingAnimation = 762, sounds = listOf(AgilitySound(Sound.LOG_BALANCE, loops = 2))))
        val enter = {
            if (!plr.isLocked && plr.canTraverseIsafdar() && plr.position == start &&
                Agility.checkLevel(plr, REQUIRED_LEVEL)) {
                plr.submitAction(AgilityAction(plr, steps, "You balance across the log.",
                    valid = { plr.canTraverseIsafdar() }))
            }
        }
        if (plr.position == start) enter() else plr.submitAction(AgilityApproach(plr, start) { enter() })
    }
}
