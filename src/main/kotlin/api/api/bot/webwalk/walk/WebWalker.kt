package api.bot.webwalk.walk

import api.bot.webwalk.data.WebWalkDataException
import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.PathfinderWalkEstimator
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.WebWalkPlanner
import api.bot.webwalk.walk.crossing.CrossingHandlers
import api.bot.Suspendable.delay
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Walks bots anywhere in the world: plans a trip, and takes the bot along it, through doors and up ladders.
 *
 * This is separate from the zone travel of bots (`BotActionHandler.travelTo`), which it doesn't use and doesn't change. A bot
 * only walks this way when a script, or the `::botwebwalk` command, asks it to.
 *
 * @author Hydrozoa
 */
object WebWalker {

    /**
     * The directory of the files of the web.
     */
    private val DIRECTORY = Path.of("data", "game", "bots", "webwalk")

    /**
     * The web, which is read the first time that it is needed and is shared by all bots. It is not read, and so not kept, while
     * the generated files are missing: a missing file is an empty one to the loader, and an empty web would stay empty.
     */
    private val graph: WebWalkGraph by lazy {
        missingData()?.let { throw WebWalkDataException(listOf(it)) }
        checkHandlers(WebWalkLoader.load(DIRECTORY))
    }

    /**
     * Determines if the generated files of the web are missing, which they are until the server has generated them.
     *
     * @return What to tell the user, or `null` if the files are there.
     */
    private fun missingData(): String? =
        if (Files.exists(DIRECTORY.resolve(WebWalkLoader.WALK_GRAPH))) null
        else "The web-walker data is missing. Start the server with bots enabled to generate it, or run the generateWebWalk Gradle task."

    /**
     * The planner, which is made the first time that it is needed. It reads the web and the collision of the world, and is
     * shared by all bots.
     */
    private val planner: WebWalkPlanner by lazy {
        WebWalkPlanner(graph, PathfinderWalkEstimator(world.collisionManager, doors = DoorCrossings.of(graph)))
    }

    /**
     * The pool that plans run on. It is small and its own, so that plans don't wait for the path searches of the navigators
     * (and the other way around). Its threads don't keep the server from exiting.
     */
    private val planPool: ExecutorService by lazy {
        val count = AtomicInteger()
        Executors.newFixedThreadPool(PLANNER_THREADS) { task ->
            Thread(task, "WebWalkPlanner-${count.incrementAndGet()}").apply { isDaemon = true }
        }
    }

    /**
     * The number of threads that plan trips.
     */
    private const val PLANNER_THREADS = 2

    /**
     * Checks that every crossing of a web has a handler to cross it, so that a typo in the data fails when the web is loaded
     * instead of in the middle of a trip.
     *
     * @param graph The web.
     * @return The same web.
     * @throws WebWalkDataException If a crossing names a handler that doesn't exist.
     */
    fun checkHandlers(graph: WebWalkGraph): WebWalkGraph {
        val problems = graph.edges.filter { it.type == EdgeType.CROSSING }
            .mapNotNull { it.action?.handler }.distinct().filter { CrossingHandlers.forId(it) == null }
            .map { "crossing handler '$it' does not exist, the handlers are ${CrossingHandlers.ids}" }
        if (problems.isNotEmpty()) {
            throw WebWalkDataException(problems)
        }
        return graph
    }

    /**
     * Plans a trip for a bot, without taking it, on the thread that calls it. Must be called on the game thread, because it
     * reads the bot as it is. Prefer [planAsync], which doesn't hold up the game thread.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param random Where the variation of the route comes from.
     * @return The trip, or `null` if the bot can't get there.
     */
    fun plan(bot: Bot, destination: Position, random: Random = Random()): WebWalkPlan? =
        planner.plan(bot.position, destination, BotCapabilities.snapshot(bot, graph.teleports), bot.personality.intelligence,
                     random)

