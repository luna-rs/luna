package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.*
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Attaches an owned rope to a dungeon entrance when needed, then uses its normal climb interaction.
 * Existing ropes are reused, including ropes attached by another player while the bot approaches.
 *
 * @author lare96
 */
object RopeEntranceCrossing : CrossingHandler {
    const val ID = "rope_entrance"
    const val ROPE = 954
    val entrances = mapOf(3827 to 3828, 3830 to 3831)

    /**
     * Reserves both descents at the surface because item requirements are carried, not cumulative costs.
     * The upper lair only needs the remaining rope. Tied ropes can expire before a planned trip arrives.
     */
    fun requirements(untied: Int): Requirements =
        Requirements(items = listOf(ItemRequirement(ROPE, if (untied == 3827) 2 else 1)))

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the rope crossing has no object"
        val untied = action.objectId ?: return "the rope crossing has no object id"
        val tied = entrances[untied] ?: return "unsupported rope entrance $untied"
        val position = action.objectPosition ?: return "the rope crossing has no position"
        if (context.arrived(leg)) return null
        context.walk(leg.from, 0)?.let { return it }
        var entrance = context.find(position, untied, tied) ?: return "no rope entrance at $position"
        if (entrance.id == untied) {
            if (!context.bot.inventory.contains(ROPE)) return "no rope for entrance at $position"
            if (!world.collisionManager.reached(context.bot, entrance, InteractionPolicy.STANDARD_SIZE)) {
                return "could not reach rope entrance at $position"
            }
            val index = context.bot.inventory.computeIndexForId(ROPE)
            if (index == -1) return "no rope for entrance at $position"
            context.bot.output.useItemOnObject(index, ROPE, entrance)
            if (!waitFor(LegContext.ACTION_TIMEOUT) { context.find(position, tied) != null }) {
                return "rope did not appear at $position"
            }
            entrance = context.find(position, tied) ?: return "rope expired at $position"
        }
        if (!context.handler.interactions.interactOnLeg(1, entrance) && !context.arrived(leg)) {
            return "could not climb rope at $position"
        }
        return if (context.awaitArrival(leg)) null else "did not arrive at ${leg.to}"
    }
}
