package api.bot.webwalk.walk.ship

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.ext.*
import game.content.sailing.ShipRoute
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Takes a ship along its [ShipRoute], as a player does: by talking to its crew on the dock, choosing its [ShipBoarding]
 * options in the dialogue, sailing, and leaving the ship by its gangplank (see `game.content.sailing`).
 *
 * The lines of the crew and the player in between the options are continued by the reflex of the bot. The crew takes the
 * fare and makes its search when the last option is chosen. A bot that is already on the deck at the end of the route, as
 * after an attempt that failed at the gangplank, only crosses the gangplank.
 *
 * Ships are their own kind of edge ([api.bot.webwalk.model.EdgeType.SHIP]), not crossings: the
 * [api.bot.webwalk.walk.WebWalkExecutor] sails its ship legs here directly. They only share the [LegContext], which
 * the executor uses too.
 *
 * @author Hydrozoa
 */
object ShipVoyage {

    /**
     * How far, in tiles, from where the ship is boarded its crew is looked for.
     */
    private const val CREW_RADIUS = 10

    /**
     * How far, in tiles, from where the ship lands a player on its deck the bot may be to count as on it.
     */
    private const val DECK_SLACK = 2

    /**
     * How far, in tiles, from the foot of the gangplank the bot may land, because the port there is the nearest tile to
     * stand on.
     */
    private const val DOCK_SLACK = 2

    /**
     * How many times the ship is tried before giving up on it.
     */
    private const val ATTEMPTS = 3

    /**
     * How much longer than the journey to wait for the bot to be on the deck after the last option.
     */
    private val SAIL_SLACK = 10.seconds

    /**
     * Takes the bot along the ship of a leg. The bot is at or near where the leg starts, and the requirements of the leg
     * have just been checked.
     *
     * @param context What is needed to move the bot and find things.
     * @param leg The ship leg, whose [api.bot.webwalk.model.EdgeAction] names the route and the gangplank.
     * @return `null` if the bot is ashore at the end of the leg, or why it isn't.
     */
    suspend fun sail(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no ship"
        val key = action.key ?: return "the leg has no ship route"
        val route = ShipRoute.entries.firstOrNull { it.name == key } ?: return "there is no ship route '$key'"
        val plankId = action.objectId ?: return "the leg has no gangplank id"
        val plankPosition = action.objectPosition ?: return "the leg has no gangplank position"
        val bot = context.bot

        var lastProblem = "the ship did not sail"
        repeat(ATTEMPTS) {
            if (context.arrived(leg, DOCK_SLACK) && !bot.isLocked) {
                return null
            }
            if (!onDeck(context, route)) {
                val boarded = board(context, leg, route)
                if (boarded != null) {
                    if (context.interrupted) return boarded
                    lastProblem = boarded
                    return@repeat
                }
            }
            val plank = context.find(plankPosition, plankId)
            if (plank == null) {
                lastProblem = "there is no gangplank $plankId at $plankPosition"
                return@repeat
            }
            if (!context.handler.interactions.interactOnLeg(1, plank)) {
                lastProblem = "could not reach the gangplank $plankId at $plankPosition"
                return@repeat
            }
            if (context.awaitArrival(leg, DOCK_SLACK)) {
                return null
            }
            lastProblem = "the bot did not get off the ship, it is at ${bot.position}"
        }
        return lastProblem
    }

    /**
     * Determines if the bot is on the deck of the ship of [route], where the ship lands it.
     */
    private fun onDeck(context: LegContext, route: ShipRoute): Boolean =
        context.near(route.destination, DECK_SLACK) && !context.bot.isLocked

    /**
     * Talks to the crew of [route], chooses its boarding options, and waits until the ship has landed the bot on its
     * deck.
     *
     * @return `null` if the bot is on the deck, or why it isn't.
     */
    private suspend fun board(context: LegContext, leg: PlanLeg, route: ShipRoute): String? {
        val bot = context.bot
        val walked = context.walk(leg.from, 0)
        if (walked != null) {
            return walked
        }
        val crew = context.findNpc(leg.from, route.crew, CREW_RADIUS)
            ?: return "none of the crew ${route.crew} is within $CREW_RADIUS tiles of ${leg.from}"
        if (!context.handler.interactions.interactOnLeg(1, crew)) {
            return "could not reach the crew ${crew.id} at ${crew.position}"
        }
        val boarding = ShipBoarding.of(route) ?: return "there is no way to board the ship of ${route.name}"
        for (option in boarding.options) {
            if (!waitFor(LegContext.ACTION_TIMEOUT) { OptionDialogue::class in bot.overlays }) {
                return "the crew did not offer '$option'"
            }
            if (!context.handler.widgets.clickDialogueOption(option)) {
                return "could not choose '$option'"
            }
            // The options close before the next lines open, so the next wait doesn't find these ones still open.
            waitFor(LegContext.ACTION_TIMEOUT) { OptionDialogue::class !in bot.overlays }
        }
        val journey = (route.ticks * 600L).milliseconds + SAIL_SLACK
        if (!waitFor(journey) { onDeck(context, route) }) {
            return "the bot did not sail to ${route.place}, it is at ${bot.position}"
        }
        return null
    }
}
