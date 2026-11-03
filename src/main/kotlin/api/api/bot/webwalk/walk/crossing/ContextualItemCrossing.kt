package api.bot.webwalk.walk.crossing

import api.bot.action.BotNpcDialogues
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.item.teleport.ContextualTravelItems
import game.item.teleport.MysticJewel
import kotlin.time.Duration.Companion.seconds

/**
 * Activates owned native travel items only at their permitted origin, using ordinary inventory and dialogue input.
 * Whistle/bell movement and maze abandonment remain entirely native. No quest stages or maze rewards are invented.
 *
 * @author lare96
 */
object ContextualItemCrossing : CrossingHandler {
    const val ID = "contextual_item"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val item = leg.action?.itemId ?: return "the crossing has no native inventory item"
        val bot = context.bot
        context.walk(leg.from, 0)?.let { return it }
        if (!bot.inventory.contains(item) || bot.overlays.hasWindow()) return "the crossing item is not available"
        val position = bot.position
        val valid = when (item) {
            ContextualTravelItems.WHISTLE -> when {
                position.z == 0 && ContextualTravelItems.WHISTLE_MAINLAND.contains(position) ->
                    leg.to == ContextualTravelItems.REALM_LANDING
                position.z <= 2 && ContextualTravelItems.WHISTLE_REALM.contains(position) ->
                    leg.to == ContextualTravelItems.MAINLAND_LANDING
                else -> false
            }
            ContextualTravelItems.BELL -> leg.to == ContextualTravelItems.BELL_LANDING &&
                ContextualTravelItems.BELL_BRICKS.any { it.isWithinDistance(position, ContextualTravelItems.BELL_RANGE) }
            MysticJewel.ID -> MysticJewel.inMaze(position) && leg.to == MysticJewel.START
            else -> false
        }
        if (!valid) return "the native item cannot travel from $position to ${leg.to}"
        if (item == MysticJewel.ID) {
            val left = BotNpcDialogues.respondVirtual(bot, MysticJewel.BRIAN, listOf("Yes I'd like to leave!"),
                timeout = 15.seconds, completed = { bot.isAlive && context.arrived(leg, 0) }) {
                MysticJewel.inMaze(bot.position) && context.handler.inventory.clickItem(1, item)
            }
            return if (left) null else "Brian's owned maze-departure conversation did not finish"
        }
        if (!context.handler.inventory.clickItem(1, item)) return "the travel item could not be activated"
        return if (context.awaitArrival(leg, 0, 15.seconds)) null else "the native travel item did not reach ${leg.to}"
    }
}
