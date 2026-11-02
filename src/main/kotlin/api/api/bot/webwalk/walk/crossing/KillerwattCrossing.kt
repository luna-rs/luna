package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.ext.*
import game.obj.entrances.KillerwattAccess
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Enters through the native flashing-light and equipment warnings, or uses the free return portal.
 * Only the rift's own choices are answered; cancellation closes owned warning dialogue before it can advance later.
 *
 * @author lare96
 */
object KillerwattCrossing : CrossingHandler {

    const val ID = "killerwatt_rift"

    /**
     * Native affirmative responses, retaining the warning rather than changing the player's saved warning preference.
     */
    private const val ENTER = "Yes I still want to go in."
    private const val CONTINUE = "Thanks for the warning, but I'm not scared of any monster."
    private val RESPONSES = setOf(ENTER, CONTINUE)

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Killerwatt crossing has no object"
        val entering = action.objectId == KillerwattAccess.RIFT &&
            action.objectPosition == KillerwattAccess.RIFT_POSITION &&
            leg.from == KillerwattAccess.LAB && leg.to == KillerwattAccess.REALM
        val leaving = action.objectId == KillerwattAccess.PORTAL &&
            action.objectPosition == KillerwattAccess.PORTAL_POSITION &&
            leg.from == KillerwattAccess.REALM && leg.to == KillerwattAccess.LAB
        if (!entering && !leaving) return "unsupported Killerwatt portal direction"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val objectPosition = action.objectPosition ?: return "the Killerwatt object has no position"
        val obj = context.find(objectPosition, action.objectId!!) ?: return "the Killerwatt portal is missing"
        val arrived = try {
            if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not reach the Killerwatt portal"
            withTimeoutOrNull(45.seconds) {
                while (!context.arrived(leg) || context.bot.isLocked) {
                    if (context.interrupted || !context.bot.isAlive) return@withTimeoutOrNull false
                    val options = context.bot.overlays[OptionDialogue::class]
                    if (options != null) {
                        val response = options.options.firstOrNull { it in RESPONSES }
                            ?: return@withTimeoutOrNull false
                        if (!context.handler.widgets.clickDialogueOption(response)) return@withTimeoutOrNull false
                    } else if (ownsDialogue(context.bot)) {
                        context.bot.output.sendContinueDialogue()
                    }
                    delay(600)
                }
                true
            } == true
        } finally {
            if (ownsDialogue(context.bot)) context.bot.overlays.closeWindows()
        }
        return if (arrived) null else "the native Killerwatt portal did not reach ${leg.to}"
    }

    /**
     * Recognizes this crossing's native speaker, options and selected player replies without closing unrelated UI.
     */
    internal fun ownsDialogue(bot: Bot): Boolean =
        bot.overlays[NpcDialogue::class]?.npcId == KillerwattAccess.PROFESSOR ||
            bot.overlays[OptionDialogue::class]?.options?.any { it in RESPONSES } == true ||
            bot.overlays[PlayerDialogue::class]?.lines?.joinToString(" ") in RESPONSES
}
