package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.KaramjaLog
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native river-log action and accepts its original opposite-bank wash-up before walking to the landing.
 * A failed balance retains its damage and experience; it is never replaced by an ordinary walking bridge.
 *
 * @author lare96
 */
object KaramjaLogCrossing : CrossingHandler {
    const val ID = "karamja_log"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Karamja log has no object"
        val passage = KaramjaLog.PASSAGES.firstOrNull {
            action.objectId == KaramjaLog.ID && action.objectPosition == it.position &&
                leg.from == it.from && leg.to == it.to
        } ?: return "unsupported Karamja log passage"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val log = context.find(passage.position, KaramjaLog.ID) ?: return "the Karamja log is missing"
        if (!context.handler.interactions.interactOnLeg(1, log)) return "could not reach the Karamja log"
        val crossed = waitFor(15.seconds) {
            !context.bot.isLocked && (context.bot.position == passage.to || context.bot.position == passage.washUp)
        }
        if (!crossed) return "could not cross the Karamja log"
        return if (context.arrived(leg, 0)) null else context.walk(leg.to, 0)
    }
}