    /**
     * Plans a trip for a bot to the nearest bank, without taking it, on the thread that calls it. Must be called on the game
     * thread, because it reads the bot as it is. Prefer [planToNearestBankAsync], which doesn't hold up the game thread.
     *
     * @param bot The bot.
     * @param random Where the variation of the route comes from.
     * @return The trip, or `null` if the bot can't get to a bank.
     */
    fun planToNearestBank(bot: Bot, random: Random = Random()): WebWalkPlan? =
        planner.planToNearestBank(bot.position, BotCapabilities.snapshot(bot, graph.teleports), bot.personality.intelligence,
                                  random)

    /**
     * Plans a trip for a bot, without taking it, on the pool of the planner. Must be called on the game thread, because the
     * bot is read as it is now, and the search is done on the position and the capabilities that it has now.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param random Where the variation of the route comes from.
     * @return A future of the trip, or of `null` if the bot can't get there. It is completed on a thread of the pool, so
     * code that has to run on the game thread should continue on [io.luna.game.GameService.gameExecutor].
     */
    fun planAsync(bot: Bot, destination: Position, random: Random = Random()): CompletableFuture<WebWalkPlan?> {
        // The web and the planner are made here the first time, on the game thread, rather than on the pool.
        val planner = planner
        val start = bot.position
        val snapshot = BotCapabilities.snapshot(bot, graph.teleports)
        val intelligence = bot.personality.intelligence
        return CompletableFuture.supplyAsync({ planner.plan(start, destination, snapshot, intelligence, random) }, planPool)
    }

    /**
     * Plans a trip for a bot to the nearest bank, without taking it, on the pool of the planner. Must be called on the game
     * thread, because the bot is read as it is now, and the search is done on the position and the capabilities that it has now.
     *
     * @param bot The bot.
     * @param random Where the variation of the route comes from.
     * @return A future of the trip, or of `null` if the bot can't get to a bank. It is completed on a thread of the pool, so
     * code that has to run on the game thread should continue on [io.luna.game.GameService.gameExecutor].
     */
    fun planToNearestBankAsync(bot: Bot, random: Random = Random()): CompletableFuture<WebWalkPlan?> {
        val planner = planner
        val start = bot.position
        val snapshot = BotCapabilities.snapshot(bot, graph.teleports)
        val intelligence = bot.personality.intelligence
        return CompletableFuture.supplyAsync({ planner.planToNearestBank(start, snapshot, intelligence, random) }, planPool)
    }

    /**
     * Loads the web and makes the planner now, so that the first bot to walk doesn't hold up the game thread while it is read.
     * Must be called on the game thread, because the planner reads the collision of the world. Does nothing if it was done
     * already.
     */
    fun warmUp() {
        planner
    }

    /**
     * Determines if a position is close enough to a destination, which is on the same plane and within a number of tiles.
     *
     * @param position The position.
     * @param destination The destination.
     * @param radius How close, in tiles, is close enough.
     * @return `true` if it is.
     */
    fun isWithin(position: Position, destination: Position, radius: Int): Boolean =
        position.z == destination.z && position.computeLongestDistance(destination) <= radius

    /**
     * Walks a bot to a destination. Must be called from a bot script, on the game thread. The trip is planned on the pool of
     * the planner while the script waits, and the walk carries on on the game thread. Returns at once if the bot is within
     * the radius already.
     *
     * If a walk fails and there are attempts left, the bot plans again from wherever it has got to, because it may have
     * been held up on the way, such as at a door that somebody else was using.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @param attempts How many times to try, which is at least 1. The default is to fail fast and leave it to the script.
     * @return How it went, which is that of the last attempt.
     */
    suspend fun webWalk(bot: Bot, destination: Position, radius: Int = 0, attempts: Int = 1): WebWalkResult {
        require(attempts >= 1) { "attempts must be at least 1, was $attempts" }
        if (isWithin(bot.position, destination, radius)) {
            return WebWalkResult(true, "Already at the destination.")
        }
        missingData()?.let { return WebWalkResult(false, it) }
        return withAttempts(bot, attempts) { walkOnce(bot, destination, radius) }
    }

