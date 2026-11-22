package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.spiritTrees.SpiritTree
import game.content.spiritTrees.SpiritTrees
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * Talks to a spirit tree and selects a destination through its native dialogue.
 *
 * @author lare96
 */
object SpiritTreeCrossing : CrossingHandler {

    const val ID = "spirit_tree"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the spirit tree leg has no Talk-to action"
        val position = action.objectPosition ?: return "the spirit tree leg has no object position"
        val from = SpiritTree.entries.firstOrNull {
            it.objectId == action.objectId && position.isWithinDistance(it.landing, 8)
        } ?: return "unknown spirit tree at $position"
        val to = from.destinations.firstOrNull { it.landing == leg.to }
            ?: return "the spirit tree at $from does not offer ${leg.to}"
        context.walk(leg.from, 0)?.let { return it }
        val bot = context.bot
        val tree = context.find(position, from.objectId)
        if (bot.overlays.hasWindow() || !context.handler.interactions.interactOnLeg(1, tree)) {
            return "could not talk to the spirit tree at $from"
        }

        if (from.objectId == SpiritTrees.YOUNG_TREE) {
            if (!choose(context, SpiritTrees.ACCEPT_YOUNG_TREE)) return "the young spirit tree did not offer travel"
        } else {
            if (!choose(context, SpiritTrees.ASK_DESTINATIONS) || !choose(context, to.option)) {
                return "the spirit tree did not offer travel to $to"
            }
        }
        return if (context.awaitArrival(leg, 0)) null else "the spirit tree did not send the bot to $to"
    }

    /**
     * Waits for the option to appear while the bot's normal dialogue reflex advances the speech.
     */
    private suspend fun choose(context: LegContext, option: String): Boolean =
        waitFor(10.seconds) { context.bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(option) == true } &&
            context.handler.widgets.clickDialogueOption(option)
}
