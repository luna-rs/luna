package world.player.skill.agility.course

import io.luna.game.action.impl.ClimbAction
import io.luna.game.action.impl.ExactMovementAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.block.ExactMovement
import world.player.skill.agility.Agility.course
import world.player.skill.agility.AgilityCourse.BARBARIAN_OUTPOST
import world.player.skill.agility.AgilityObstacle.*

/**
 * The barbarian outpost agility course.
 */
course(BARBARIAN_OUTPOST) {

    // TODO wrong animation, timing weird, how to make the rope swing??? think it starts out as one animation then changes to another
    obstacle(id = 2282,
             type = BARBARIAN_ROPESWING,
             action = { movementAction(this, Position(2551, 3549), 3067, "You swing across the pit.") })

    obstacle(id = 2294,
             type = BARBARIAN_LOG_BALANCE,
             action = { movementAction(this, Position(2541, 3546), 762, "You walk across the log balance.") })

    obstacle(id = 2284,
             type = BARBARIAN_OBSTACLE_NET,
             action = { ClimbAction(this, Position(2537, 3545, 1), Direction.WEST, "You climb the obstacle net.") })

    obstacle(id = 2302,
             type = BARBARIAN_BALANCING_LEDGE,
             action = {
                 val firstRoute = ExactMovement.to(this, Position(2532, 3547, 1))
                 val secondRoute = ExactMovement.to(this, Position(2532, 3546, 1))

                 val movementAction = object : ExactMovementAction(this) {
                     override fun onStart() {
                         walkingAnimationId = 756
                     }

                     override fun onMoveStart(movement: ExactMovement) {
                         if (movement == firstRoute) {
                             sendMessage("You walk across the balancing ledge.")
                         }
                     }

                     override fun onMoveEnd(movement: ExactMovement) {
                         if (movement == firstRoute) {
                             face(Direction.SOUTH)
                         }
                     }
                 }

                 movementAction.addRoute(firstRoute)
                 movementAction.addRoute(secondRoute)
                 movementAction
             })

    obstacle(id = 3205,
             type = BARBARIAN_LADDER,
             action = { ClimbAction(this, Position(2532, 3546), Direction.SOUTH, "You climb down the ladder.") })

    obstacle(id = 1747,
             type = BARBARIAN_LADDER,
             action = { ClimbAction(this, Position(2532, 3546, 1), Direction.SOUTH, "You climb up the ladder.") })

    obstacle(id = 1948,
             type = BARBARIAN_CRUMBLING_WALL,
             action = { movementAction(this, position.translate(2, 0), 840, "You climb over the wall.") })
}