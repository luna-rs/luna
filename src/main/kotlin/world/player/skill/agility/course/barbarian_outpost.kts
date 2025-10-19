package world.player.skill.agility.course

import io.luna.game.action.impl.ClimbAction
import io.luna.game.action.impl.ExactMovementAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.ExactMovement
import world.player.skill.agility.Agility.course
import world.player.skill.agility.AgilityCourse.BARBARIAN_OUTPOST
import world.player.skill.agility.AgilityObstacle.*

/**
 * The barbarian outpost agility course.
 */
course(BARBARIAN_OUTPOST) {
    // TODO Ropeswing animation clunky.
    obstacle(id = 2282,
             type = BARBARIAN_ROPESWING,
             action = {
                 object : ExactMovementAction(this, ExactMovement.to(this, Position(2551, 3549)), 3067) {
                     override fun onStart() {
                         mob.animation(Animation(751, AnimationPriority.HIGH))
                     }

                     override fun onMoveStart() {
                         it.animate()
                         sendMessage("You swing across the pit.")
                     }
                 }
             })
    obstacle(id = 2294,
             type = BARBARIAN_LOG_BALANCE,
             action = { movementAction(this, Position(2541, 3546), 762, "You walk across the log balance.") })
    obstacle(id = 2284,
             type = BARBARIAN_OBSTACLE_NET,
             action = { ClimbAction(this, Position(2537, 3545, 1), Direction.WEST, "You climb the obstacle net.") })
    obstacle(id = 2302,
             type = BARBARIAN_BALANCING_LEDGE,
             action = {
                 object : ExactMovementAction(this, ExactMovement.to(this, Position(2532, 3547, 1)), 756) {
                     override fun onMoveStart() {
                         sendMessage("You walk across the balancing ledge.")
                     }
                 }
             })
    obstacle(id = 3205,
             type = BARBARIAN_LADDER,
             action = { ClimbAction(this, Position(2532, 3546), Direction.SOUTH, "You climb down the ladder.") })
    obstacle(id = 1747,
             type = BARBARIAN_LADDER,
             action = { ClimbAction(this, Position(2532, 3546, 1), Direction.SOUTH, "You climb up the ladder.") })
    obstacle(id = 1948,
             type = BARBARIAN_CRUMBLING_WALL,
             action = { climbRocks(this) })
}

/**
 * Returns an action that starts the climbing over the last 3 rocks.
 */
fun climbRocks(plr: Player, loop: Int = 0): ExactMovementAction {
    val x = if (loop == 2) 2 else 3 // Last climb doesn't move us as far.
    return object : ExactMovementAction(plr, ExactMovement.to(plr, plr.position.translate(x, 0)), 840) {
        override fun onStart() {
            if (plr.position.x >= 2543) { // Climbed the last rock wall.
                interrupt()
                plr.unlock()
            }
        }

        override fun onMoveStart() {
            plr.sendMessage("You climb over the wall.")
        }

        override fun onMoveEnd() {
            // Submit the same action again, up to 3 times for each rock wall.
            mob.submitAction(climbRocks(plr, loop + 1))
        }
    }
}