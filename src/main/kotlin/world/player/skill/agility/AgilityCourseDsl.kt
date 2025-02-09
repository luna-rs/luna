package world.player.skill.agility

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.skill.agility.Agility.completedObstacles

/**
 * A receiver that helps create our DSL to construct agility courses and their obstacles.
 */
class AgilityCourseDsl(val courseType: AgilityCourse) {

    /**
     * Adds an agility obstacle to this course.
     */
    fun obstacle(id: Int,
                 type: AgilityObstacle,
                 action: Player.(GameObject) -> Action<Player>) {
        object1(id) {
            if (type.startIf(plr) && Agility.checkLevel(plr, courseType.level)) {
                plr.submitAction(action(plr, gameObject))
                plr.completedObstacles.put(courseType, type)
                plr.agility.addExperience(type.xp)
                if (type.bonus(plr) && Agility.isCourseCompleted(plr, courseType)) {
                    plr.agility.addExperience(courseType.bonus)
                    plr.completedObstacles.clear()
                }
            }
        }
    }
}