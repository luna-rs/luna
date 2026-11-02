package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.LighthouseBridge
import game.obj.entrances.LighthouseBasementDoor
import io.luna.game.model.EntityState
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native bridge jumps and directional basement walls. A source jump failure still lands on the far bank,
 * one tile before the normal exit; after its damage/action finishes, that final tile is reached by ordinary walking.
 *
 * @author lare96
 */
object LighthouseBridgeCrossing : CrossingHandler {

    const val ID = "lighthouse_bridge"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the bridge jump has no action"
        val id = action.objectId ?: return "the bridge jump has no object"
        val position = action.objectPosition ?: return "the bridge jump has no position"
        context.walk(leg.from, 0)?.let { return it }
        val wall = LighthouseBasementDoor.Wall.entries.firstOrNull { it.id == id }
        if (wall != null) {
            if (position != wall.position || leg.from != wall.from || leg.to != wall.to)
                return "the Lighthouse wall has the wrong direction"
            if (!waitFor(10.seconds) { context.find(position, id) != null }) return "the Lighthouse wall did not close"
            val obj = context.find(position, id) ?: return "the Lighthouse wall is missing"
            if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not open the Lighthouse wall"
            return if (context.awaitArrival(leg, 0)) null else "the Lighthouse wall did not reach its native side"
        }
        val obj = context.find(position, id) ?: return "the bridge jump spot is missing"
        if (LighthouseBridge.start(obj) != leg.from) return "the bridge jump has the wrong start"
        if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not use the bridge jump"
        if (!waitFor(10.seconds) {
            !context.bot.isAlive || context.bot.state != EntityState.ACTIVE ||
                (!context.bot.isLocked && context.near(leg.to, 1))
        }) return "the bridge jump did not reach the far bank"
        if (!context.bot.isAlive || context.bot.state != EntityState.ACTIVE) return "the bridge jump ended on death or logout"
        return if (context.arrived(leg, 0)) null else context.walk(leg.to, 0)
    }
}
