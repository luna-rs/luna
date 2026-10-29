package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject

/**
 * Crosses an Agility shortcut, such as a pipe or a jump, by clicking it from the tile where the leg starts. The level
 * is checked by the planner from the requirements of the edge, so a bot only gets here with it.
 */
object AgilityShortcutCrossing : CrossingHandler {

    const val ID = "agility_shortcut"

    /**
     * How far from the start of a leg the object that is clicked may be. A shortcut can be made of more than one
     * object, such as a pipe in two halves, and each end is clicked on the half beside it.
     */
    private const val CLICK_RADIUS = 2

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the shortcut has no object"
        val id = action.objectId ?: return "the shortcut has no object id"
        repeat(3) {
            if (context.arrived(leg, 0)) {
                return null
            }
            context.walk(leg.from, 0)?.let { return it }
            val shortcut = nearest(context, leg.from, id) ?: return "no shortcut $id near ${leg.from}"
            if (!context.handler.interactions.interactOnLeg(1, shortcut) && !context.arrived(leg, 0)) {
                return "could not reach shortcut $id at ${shortcut.position}"
            }
            if (context.awaitArrival(leg, 0)) {
                return null
            }
        }
        return "could not cross shortcut $id after 3 attempts"
    }

    /**
     * Finds the object of a shortcut that is closest to where the bot clicks it from.
     */
    private fun nearest(context: LegContext, from: Position, id: Int): GameObject? {
        var best: GameObject? = null
        for (dx in -CLICK_RADIUS..CLICK_RADIUS) {
            for (dy in -CLICK_RADIUS..CLICK_RADIUS) {
                val found = context.find(from.translate(dx, dy), id) ?: continue
                if (best == null || from.computeLongestDistance(found.position) <
                    from.computeLongestDistance(best.position)) {
                    best = found
                }
            }
        }
        return best
    }
}
