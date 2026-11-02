package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.ext.*
import game.npc.spawn.shantayPass.ShantayPass
import io.luna.game.model.mob.dialogue.GiveItemDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native doorway, warning and ticket handover, including the item dialogue the speech reflex leaves open.
 *
 * @author lare96
 */
object ShantayPassCrossing : CrossingHandler {
    const val ID = "shantay_pass"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "Shantay Pass crossing has no object"
        if (action.objectId != ShantayPass.DOORWAY || action.objectPosition != ShantayPass.POSITION) {
            return "unsupported Shantay Pass object"
        }
        val entering = leg.from == ShantayPass.NORTH && leg.to == ShantayPass.SOUTH
        if (!entering && (leg.from != ShantayPass.SOUTH || leg.to != ShantayPass.NORTH)) {
            return "unsupported Shantay Pass direction"
        }
        val bot = context.bot
        if (!ShantayPass.canTravel(bot) || context.interrupted) return "the Shantay Pass journey was interrupted"
        if (context.arrived(leg, 0)) return null
        if (entering && !bot.inventory.contains(ShantayPass.PASS)) return "a Shantay pass is required to enter the desert"
        context.walk(leg.from, 0)?.let { return it }
        if (!ShantayPass.canTravel(bot)) return "the Shantay Pass journey was interrupted"
        val doorway = context.find(ShantayPass.POSITION, ShantayPass.DOORWAY) ?: return "Shantay's doorway is missing"
        val previousAdmission = ShantayPass.admission(bot)
        var admission: Any? = null
        val crossed = try {
            if (!context.handler.interactions.interactOnLeg(1, doorway) && !context.arrived(leg, 0)) {
                return "could not reach Shantay's doorway"
            }
            admission = ShantayPass.admission(bot).takeUnless { it === previousAdmission }
            withTimeoutOrNull(60.seconds) {
                while (!context.arrived(leg, 0)) {
                    if (admission == null) admission = ShantayPass.admission(bot).takeUnless { it === previousAdmission }
                    if (!ShantayPass.canTravel(bot) || context.interrupted || !advanceDialogue(context)) {
                        return@withTimeoutOrNull false
                    }
                    delay(600)
                }
                ShantayPass.canTravel(bot)
            }
        } finally {
            ShantayPass.cancelAdmission(bot,
                admission ?: ShantayPass.admission(bot).takeUnless { it === previousAdmission })
        } == true
        if (crossed) bot.overlays.closeWindows()
        return if (crossed) null else "the Shantay guard did not let the bot through"
    }

    /**
     * Answers only the native warning and explicitly continues the display-only ticket dialogue.
     */
    internal fun advanceDialogue(context: LegContext): Boolean {
        val bot = context.bot
        val options = bot.overlays[OptionDialogue::class]
        if (options != null) {
            return ShantayPass.AGREE in options.options && context.handler.widgets.clickDialogueOption(ShantayPass.AGREE)
        }
        return bot.overlays[GiveItemDialogue::class] == null || bot.output.sendContinueDialogue()
    }
}
