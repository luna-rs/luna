package api.bot.webwalk.walk.crossing

import api.bot.webwalk.generate.ArandarCrossingSpec
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.IsafdarForest
import kotlin.time.Duration.Companion.seconds

/**
 * Crosses native Isafdar woodland from safe walking anchors around its exact-movement endpoints.
 *
 * @author lare96
 */
object IsafdarForestCrossing : CrossingHandler {

    const val ID = "isafdar_forest"

    private val LANDING_TIMEOUT = 10.seconds

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the forest crossing has no object"
        val id = action.objectId ?: return "the forest crossing has no object id"
        val position = action.objectPosition ?: return "the forest crossing has no object position"
        if (id !in IsafdarForest.IDS) return "unsupported Isafdar forest $id"
        val forest = context.find(position, id) ?: return "the forest passage is missing at $position"
        if (leg.from.z != position.z || leg.to.z != position.z) return "the forest anchors are on the wrong plane"
        val anchors = ArandarCrossingSpec.forestApproaches(forest)
            ?: return "the forest passage has no safe walking anchors"
        if ((leg.from != anchors.first || leg.to != anchors.second) &&
            (leg.from != anchors.second || leg.to != anchors.first)) return "the forest anchors have changed"
        if (context.bot.isLocked) return "the forest crossing cannot start while locked"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val (start, destination) = IsafdarForest.route(forest, leg.from)
        if (start.computeLongestDistance(leg.from) > 1 ||
            IsafdarForest.route(forest, leg.to).first != destination) return "the forest anchors are on the wrong sides"
        if (!context.handler.interactions.interactOnLeg(1, forest)) return "could not reach the forest passage"
        if (!context.awaitArrival(leg.copy(to = destination), 0, LANDING_TIMEOUT)) {
            return "the forest passage did not reach its native destination $destination"
        }
        return context.walk(leg.to, 0)
    }
}
