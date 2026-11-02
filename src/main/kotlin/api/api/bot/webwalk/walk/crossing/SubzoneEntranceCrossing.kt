package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.SwampCaveEntrance
import game.obj.entrances.WaterfallApproach
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player

/**
 * Uses native personal-rope and Waterfall approach interactions.
 * Resources and worn admission are checked again after walking. These crossings never move the bot
 * directly: normal object/item messages invoke the same native actions as a player's clicks.
 *
 * @author lare96
 */
object SubzoneEntranceCrossing : CrossingHandler {
    const val ID = "subzone_entrance"
    const val SWAMP_READY = "swamp_caves_ready"
    const val WATERFALL_AMULET = "waterfall_worn_amulet"

    /**
     * A personal rope can be reused; a first descent consumes one carried rope in the native handler.
     */
    fun canEnterSwamp(player: Player) = SwampCaveEntrance.canEnter(player)

    /**
     * The existing Waterfall door requires the amulet to be worn, rather than merely owned.
     */
    fun canEnterWaterfall(player: Player) = player.equipment[Equipment.AMULET]?.id == WaterfallApproach.AMULET

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the native entrance leg has no action"
        val id = action.objectId ?: return "the native entrance leg has no object id"
        context.walk(leg.from, 0)?.let { return it }
        val bot = context.bot
        val position = action.objectPosition ?: return "the native entrance leg has no object position"
        val obj = context.find(position, id) ?: return "native entrance $id is missing at $position"
        when (id) {
            SwampCaveEntrance.DARK_HOLE -> if (!canEnterSwamp(bot)) return "no personal rope or carried rope"
            2010 -> if (!canEnterWaterfall(bot)) return "Glarial's amulet is not worn"
            WaterfallApproach.ROCK, WaterfallApproach.TREE -> {
                val index = bot.inventory.computeIndexForId(WaterfallApproach.ROPE)
                if (index == -1) return "no rope for the Waterfall crossing"
                // Rock throwing is a native ranged item-on-object interaction from its northern island.
                bot.output.useItemOnObject(index, WaterfallApproach.ROPE, obj)
                return if (context.awaitArrival(leg, 0)) null else "did not cross with the Waterfall rope"
            }
            WaterfallApproach.RAFT, WaterfallApproach.BARREL -> Unit
            else -> return "unsupported native entrance $id"
        }
        if (!context.handler.interactions.interactOnLeg(1, obj) && !context.arrived(leg, 0))
            return "could not use native entrance $id"
        return if (context.awaitArrival(leg, 0)) null else "native entrance $id did not reach ${leg.to}"
    }
}
