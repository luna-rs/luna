package api.bot.webwalk.walk

import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlanner
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What is needed to take a bot along one leg of a plan: to move it about, find things in the world, and wait for it to
 * land. The [WebWalkExecutor] uses it for every leg, and hands it to the code that takes the legs it doesn't take itself,
 * such as the [api.bot.webwalk.walk.crossing.CrossingHandler]s and [api.bot.webwalk.walk.ship.ShipVoyage], so that it is
 * only written once.
 *
 * @property bot The bot.
 * @property handler The actions of the bot.
 *
 * @author Hydrozoa
 */
class LegContext(val bot: Bot, val handler: BotActionHandler) {

    /**
     * Whether another navigation took ownership during this plan. Recovery must not override it.
     */
    var interrupted: Boolean = false
        private set

    companion object {

        /**
         * The longest a walk of a single leg may take. Legs are short, so this only catches a bot that is stuck.
         */
        val WALK_TIMEOUT: Duration = 60.seconds

        /**
         * The longest a walk of a single leg in [WebWalkPlanner.TAVERLEY_DUNGEON] may take, where one leg can wind for over a
         * hundred steps.
         */
        val TAVERLEY_WALK_TIMEOUT: Duration = 2.minutes

        /**
         * How long to wait for a door to open, a ladder to be climbed, or a dialogue to show.
         */
        val ACTION_TIMEOUT: Duration = 10.seconds

        /**
         * How far, in tiles, a bot may land from where a climb says, because landings can be anywhere in an area.
         */
        const val LANDING_SLACK = 8
    }

    /**
     * Determines if the bot is within a number of tiles of a position, on the same plane.
     */
    fun near(position: Position, tiles: Int): Boolean =
        bot.position.z == position.z && bot.position.computeLongestDistance(position) <= tiles

    /**
     * Finds an object by position and one of its ids, or `null` if it is not there.
     */
    fun find(position: Position, vararg ids: Int): GameObject? =
        world.objects.findAll(position).filter { it.id in ids }.findFirst().orElse(null)

    /**
     * Finds the nearest NPC to a position with one of some ids, within a number of tiles, or `null` if there is none.
     */
    fun findNpc(near: Position, ids: Set<Int>, radius: Int): Npc? =
        world.locator.findNpcs(near, radius, true) { it.id in ids }.firstOrNull()

    /**
     * Walks to a position.
     *
     * @param target The position.
     * @param slack How many tiles short the bot may be let go, because another walk comes next, or `0` to stop exactly.
     * @param stopWhen Ends the walk early, with success, as soon as it is `true`.
     * @return `null` if the bot got there, or why it didn't.
     */
    suspend fun walk(target: Position, slack: Int, stopWhen: () -> Boolean = { false }): String? {
        val future: CompletableFuture<NavigationResult> = bot.navigator.navigate(target, true)
        val finished = try {
            val timeout = if (target in WebWalkPlanner.TAVERLEY_DUNGEON) TAVERLEY_WALK_TIMEOUT else WALK_TIMEOUT
            waitFor(timeout) { future.isDone || (slack > 0 && near(target, slack)) || stopWhen() }
        } catch (e: CancellationException) {
            if (bot.navigator.currentPending === future) {
                bot.navigator.cancel()
                bot.walking.clear()
            }
            throw e
        }
        if (!finished) {
            if (bot.navigator.currentPending === future) {
                bot.navigator.cancel()
                bot.walking.clear()
            }
            return "timed out walking to $target from ${bot.position}"
        }
        if (!future.isDone) {
            // Let go short of the target, for the next walk or because the bot has arrived.
            return null
        }
        if (future.isCancelled) {
            interrupted = true
            return "the walk to $target was interrupted"
        }
        val result = future.await()
        return if (result == NavigationResult.REACHED) null else "could not walk to $target from ${bot.position} ($result)"
    }

    /**
     * Determines if the bot has landed where a leg leads.
     */
    fun arrived(leg: PlanLeg, slack: Int = LANDING_SLACK): Boolean = near(leg.to, slack)

    /**
     * Finds how far, in tiles, from where a leg leads the bot may land, which is [LANDING_SLACK] unless the leg stays on
     * one plane and is shorter than that. A bot that is still where the leg starts is then never counted as having
     * arrived, which matters for entrances that only move it a few tiles.
     */
    fun landingSlack(leg: PlanLeg): Int {
        if (leg.from.z != leg.to.z) {
            return LANDING_SLACK
        }
        return minOf(LANDING_SLACK, maxOf(0, leg.from.computeLongestDistance(leg.to) - 1))
    }

    /**
     * Waits until the bot has landed where a leg leads and is free to move again. A climb locks the bot until it is
     * done, and a walk that starts while it is locked is dropped, which is why being in place is not enough.
     *
     * @param leg The leg.
     * @param slack How far, in tiles, from where the leg leads the bot may be.
     * @param timeout How long to wait.
     * @return `true` if the bot landed and was free before the timeout.
     */
    suspend fun awaitArrival(leg: PlanLeg, slack: Int = LANDING_SLACK, timeout: Duration = ACTION_TIMEOUT): Boolean =
        waitFor(timeout) { arrived(leg, slack) && !bot.isLocked }
}
