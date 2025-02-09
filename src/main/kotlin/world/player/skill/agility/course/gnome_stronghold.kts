package world.player.skill.agility.course

import io.luna.game.action.ClimbAction
import io.luna.game.action.ExactMovementAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import world.player.skill.agility.Agility.course
import world.player.skill.agility.AgilityCourse.GNOME_STRONGHOLD
import world.player.skill.agility.AgilityObstacle.*

/**
 * The gnome stronghold agility course.
 */
course(GNOME_STRONGHOLD) {
    obstacle(id = 2295,
             type = GNOME_LOG_BALANCE,
             action = {
                 object : ExactMovementAction(this, 1, Position(2474, 3429), 762) {
                     override fun onMove() {
                         sendMessage("You walk across the log balance.")
                     }
                 }
             })


    obstacle(id = 2285,
             type = GNOME_OBSTACLE_NET,
             action = {
                 ClimbAction(this, position.translate(0, -2, 1), Direction.SOUTH,
                             "You climb the obstacle net.")
             })


    obstacle(id = 2313,
             type = GNOME_TREE_BRANCH,
             action = {
                 ClimbAction(this, Position(2473, 3420, 2), Direction.SOUTH,
                             "You climb the tree branch.")
             })


    obstacle(id = 2312,
             type = GNOME_BALANCING_ROPE,
             action = {
                 object : ExactMovementAction(this, 1, Position(2483, 3420, 2), 762) {
                     override fun onMove() {
                         sendMessage("You walk across the balancing rope.")
                     }
                 }
             })
    obstacle(id = 4059,
             type = GNOME_BALANCING_ROPE,
             action = {
                 object : ExactMovementAction(this, 1, Position(2477, 3420, 2), 762) {
                     override fun onMove() {
                         sendMessage("You walk across the balancing rope.")
                     }
                 }
             })


    obstacle(id = 2314,
             type = GNOME_TREE_BRANCH_2,
             action = {
                 ClimbAction(this, Position(2486, 3419), Direction.NORTH,
                             "You climb down the tree branch.")
             })


    obstacle(id = 2286,
             type = GNOME_OBSTACLE_NET_2,
             action = {
                 ClimbAction(this, position.translate(0, 2, 0), Direction.NORTH,
                             "You climb the obstacle net.")
             })


    obstacle(id = 154,
             type = GNOME_OBSTACLE_PIPE,
             action = {
                 object : ExactMovementAction(this, 1, Position(2484, 3437, 0), 844, Direction.NORTH_WEST) {
                     override fun onMove() {
                         sendMessage("You climb through the obstacle pipe.")
                     }
                 }
             })
    obstacle(id = 4058,
             type = GNOME_OBSTACLE_PIPE,
             action = {
                 object : ExactMovementAction(this, 1, Position(2487, 3437, 0), 844, Direction.NORTH_WEST) {
                     override fun onMove() {
                         sendMessage("You climb through the obstacle pipe.")
                     }
                 }
             })
}