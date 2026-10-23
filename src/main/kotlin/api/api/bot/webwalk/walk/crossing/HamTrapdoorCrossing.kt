package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.*
import engine.obj.HamTrapdoor
import kotlin.time.Duration.Companion.seconds

/**
 * Crosses the H.A.M. Hideout trapdoor west of Lumbridge, by picking its lock until it opens and then climbing down, as a
 * player does (see [HamTrapdoor]).
 *
 * The trapdoor only opens for whoever picked it, so the bot asks [HamTrapdoor.isOpen] rather than looking for another
 * object. Each attempt locks the bot until its result is in, and a failure numbs its fingers, which lowers its Thieving
 * level and so its chance. A trapdoor that the bot opened earlier and that hasn't closed again is climbed down at once.
 *
 * @author Hydrozoa
 */
object HamTrapdoorCrossing : CrossingHandler {

    /**
     * The name that the data gives this handler.
     */
    const val ID = "ham_trapdoor"

    /**
     * The number of the closed trapdoor's Pick-Lock option.
     */
    private const val PICK_OPTION = 5

    /**
     * The number of the open trapdoor's Climb-down option.
     */
    private const val CLIMB_OPTION = 1

    /**
     * How many times the lock is tried before giving up on it. At the lowest chance, 4% at level 1, all of them fail about
     * one time in a hundred.
     */
    private const val ATTEMPTS = 100

    /**
     * How long to wait for an attempt to start once the trapdoor was clicked.
     */
    private val ATTEMPT_START_WAIT = 3.seconds

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val bot = context.bot
        if (context.arrived(leg)) {
            return null
        }
        val walked = context.walk(leg.from, 0)
        if (walked != null) {
            return walked
        }
        val trapdoor = context.find(HamTrapdoor.POSITION, HamTrapdoor.ID)
            ?: return "there is no trapdoor ${HamTrapdoor.ID} at ${HamTrapdoor.POSITION}"

        var attempts = 0
        while (!HamTrapdoor.isOpen(bot) && attempts < ATTEMPTS) {
            if (!context.handler.interactions.interact(PICK_OPTION, trapdoor)) {
                return "could not reach the trapdoor at ${HamTrapdoor.POSITION}"
            }
            attempts++

            // An attempt locks the bot until its result is in.
            waitFor(ATTEMPT_START_WAIT) { bot.isLocked || HamTrapdoor.isOpen(bot) }
            if (!waitFor(LegContext.ACTION_TIMEOUT) { !bot.isLocked }) {
                return "the bot is still busy with the lock of the trapdoor at ${HamTrapdoor.POSITION}"
            }
        }
        if (!HamTrapdoor.isOpen(bot)) {
            return "could not pick the lock of the trapdoor in $ATTEMPTS attempts (Thieving ${bot.thieving.level})"
        }

        if (!context.handler.interactions.interact(CLIMB_OPTION, trapdoor)) {
            return "could not reach the open trapdoor at ${HamTrapdoor.POSITION}"
        }
        return if (context.awaitArrival(leg)) null else "the bot did not arrive near ${leg.to}, it is at ${bot.position}"
    }
}
