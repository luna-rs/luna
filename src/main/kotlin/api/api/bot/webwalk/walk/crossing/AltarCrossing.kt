package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.*
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Enters ruins with a worn tiara or a carried talisman, and leaves through altar portals.
 *
 * @author lare96
 */
object AltarCrossing : CrossingHandler {

    const val ID = "runecrafting_altar"

    /**
     * The capability flag for entering [altar].
     */
    fun flag(altar: Altar): String = "altar_entry_${altar.name.lowercase()}"

    /**
     * Whether the player has the access item in the location used by enterAltar.
     */
    fun canEnter(player: Player, altar: Altar): Boolean =
        player.equipment.get(Equipment.HEAD)?.id == altar.tiara || player.inventory.contains(altar.talisman)

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the altar leg has no action"
        val id = action.objectId ?: return "the altar leg has no object id"
        val position = action.objectPosition ?: return "the altar leg has no object position"
        val entry = Altar.OBJECT_TO_ALTAR[id]
        if (entry == null && id !in Altar.PORTAL_TO_ALTAR) return "unsupported altar object $id"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val obj = context.find(position, id) ?: return "altar object $id is missing at $position"
        val bot = context.bot
        if (entry != null && !canEnter(bot, entry)) return "missing access item for ${entry.name} altar"
        val tiara = entry != null && bot.equipment.get(Equipment.HEAD)?.id == entry.tiara
        if (entry == null || tiara) {
            if (!context.handler.interactions.interactOnLeg(1, obj) && !context.arrived(leg, 0)) {
                return "could not reach altar object $id"
            }
        } else {
            // The leg already walked to the interaction tile; do not start another web-walk to use the item.
            if (!world.collisionManager.reached(bot, obj, InteractionPolicy.STANDARD_SIZE)) {
                return "could not reach altar ruins $id"
            }
            val index = bot.inventory.computeIndexForId(entry.talisman)
            if (index == -1) return "missing talisman for ${entry.name} altar"
            bot.output.useItemOnObject(index, entry.talisman, obj)
        }
        if (!context.awaitArrival(leg, 0)) return "did not arrive at altar destination ${leg.to}"
        return if (!bot.walking.isLocked || waitFor(LegContext.ACTION_TIMEOUT) { !bot.walking.isLocked }) null
        else "altar movement is still locked"
    }
}
