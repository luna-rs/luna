package game.skill.agility

import io.luna.game.model.mob.Player

/**
 * An enum representing agility obstacles on an [AgilityCourse] types.
 *
 * @author lare96
 */
enum class AgilityObstacle(val startIf: Player.() -> Boolean = { true },
                           val xp: Double,
                           val bonus: Player.() -> Boolean = { false }) {

    // Gnome course obstacles.
    GNOME_LOG_BALANCE(startIf = { x == 2474 && y == 3436 },
                      xp = 10.0),
    GNOME_OBSTACLE_NET(xp = 10.0),
    GNOME_TREE_BRANCH(xp = 6.5),
    GNOME_BALANCING_ROPE(xp = 10.0),
    GNOME_TREE_BRANCH_2(xp = 6.5),
    GNOME_OBSTACLE_NET_2(xp = 10.0),
    GNOME_OBSTACLE_PIPE(startIf = { (x == 2484 && y == 3430) || (x == 2487 && y == 3430) },
                        xp = 7.5,
                        bonus = { true }),

    // Barbarian outpost obstacles.
    BARBARIAN_ROPESWING(startIf = { x == 2551 && y == 3554 },
                        xp = 22.0),
    BARBARIAN_LOG_BALANCE(startIf = { x == 2551 && y == 3546 },
                          xp = 13.7),
    BARBARIAN_OBSTACLE_NET(startIf = { x == 2539 && (y == 3545 || y == 3546) },
                           xp = 8.2),
    BARBARIAN_BALANCING_LEDGE(xp = 22.0),
    BARBARIAN_LADDER(startIf = { x == 2532 && y == 3546 },
                     xp = 1.0),
    BARBARIAN_CRUMBLING_WALL(startIf = { (x == 2535 && y == 3553) || (x == 2538 && y == 3553) || (x == 2541 && y == 3553) },
                             xp = 13.7,
                             bonus = { true }),

    // Wilderness obstacles.
    WILDERNESS_OBSTACLE_PIPE(startIf = { x == 3004 && y == 3937 },
                             xp = 12.5),
    WILDERNESS_ROPESWING(startIf = { x == 3005 && y == 3953 },
                         xp = 20.0),
    WILDERNESS_STEPPING_STONE(startIf = { x == 3002 && y == 3960 },
                              xp = 20.0),
    WILDERNESS_LOG_BALANCE(startIf = { x == 3002 && y == 3945 },
                           xp = 20.0),
    WILDERNESS_ROCKS(xp = 1.0,
                     bonus = { true })
}