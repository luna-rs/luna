package game.skill.agility

import io.luna.game.model.Position

/**
 * Entry positions, XP, and failure rules for the three existing agility courses.
 * Level requirements belong to entrances, not every obstacle inside a course.
 * [startIf] tells whether a tile is one the obstacle starts from.
 *
 * @author lare96
 */
enum class AgilityObstacle(val xp: Double,
                           val startIf: Position.() -> Boolean,
                           val level: Int = 0,
                           val failure: AgilityFailure? = null) {
    GNOME_LOG_BALANCE(7.5, { x == 2474 && y == 3436 && z == 0 }),
    GNOME_OBSTACLE_NET(7.5, { x in 2471..2476 && y == 3426 && z == 0 }),
    GNOME_TREE_BRANCH(5.0, { x in 2472..2474 && y in 3421..3423 && z == 1 }),
    GNOME_BALANCING_ROPE(7.5, { x == 2477 && y == 3420 && z == 2 }),
    GNOME_TREE_BRANCH_2(5.0, { x in 2484..2488 && y in 3417..3421 && z == 2 }),
    GNOME_OBSTACLE_NET_2(7.5, { x in 2483..2488 && y == 3425 && z == 0 }),
    GNOME_OBSTACLE_PIPE(7.5, { x in setOf(2484, 2487) && y == 3430 && z == 0 }),

    BARBARIAN_ROPESWING(22.0, { x == 2551 && y == 3554 && z == 0 },
        failure = AgilityFailure.BARBARIAN_ROPE),
    BARBARIAN_LOG_BALANCE(13.7, { x == 2551 && y == 3546 && z == 0 },
        failure = AgilityFailure.BARBARIAN_LOG),
    BARBARIAN_OBSTACLE_NET(8.2, { x == 2539 && y in 3545..3546 && z == 0 }),
    BARBARIAN_BALANCING_LEDGE(22.0, { x == 2536 && y == 3547 && z == 1 },
        failure = AgilityFailure.BARBARIAN_LEDGE),
    BARBARIAN_LADDER(0.0, { x == 2532 && y == 3546 && z in 0..1 }),
    BARBARIAN_CRUMBLING_WALL(13.7, { x in setOf(2535, 2538, 2541) && y == 3553 && z == 0 }),

    WILDERNESS_OBSTACLE_PIPE(12.5, { x == 3004 && y == 3937 && z == 0 }, level = 49),
    WILDERNESS_ROPESWING(20.0, { x == 3005 && y == 3953 && z == 0 },
        failure = AgilityFailure.WILDERNESS_ROPE),
    WILDERNESS_STEPPING_STONE(20.0, { x == 3002 && y == 3960 && z == 0 },
        failure = AgilityFailure.WILDERNESS_STONES),
    WILDERNESS_LOG_BALANCE(20.0, { x == 3002 && y == 3945 && z == 0 },
        failure = AgilityFailure.WILDERNESS_LOG),
    WILDERNESS_ROCKS(0.0, { x in 2993..2995 && y == 3937 && z == 0 })
}
