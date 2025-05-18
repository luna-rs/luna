package world.player.skill.agility

import api.attr.Attr
import api.predef.*
import com.google.common.collect.HashMultimap
import io.luna.game.model.mob.Player

/**
 * A utility class for common agility functions.
 */
object Agility {

    /**
     * The completed obstacles player attribute.
     */
    val Player.completedObstacles by Attr.obj(HashMultimap.create<AgilityCourse, AgilityObstacle>())

    /**
     * Creates and registers a new agility course.
     */
    fun course(type: AgilityCourse, init: AgilityCourseDsl.() -> Unit) {
        val receiver = AgilityCourseDsl(type)
        init(receiver)
    }

    /**
     * Checks the player's agility level against [level] and awards [xp] if successful, sends a message otherwise.
     */
    fun checkLevel(plr: Player, level: Int): Boolean {
        if(plr.agility.level >= level) {
            return true
        }
        // TODO proper message https://github.com/luna-rs/luna/issues/384, may need msg parameter
        plr.sendMessage("You need an Agility level of $level to do this.")
        return false
    }

    /**
     * Determines if the player has completed all obstacles on the course.
     */
    fun isCourseCompleted(plr: Player, type: AgilityCourse): Boolean {
        return plr.completedObstacles.get(type).size == type.obstacles.size
    }
}