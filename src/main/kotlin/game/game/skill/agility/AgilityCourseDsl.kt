package game.skill.agility

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Registers course obstacles and awards their rewards after successful traversal.
 *
 * @author lare96
 */
class AgilityCourseDsl(val courseType: AgilityCourse) {

    /**
     * Registers an obstacle. Only the last Barbarian wall contributes a course checkpoint. [action] only builds the
     * traversal: it is also built for a failed attempt, for its start message. A click that leaves the player beside
     * the obstacle's start tile, within [AgilityApproach.RADIUS] tiles, walks them onto it first.
     */
    fun obstacle(id: Int, type: AgilityObstacle,
                 checkpoint: (GameObject) -> Boolean = { true },
                 action: Player.(GameObject) -> AgilityAction) {
        object1(id) {
            val start = { attempt(plr, type, checkpoint = checkpoint(gameObject)) { action(plr, gameObject) } }
            if (type.startIf(plr.position)) {
                start()
            } else if (!plr.isLocked) {
                AgilityApproach.findStart(plr, type)?.let { plr.submitAction(AgilityApproach(plr, it, start)) }
            }
        }
    }

    /**
     * Selects an outcome after checking the player's position and current level.
     */
    internal fun attempt(plr: Player, type: AgilityObstacle,
                         roll: () -> Double = { rand().nextDouble() },
                         checkpoint: Boolean = true,
                         action: () -> AgilityAction) {
        if (plr.isLocked || !type.startIf(plr.position) || !Agility.checkLevel(plr, type.level)) return
        val traversal = action()
        val failure = type.failure
        if (failure != null && failure.fails(plr.agility.level, roll())) {
            plr.submitAction(AgilityFailureAction(plr, failure, traversal.message))
            return
        }
        traversal.onSuccess = { Agility.complete(plr, courseType, type, checkpoint) }
        plr.submitAction(traversal)
    }

    /**
     * Creates a single continuous walking traversal.
     */
    fun movementAction(plr: Player, destination: Position, walkingAnimationId: Int, message: String,
                       sounds: List<AgilitySound> = emptyList(), endMessage: String? = null,
                       start: () -> Unit = {}) =
        AgilityAction(plr, listOf(AgilityStep(destination,
            maxOf(1, plr.position.computeLongestDistance(destination)), walkingAnimation = walkingAnimationId,
            sounds = sounds)),
            message, start, endMessage)

    /**
     * Creates a climb that changes floors [ticks] after the climbing animation starts.
     */
    fun climbAction(plr: Player, destination: Position, message: String?, ticks: Int = 2,
                    endMessage: String? = null, start: () -> Unit = {}) =
        AgilityAction(plr, listOf(AgilityStep(destination, ticks, animation = 828, jump = true)), message,
            start, endMessage)
}
