package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.HangarExit
import kotlin.time.Duration.Companion.seconds

/**
 * Leaves the powered military hangar through the native teleportation device's ordinary Operate option.
 *
 * @author lare96
 */
object HangarExitCrossing : CrossingHandler {

    const val ID = "hangar_exit"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the hangar exit leg has no object action"
        if (action.objectId != HangarExit.POWERED_DEVICE || action.objectPosition != HangarExit.POWERED_POSITION ||
            leg.from != HangarExit.POWERED_APPROACH || leg.to != HangarExit.DESTINATION) {
            return "unsupported hangar exit passage"
        }
        if (context.arrived(leg, 0) && !context.bot.isLocked) return null
        context.walk(leg.from, 0)?.let { return it }
        val obj = context.find(HangarExit.POWERED_POSITION, HangarExit.POWERED_DEVICE)
            ?: return "the powered hangar teleportation device is missing"
        if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not operate the hangar exit"
        return if (context.awaitArrival(leg, 0, 10.seconds)) null else "the hangar exit did not reach ${leg.to}"
    }
}
