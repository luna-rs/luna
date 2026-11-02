package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.*
import game.content.shiloFurnace.ShiloFurnace
import game.obj.doors.Doors
import game.skill.thieving.lockedDoor.ArdougneHouseDoorAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses native pick-lock and paid-furnace interactions, with bounded failed-lock retries after arrival.
 * Admission is rechecked on the bot's current skills or carried coins, preserving drains and spent fares.
 *
 * @author lare96
 */
object ProductionAccessCrossing : CrossingHandler {

    const val ID = "production_access"
    const val CHEST_ADMISSION = "ardougne_chest_door_admission"
    const val SOUTH_CHEST_ADMISSION = "ardougne_south_chest_door_admission"
    const val RANGE_ADMISSION = "ardougne_range_door_admission"
    const val CASTLE_ADMISSION = "ardougne_castle_door_admission"
    const val HUT_ADMISSION = "wilderness_hut_door_admission"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the production passage has no action"
        val id = action.objectId ?: return "the production passage has no door id"
        val position = action.objectPosition ?: return "the production passage has no door position"
        val lock = ArdougneHouseDoorAction.Lock.entries.firstOrNull { it.door == id }
        if (lock == null && id != ShiloFurnace.DOOR) return "unsupported production door $id"
        repeat(20) {
            if (context.arrived(leg, 0)) return null
            context.walk(leg.from, 0)?.let { return it }
            if (!waitFor(10.seconds) { context.find(position, id) != null }) return "production door did not close"
            val door = context.find(position, id) ?: return@repeat
            val entering = Doors.isEntering(position, door.direction, context.bot.position)
            if (lock != null && entering && lock.requiresLockpick && !context.bot.inventory.contains(1523))
                return "the door requires a carried lockpick"
            if (lock != null && entering && context.bot.thieving.level < lock.level)
                return "the door requires level ${lock.level} Thieving"
            if (id == ShiloFurnace.DOOR && entering &&
                context.bot.inventory.computeAmountForId(995) < ShiloFurnace.FARE)
                return "Yohnus requires twenty coins for furnace admission"
            if (!context.handler.interactions.interactOnLeg(if (lock != null && entering) 2 else 1, door))
                return "could not reach production door $id"
            if (id == ShiloFurnace.DOOR && entering) {
                val offered = withTimeoutOrNull(10_000) {
                    repeat(12) {
                        if (context.bot.overlays.getOverlay(OptionDialogue::class.java) != null) return@withTimeoutOrNull true
                        if (!context.bot.isAlive || context.bot.state != EntityState.ACTIVE) return@withTimeoutOrNull false
                        context.bot.output.sendContinueDialogue()
                        delay(500)
                    }
                    false
                } == true
                if (!offered || !context.handler.widgets.clickDialogueOption(ShiloFurnace.ACCEPT))
                    return "Yohnus did not offer the furnace admission"
            }
            if (context.awaitArrival(leg, 0, if (id == ShiloFurnace.DOOR) 12.seconds else 4.seconds)) return null
        }
        return "could not cross production door $id after twenty attempts"
    }
}
