package game.skill.agility

import api.attr.Attr
import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Agility course progression using Lost City's ordered checkpoints.
 *
 * @author lare96
 */
object Agility {

    /**
     * The rope's swing animation. The 377 ropeswing objects have no animation of their own.
     */
    const val ROPE_SWING_ANIMATION = 497

    /**
     * Each course keeps its own last completed checkpoint.
     */
    val Player.courseProgress by Attr.obj { mutableMapOf<AgilityCourse, Int>() }

    /**
     * Creates and registers a course.
     */
    fun course(type: AgilityCourse, init: AgilityCourseDsl.() -> Unit) {
        init(AgilityCourseDsl(type))
    }

    /**
     * Checks the current level, including temporary boosts and drains.
     */
    fun checkLevel(plr: Player, level: Int): Boolean {
        if (plr.agility.level >= level) return true
        plr.sendMessage("You need an Agility level of $level to do this.")
        return false
    }

    /**
     * Awards obstacle XP only after traversal. Earlier checkpoints rewind progress; skipped ones
     * cannot advance it. Failures leave progress alone, as in the source scripts.
     */
    fun complete(plr: Player, course: AgilityCourse, obstacle: AgilityObstacle, checkpoint: Boolean = true) {
        if (obstacle.xp > 0) plr.agility.addExperience(obstacle.xp)
        val stage = course.obstacles.indexOf(obstacle) + 1
        if (!checkpoint || stage == 0) return
        val previous = plr.courseProgress[course] ?: 0
        if (previous + 1 >= stage) plr.courseProgress[course] = stage
        if (plr.courseProgress[course] == course.obstacles.size) {
            plr.agility.addExperience(course.bonus)
            plr.courseProgress.remove(course)
        }
    }
}
