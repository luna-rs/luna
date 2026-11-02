package game.skill.agility

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * Registers course obstacles and awards their rewards after successful traversal.
 *
 * @author lare96
 */
class AgilityCourseDsl(val courseType: AgilityCourse) {

    /**
     * Whether a player can take this course's obstacles. Obstacles registered with a refusal play it instead for
     * players who can't; a refusal that returns `null` has handled the click itself.
     */
    var canPass: Player.() -> Boolean = { true }

    /**
     * Registers an obstacle. Only the last Barbarian wall contributes a course checkpoint. [action] only builds the
     * traversal: it is also built for a failed attempt, for its start message. A failed attempt plays [fail] when
     * given, or the obstacle's [AgilityFailure] route; [failsIf], when given, decides the failure instead of that
     * route's roll and needs [fail]. A player who fails [canPass] plays [refuse] when given. [allow], when given,
     * sees each click first and returns `false` when it turned the player away itself. A click that leaves the player
     * beside the obstacle's start tile, within [AgilityApproach.RADIUS] tiles, walks them onto it first; [startTile]
     * names that tile when it depends on the object clicked. [interaction] is how close a click must bring them.
     */
    fun obstacle(id: Int, type: AgilityObstacle,
                 checkpoint: (GameObject) -> Boolean = { true },
                 fail: (Player.(GameObject) -> AgilityAction)? = null,
                 refuse: (Player.(GameObject) -> AgilityAction?)? = null,
                 failsIf: (Player.() -> Boolean)? = null,
                 allow: (Player.(GameObject) -> Boolean)? = null,
                 startTile: (Player.(GameObject) -> Position)? = null,
                 interaction: InteractionPolicySupplier = InteractionPolicy.STANDARD_SIZE_BIF,
                 action: Player.(GameObject) -> AgilityAction) {
        require(failsIf == null || fail != null) { "An obstacle's own fail roll needs its own fall." }
        object1(id, interaction) {
            val obj = gameObject
            if (allow != null && !allow(plr, obj)) return@object1
            val start = {
                attempt(plr, type, checkpoint = checkpoint(obj),
                    fail = fail?.let { { it(plr, obj) } },
                    refuse = refuse?.let { { it(plr, obj) } },
                    failsIf = failsIf?.let { { it(plr) } }) { action(plr, obj) }
            }
            val from = startTile?.invoke(plr, obj)
            if (from?.let { plr.position == it } ?: type.startIf(plr.position)) {
                start()
            } else if (!plr.isLocked) {
                val tile = from ?: AgilityApproach.findStart(plr, type)
                tile?.let { plr.submitAction(AgilityApproach(plr, it, start)) }
            }
        }
    }

    /**
     * Selects an outcome after checking the player's position, current level and [canPass].
     */
    internal fun attempt(plr: Player, type: AgilityObstacle,
                         roll: () -> Double = { rand().nextDouble() },
                         checkpoint: Boolean = true,
                         fail: (() -> AgilityAction)? = null,
                         refuse: (() -> AgilityAction?)? = null,
                         failsIf: (() -> Boolean)? = null,
                         action: () -> AgilityAction) {
        if (plr.isLocked || !type.startIf(plr.position) || !Agility.checkLevel(plr, type.level)) return
        if (refuse != null && !plr.canPass()) {
            refuse()?.let { plr.submitAction(it) }
            return
        }
        val traversal = action()
        val failure = type.failure
        if (failsIf?.invoke() ?: (failure != null && failure.fails(plr.agility.level, roll()))) {
            plr.submitAction(fail?.invoke() ?: AgilityFailureAction(plr, failure!!, traversal.message))
            return
        }
        traversal.onSuccess = { Agility.complete(plr, courseType, type, checkpoint) }
        plr.submitAction(traversal)
    }

    /**
     * Creates a single continuous walk across an obstacle, a tile a tick.
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
