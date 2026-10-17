package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.predef.ext.*
import game.npc.spawn.alKharid.BorderGate
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * Crosses the toll gate between Lumbridge and Al Kharid, by clicking a leaf of the gate and agreeing to pay the border guard
 * in the dialogue, as a player does (see [BorderGate]).
 *
 * The dialogue is: the bot asks, the guard answers, and then three options are shown, of which the last, "Yes, ok.", pays.
 * The first two lines are continued by the reflex of the bot. The gate takes the toll, opens, and walks the bot through in a
 * single step. If another player is going through, the leaf is not there, so the bot waits for the gate to close again.
 *
 * @author Hydrozoa
 */
object BorderGateCrossing : CrossingHandler {

    /**
     * The name that the data gives this handler.
     */
    const val ID = "border_gate"

    /**
     * The number of the dialogue option that agrees to pay the toll, see [BorderGate.talk].
     */
    const val AGREE_OPTION = 3

    /**
     * How many times the gate is tried before giving up on it.
     */
    private const val ATTEMPTS = 3

    /**
     * How long to wait for the gate to close behind another player.
     */
    private val GATE_WAIT = 10.seconds

    /**
     * How long to wait for the bot to be through after it agreed. The gate itself gives up on a player after 30 ticks.
     */
    private val CROSS_TIMEOUT = 25.seconds

    override suspend fun cross(context: CrossingContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no object"
        val closedId = action.objectId ?: return "the leg has no object id"
        val position = action.objectPosition ?: return "the leg has no object position"
        val bot = context.bot

        var lastProblem = "the gate did not open"
        repeat(ATTEMPTS) {
            if (bot.position == leg.to) {
                return null
            }
            val coins = bot.inventory.computeAmountForId(CapabilitySnapshot.COINS)
            if (coins < BorderGate.TOLL) {
                return "the bot has $coins coins, but the toll is ${BorderGate.TOLL}"
            }
            val walked = context.walk(leg.from, 0)
            if (walked != null) {
                lastProblem = walked
                return@repeat
            }

            // The leaf is gone while the gate is open for somebody else.
            if (context.find(position, closedId) == null && !waitFor(GATE_WAIT) { context.find(position, closedId) != null }) {
                lastProblem = "the gate $closedId at $position is not closed"
                return@repeat
            }
            val leaf = context.find(position, closedId) ?: return@repeat
            if (!context.handler.interactions.interact(1, leaf)) {
                lastProblem = "could not reach the gate $closedId at $position"
                return@repeat
            }
            if (!waitFor(CrossingContext.ACTION_TIMEOUT) { OptionDialogue::class in bot.overlays }) {
                lastProblem = "the guard did not offer the toll"
                return@repeat
            }
            if (!context.handler.widgets.clickDialogueOption(AGREE_OPTION)) {
                lastProblem = "could not agree to pay the toll"
                return@repeat
            }
            if (waitFor(CROSS_TIMEOUT) { bot.position == leg.to && !bot.isLocked }) {
                return null
            }
            lastProblem = "the bot did not get through the gate, it is at ${bot.position}"
        }
        return lastProblem
    }
}
