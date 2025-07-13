package world.player.skill.agility.course

import world.player.skill.agility.Agility.course
import world.player.skill.agility.AgilityCourse.WILDERNESS

// TODO make course accessible from wilderness
/**
 * The wilderness agility course.
 */
course(WILDERNESS) {
    /*  obstacle(id = 2288,
               type = WILDERNESS_OBSTACLE_PIPE,
               action = {
                   object : ExactMovementAction(this, 1, Position(3004, 3950), 844, Direction.NORTH_WEST) {
                       override fun onMoveStart(movement: ExactMovement) {
                           sendMessage("You climb through the obstacle pipe.")
                       }
                   }
               })

      obstacle(id = 2294,
               type = WILDERNESS_ROPESWING, // TODO fix barbarian first
               action = {
                   object : ExactMovementAction(this, 1, Position(2541, 3546), 762) {
                       override fun onMoveStart(movement: ExactMovement) {
                           sendMessage("You walk across the log balance.")
                       }
                   }
               })

      obstacle(id = 2311,
               type = WILDERNESS_STEPPING_STONE,
               action = {
                   var base = Position(3002, 3960)
                   val routes = LinkedList<Supplier<ExactMovementAction>>()
                   repeat(6) {
                       routes += Supplier<ExactMovementAction> {
                           ExactMovementAction(this@obstacle,
                                                                                           1,
                                                                                           base.translate(-(it + 1), 0),
                                                                                           1604)
                       }
                   }
                   return@obstacle object : ThrottledAction<Player>(this@obstacle, teleportDelay, 1) {
                       override fun execute() {
                           ExactMovementAction.test(this@obstacle, routes)
                       }
                   }
               })

      obstacle(id = 2297,
               type = WILDERNESS_LOG_BALANCE, // TODO fine i think, just test noclipping after
               action = {
                   object : ExactMovementAction(this, 1, Position(2532, 3547, 1), 756) {
                       override fun onMoveStart(movement: ExactMovement) {
                           sendMessage("You walk across the balancing ledge.")
                       }
                   }
               })

      obstacle(id = 3205,
               type = WILDERNESS_ROCKS,
               action = {
                   ClimbAction(this,
                                                                           Position(2533, 3546, 0),
                                                                           Direction.SOUTH,
                                                                           "You climb down the ladder.")
               })*/
}