package game.skill.agility

import game.skill.agility.AgilityObstacle.*

/**
 * Ordered course checkpoints and completion bonuses from Lost City's 377 scripts.
 *
 * @author lare96
 */
enum class AgilityCourse(val level: Int, val bonus: Double, val obstacles: List<AgilityObstacle>) {
    GNOME_STRONGHOLD(1, 39.0, listOf(GNOME_LOG_BALANCE, GNOME_OBSTACLE_NET, GNOME_TREE_BRANCH,
        GNOME_BALANCING_ROPE, GNOME_TREE_BRANCH_2, GNOME_OBSTACLE_NET_2, GNOME_OBSTACLE_PIPE)),
    BARBARIAN_OUTPOST(35, 46.2, listOf(BARBARIAN_ROPESWING, BARBARIAN_LOG_BALANCE, BARBARIAN_OBSTACLE_NET,
        BARBARIAN_BALANCING_LEDGE, BARBARIAN_CRUMBLING_WALL)),
    WILDERNESS(52, 498.9, listOf(WILDERNESS_OBSTACLE_PIPE, WILDERNESS_ROPESWING, WILDERNESS_STEPPING_STONE,
        WILDERNESS_LOG_BALANCE, WILDERNESS_ROCKS)),

    // The 2006 Knowledge Base lists XP for each obstacle and no lap bonus; the zip vine's 300 is its own.
    APE_ATOLL(48, 0.0, listOf(APE_STEPPING_STONE, APE_TROPICAL_TREE, APE_MONKEY_BARS, APE_SKULL_SLOPE, APE_ROPE,
        APE_ZIP_VINE))
}
