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
import world.player.skill.agility.AgilityCourse.WILDERNESS
import world.player.skill.agility.AgilityObstacle.*

/**
 * The wilderness agility course.
 */
course(WILDERNESS) {
    // TODO Same ropeswing issue as barbarian outpost.
    obstacle(id = 2288,
             type = WILDERNESS_OBSTACLE_PIPE,
             action = {
                 object : ExactMovementAction(this, ExactMovement.to(this, Position(3004, 3950)), 844) {
                     override fun onStart() {
                         mob.animation(Animation(749, AnimationPriority.HIGH))
                     }

                     override fun onMoveStart() {
                         sendMessage("You climb through the obstacle pipe.")
                     }

                     override fun onMoveEnd() {
                         mob.animation(Animation(748, AnimationPriority.HIGH))
                     }
                 }
             })
    obstacle(id = 2283,
             type = WILDERNESS_ROPESWING,
             action = {
                 object : ExactMovementAction(this, ExactMovement.to(this, Position(3005, 3958)), 3067) {
                     override fun onStart() {
                         face(Direction.NORTH)
                         mob.animation(Animation(751, AnimationPriority.HIGH))
                     }

                     override fun onMoveStart() {
                         it.animate()
                         sendMessage("You swing across the pit.")
                     }
                 }
             })
    obstacle(id = 2311,
             type = WILDERNESS_STEPPING_STONE,
             action = { steppingStone(this) })
    obstacle(id = 2297,
             type = WILDERNESS_LOG_BALANCE,
             action = {
                 movementAction(this, Position(2994, 3945), 762, "You walk across the balancing ledge.")
             })
    obstacle(id = 2328,
             type = WILDERNESS_ROCKS,
             action = { ClimbAction(this, Position(2995, 3933), Direction.SOUTH, "You climb up the rocks.") })
}

/**
 * Returns an action that starts the last 6 stepping stones.
 */
fun steppingStone(plr: Player, loop: Int = 0): ExactMovementAction {
    return object : ExactMovementAction(plr, ExactMovement.to(plr, plr.position.translate(-1, 0)), 1604) {
        override fun onStart() {
            if (plr.position.x <= 2996) { // Climbed the last stepping stone.
                interrupt()
                plr.unlock()
            }
        }

        override fun onMoveEnd() {
            // Submit the same action again, up to 6 times for each stepping stone.
            mob.submitAction(steppingStone(plr, loop + 1))
        }
    }
}