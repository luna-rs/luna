package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.MournerBasement
import game.obj.entrances.PortPhasmatysBarrier
import game.obj.doors.MournerHeadquartersDoors
import game.obj.doors.ArdougneWallDoors
import game.skill.agility.GnomeStrongholdRocks
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.TextDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native Stronghold rocks, Ardougne and Mourner passages and Phasmatys barriers through object clicks.
 * The rock level is rechecked after approach, and entering the city advances its actual Continue page.
 *
 * @author lare96
 */
object NativeAccessCrossing : CrossingHandler {

    const val ID = "native_access"
    const val ROCKS_READY = "stronghold_rocks_ready"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? =
        NativeDialogueControl.own(context.bot) { crossOwned(context, leg) }

    private suspend fun crossOwned(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the native access leg has no object action"
        val id = action.objectId ?: return "the native access leg has no object id"
        val position = action.objectPosition ?: return "the native access leg has no object position"
        val basement = id == MournerBasement.CLOSED && position == MournerBasement.POSITION &&
            leg.to == MournerBasement.LANDING
        val headquarters = MournerHeadquartersDoors.PASSAGES.firstOrNull {
            id == MournerHeadquartersDoors.ID && it.position == position &&
                (it.from == leg.from && it.to == leg.to || it.to == leg.from && it.from == leg.to)
        }
        val cityWall = ArdougneWallDoors.PASSAGES.firstOrNull {
            it.id == id && it.position == position && it.from == leg.from && it.to == leg.to
        }
        val rocks = GnomeStrongholdRocks.PASSAGES.firstOrNull {
            it.id == id && it.position == position && it.from == leg.from && it.to == leg.to
        }
        val barrier = PortPhasmatysBarrier.PASSAGES.firstOrNull {
            id == PortPhasmatysBarrier.ID && it.position == position &&
                (it.outside == leg.from && it.inside == leg.to || it.inside == leg.from && it.outside == leg.to)
        }
        if (!basement && headquarters == null && cityWall == null && rocks == null && barrier == null) {
            return "unsupported native access passage"
        }
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        if (rocks != null && !GnomeStrongholdRocks.canTraverse(context.bot)) {
            return "the Stronghold rocks require current Agility ${GnomeStrongholdRocks.LEVEL}"
        }
        // These native temporary open states have no menu action; wait for the closed clickable object to return.
        val temporaryOpen = when {
            basement -> MournerBasement.OPEN
            headquarters != null -> MournerHeadquartersDoors.OPEN_ID
            else -> null
        }
        val openPosition = headquarters?.to ?: position
        val obj = if (temporaryOpen != null) withTimeoutOrNull(8_000) {
            while (context.find(position, id) == null) {
                if (context.interrupted || context.bot.state != EntityState.ACTIVE || !context.bot.isAlive ||
                    context.bot.position != leg.from) return@withTimeoutOrNull null
                if (context.find(openPosition, temporaryOpen) == null) return@withTimeoutOrNull null
                delay(500)
            }
            context.find(position, id)
        } else context.find(position, id)
        if (obj == null) return "native access object $id is missing at $position"
        if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not use native access object $id"
        if (barrier == null || leg.from == barrier.inside) {
            return if (context.awaitArrival(leg, 0, 8.seconds)) null else "the native passage did not reach ${leg.to}"
        }
        val bot = context.bot
        var ownedPage: TextDialogue? = null
        var continuedPage: TextDialogue? = null
        var entered = false
        try {
            entered = withTimeoutOrNull(12_000) {
                repeat(20) {
                    if (context.arrived(leg, 0) && !bot.isLocked) return@withTimeoutOrNull true
                    if (bot.state != EntityState.ACTIVE || !bot.isAlive) return@withTimeoutOrNull false
                    if (bot.position != leg.from && !bot.isLocked) return@withTimeoutOrNull false
                    val page = bot.overlays.getOverlay(TextDialogue::class.java)
                    if (!bot.isLocked && page != null) {
                        if (page.text != listOf(PortPhasmatysBarrier.ENTRY_MESSAGE)) return@withTimeoutOrNull false
                        ownedPage = page
                        if (page !== continuedPage) {
                            if (!bot.output.sendContinueDialogue()) return@withTimeoutOrNull false
                            continuedPage = page
                        }
                    }
                    delay(500)
                }
                false
            } == true
        } finally {
            if (!entered && !bot.isLocked && ownedPage != null &&
                bot.overlays.getOverlay(TextDialogue::class.java) === ownedPage) {
                bot.overlays.closeWindows()
            }
        }
        return if (entered) null else "the Phasmatys entry confirmation did not finish"
    }
}
