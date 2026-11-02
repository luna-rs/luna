package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.IsafdarLogs
import kotlin.math.abs

/**
 * Clicks a log's raw bridged map placement while walking and landing on its rendered ground plane.
 *
 * @author lare96
 */
object IsafdarLogCrossing : CrossingHandler {

    const val ID = "isafdar_log"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val id = leg.action?.objectId ?: return "the log crossing has no object id"
        val ends = IsafdarLogs.ENDS[id] ?: return "unsupported Isafdar log $id"
        context.walk(leg.from, 0)?.let { return it }
        val position = ends.minBy { abs(it.x - leg.from.x) + abs(it.y - leg.from.y) }
        val log = context.find(position, id) ?: return "the bridged log is missing"
        if (!context.handler.interactions.interactOnLeg(1, log)) return "could not reach the bridged log"
        return if (context.awaitArrival(leg, 0)) null else "the log crossing did not reach ${leg.to}"
    }
}