    /**
     * Walks a bot to the nearest bank, which is the one that the cheapest trip ends at. Must be called from a bot script, on
     * the game thread. It is walked like any other trip, see [webWalk], and the bank that the bot is sent to is in the result.
     * Returns at once if the bot is within the radius of a bank already.
     *
     * The bot ends up on the [api.bot.webwalk.model.BankDefinition.stand] tile of the bank, which is in front of the booth
     * and not behind the counter.
     *
     * @param bot The bot.
     * @param radius How close to the tile in front of a booth, in tiles on the same plane, is close enough.
     * @param attempts How many times to try, which is at least 1. A new attempt plans again, and may pick another bank.
     * @return How it went, which is that of the last attempt. [WebWalkResult.bank] is the bank that was walked to.
     */
    suspend fun webWalkToNearestBank(bot: Bot, radius: Int = 0, attempts: Int = 1): WebWalkResult {
        require(attempts >= 1) { "attempts must be at least 1, was $attempts" }
        missingData()?.let { return WebWalkResult(false, it) }
        graph.banks.firstOrNull { isWithin(bot.position, it.stand, radius) }?.let {
            return WebWalkResult(true, "Already at a bank.", bank = it)
        }
        return withAttempts(bot, attempts) { walkToNearestBankOnce(bot, radius) }
    }

    /**
     * Tries a walk a number of times, with the combat reflex of the bot disabled.
     *
     * @param bot The bot.
     * @param attempts How many times to try.
     * @param walk Walks once.
     * @return How it went, which is that of the last attempt.
     */
    private suspend fun withAttempts(bot: Bot, attempts: Int, walk: suspend () -> WebWalkResult): WebWalkResult {
        val disabledBefore = bot.reflex.isDisableCombatReflex
        try {
            bot.reflex.isDisableCombatReflex = true
            var attempt = 1
            while (true) {
                val result = walk()
                if (result.success || attempt >= attempts) {
                    return result
                }
                bot.log("WebWalk: attempt $attempt of $attempts failed, planning again. ${result.message}")
                attempt++
                delay(1.seconds, 3.seconds)
            }
        } finally {
            bot.reflex.isDisableCombatReflex = disabledBefore
        }
    }

    /**
     * Plans a trip and takes the bot along it once, with the combat reflex already disabled by the caller.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @return How it went.
     */
    private suspend fun walkOnce(bot: Bot, destination: Position, radius: Int): WebWalkResult {
        val plan = try {
            planAsync(bot, destination).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not plan a web walk for ${bot.username}!", e)
            return WebWalkResult(false, "Could not plan the trip: $e")
        } ?: return WebWalkResult(false, "No trip from ${bot.position} to $destination.")

        return follow(bot, plan, destination, radius)
    }

    /**
     * Plans a trip to the nearest bank and takes the bot along it once, with the combat reflex already disabled by the caller.
     *
     * @param bot The bot.
     * @param radius How close to the tile in front of the booth, in tiles on the same plane, is close enough.
     * @return How it went.
     */
    private suspend fun walkToNearestBankOnce(bot: Bot, radius: Int): WebWalkResult {
        val plan = try {
            planToNearestBankAsync(bot).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not plan a web walk to a bank for ${bot.username}!", e)
            return WebWalkResult(false, "Could not plan the trip: $e")
        } ?: return WebWalkResult(false, "No trip from ${bot.position} to a bank.")

        val end = plan.destination ?: bot.position
        val bank = graph.banks.firstOrNull { it.stand == end }
            ?: return WebWalkResult(false, "The trip to a bank ends at $end, which is not a bank.", plan)
        return follow(bot, plan, bank.stand, radius).copy(bank = bank)
    }

    /**
     * Takes the bot along a plan, with the combat reflex already disabled by the caller.
     *
     * @param bot The bot.
     * @param plan The trip.
     * @param destination Where the trip ends.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @return How it went.
     */
    private suspend fun follow(bot: Bot, plan: WebWalkPlan, destination: Position, radius: Int): WebWalkResult {
        bot.log("WebWalk: $plan")
        bot.actionHandler.widgets.clickRunning(true)
        val result = WebWalkExecutor(bot, bot.actionHandler, destination, radius).execute(plan)
        bot.log("WebWalk: ${result.message}")
        return result
    }
}
