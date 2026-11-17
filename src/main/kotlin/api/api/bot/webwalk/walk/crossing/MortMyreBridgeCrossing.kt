package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.MortMyreBridge

/**
 * Uses the same climb and bridge walk as a player's tree click.
 *
 * @author lare96
 */
object MortMyreBridgeCrossing : CrossingHandler {
    const val ID = "mort_myre_bridge"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Mort Myre bridge leg has no tree"
        val end = MortMyreBridge.End.entries.firstOrNull {
            action.objectId == MortMyreBridge.TREE && action.objectPosition == it.tree &&
                leg.from == it.bank && leg.to == it.opposite.bank
        } ?: return "unsupported Mort Myre bridge crossing"
        if (context.arrived(leg, 0)) return null
        context.walk(end.bank, 0)?.let { return it }
        val tree = context.find(end.tree, MortMyreBridge.TREE) ?: return "the Mort Myre bridge tree is missing"
        // The click starts movement away from the tree, so wait for the landing instead of stationary reach.
        val bot = context.bot
        if (bot.position != end.bank || bot.isLocked || !bot.output.sendObjectInteraction(1, tree)) {
            return "could not use the Mort Myre bridge tree"
        }
        return if (context.awaitArrival(leg, 0)) null else "could not cross the Mort Myre bridge"
    }
}
