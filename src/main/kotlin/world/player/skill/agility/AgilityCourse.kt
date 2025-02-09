package world.player.skill.agility

import world.player.skill.agility.AgilityObstacle.*

/**
 * An enum representing agility courses.
 */
enum class AgilityCourse(val level: Int, val bonus: Double, val obstacles: Set<AgilityObstacle>) {
    GNOME_STRONGHOLD(level = 1,
                     bonus = 50.0,
                     obstacles = setOf(GNOME_LOG_BALANCE, GNOME_OBSTACLE_NET, GNOME_TREE_BRANCH,
                                       GNOME_BALANCING_ROPE, GNOME_TREE_BRANCH_2, GNOME_OBSTACLE_NET_2,
                                       GNOME_OBSTACLE_PIPE)),
    BARBARIAN_OUTPOST(level = 35,
                    bonus = 46.3,
                    obstacles = setOf(BARBARIAN_ROPESWING, BARBARIAN_LOG_BALANCE, BARBARIAN_OBSTACLE_NET,
                                      BARBARIAN_BALANCING_LEDGE, BARBARIAN_LADDER, BARBARIAN_CRUMBLING_WALL)),
}