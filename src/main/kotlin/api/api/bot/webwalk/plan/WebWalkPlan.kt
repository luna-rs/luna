package api.bot.webwalk.plan

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.FairyRing
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import io.luna.game.model.Position

/**
 * One step of a [WebWalkPlan].
 *
 * @property type How the step is taken.
 * @property from Where the step starts.
 * @property to Where the step ends.
 * @property cost What the step cost the planner, in ticks and penalties.
 * @property action What to do to take the step, for doors, ladders and the like.
 * @property teleport The teleport to use, for [EdgeType.TELEPORT].
 * @property fairyRing The ring to arrive at, for [EdgeType.FAIRY_RING].
 * @property requirements What the bot needs to take the step, so that the executor can check that it still does.
 *
 * @author Hydrozoa
 */
data class PlanLeg(val type: EdgeType,
                   val from: Position,
                   val to: Position,
                   val cost: Double,
                   val action: EdgeAction? = null,
                   val teleport: TeleportDefinition? = null,
                   val fairyRing: FairyRing? = null,
                   val requirements: Requirements = Requirements.NONE)

/**
 * A trip from one position to another, as the steps to take in order.
 *
 * @property legs The steps. Each starts where the one before it ends.
 * @property cost What the whole trip cost the planner, in ticks and penalties.
 *
 * @author Hydrozoa
 */
class WebWalkPlan(val legs: List<PlanLeg>, val cost: Double) {

    /**
     * The position that the trip starts at, or `null` if it is empty.
     */
    val start: Position?
        get() = legs.firstOrNull()?.from

    /**
     * The position that the trip ends at, or `null` if it is empty.
     */
    val destination: Position?
        get() = legs.lastOrNull()?.to

    override fun toString(): String {
        val steps = legs.joinToString(" > ") { "${it.type}(${it.to.x},${it.to.y},${it.to.z})" }
        return "WebWalkPlan(cost=${"%.1f".format(cost)}, legs=$steps)"
    }
}
