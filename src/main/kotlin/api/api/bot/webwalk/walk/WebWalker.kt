package api.bot.webwalk.walk

import api.bot.webwalk.data.WebWalkDataException
import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.BankItems
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.PathfinderWalkEstimator
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.WebWalkPlanner
import api.bot.webwalk.walk.crossing.CrossingHandlers
import api.bot.Suspendable.delay
import api.predef.*
import game.content.sailing.ShipRoute
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
        WebWalkLoader.load(DIRECTORY).also(::checkHandlers)
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
     * Chooses between trips straight to a destination and trips by the bank, with the [planner].
     */
    private val tripPlanner: TripPlanner by lazy { TripPlanner(planner, graph.banks) }

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
     * Checks that every crossing of a web has a handler to cross it, and that every ship is of a [ShipRoute], so that a typo
     * in the data or a route that was renamed fails when the web is loaded instead of in the middle of a trip.
     *
     * @param graph The web.
     * @throws WebWalkDataException If a crossing names a handler, or a ship a route, that doesn't exist.
     */
    fun checkHandlers(graph: WebWalkGraph) {
        val routes = ShipRoute.entries.map { it.name }.toSet()
        val problems = graph.edges.filter { it.type == EdgeType.CROSSING }
            .mapNotNull { it.action?.handler }.distinct().filter { CrossingHandlers.forId(it) == null }
            .map { "crossing handler '$it' does not exist, the handlers are ${CrossingHandlers.ids}" } +
                graph.edges.filter { it.type == EdgeType.SHIP }
                    .mapNotNull { it.action?.key }.distinct().filter { it !in routes }
                    .map { "ship route '$it' does not exist, the routes are $routes" }
        if (problems.isNotEmpty()) {
            throw WebWalkDataException(problems)
        }
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
     * Chooses a trip for a bot, without taking it, on the pool of the planner: straight to the destination with what it
     * carries, or by its nearest bank to withdraw what the rest of the trip spends, whichever costs less (see
     * [TripPlanner]). Must be called on the game thread, because the bot and its bank are read as they are now.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param bank The most of the bank that the trip may use. [BankAccess.NONE] plans with what the bot carries only.
     * @param random Where the variation of the route comes from.
     * @return A future of the trip. It is completed on a thread of the pool, so code that has to run on the game thread
     * should continue on [io.luna.game.GameService.gameExecutor].
     */
    fun planTrip(bot: Bot, destination: Position, bank: BankAccess = BankAccess.ALL,
                 random: Random = Random()): CompletableFuture<TripPlan> {
        val trips = tripPlanner
        val start = bot.position
        // The bank is copied once, for the view and every snapshot.
        val banked = if (bank == BankAccess.NONE) BankItems.EMPTY else BankItems.of(bot)
        val view = if (bank == BankAccess.NONE) BankView() else BankWithdrawal.view(bot, banked)
        val carried = BotCapabilities.snapshot(bot, graph.teleports)
        val tiers = LinkedHashMap<BankAccess, CapabilitySnapshot>()
        for (access in listOf(BankAccess.ALL, BankAccess.COINS)) {
            if (access <= bank) {
                tiers[access] = BotCapabilities.snapshot(bot, graph.teleports, bank = access, banked = banked)
            }
        }
        val intelligence = bot.personality.intelligence
        return CompletableFuture.supplyAsync({ trips.plan(start, destination, carried, tiers, view, intelligence, random) },
                                             planPool)
    }

    /**
     * Plans the trip from the bank that a bot is at, and works out what it would withdraw, on the pool of the planner. Must
     * be called on the game thread, because the bot and its bank are read as they are now.
     *
     * @param bot The bot, at the stand of the bank.
     * @param destination Where it is going.
     * @param access How much of the bank the trip counts.
     * @return A future of the trip from the bank, or of `null` if there is none.
     */
    private fun planFromBankAsync(bot: Bot, destination: Position, access: BankAccess): CompletableFuture<BankStandPlan?> {
        val trips = tripPlanner
        val stand = bot.position
        // The bank is copied again, as it is now, once for the view and the snapshot.
        val banked = BankItems.of(bot)
        val view = BankWithdrawal.view(bot, banked)
        val carried = BotCapabilities.snapshot(bot, graph.teleports)
        val snapshot = BotCapabilities.snapshot(bot, graph.teleports, bank = access, banked = banked)
        val intelligence = bot.personality.intelligence
        return CompletableFuture.supplyAsync({ trips.fromBank(stand, destination, snapshot, carried, view, intelligence) },
                                             planPool)
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
     * The trip may go by the nearest bank first, to withdraw the coins, runes or teleport jewellery that the rest of it
     * spends, when that costs less than the trip with what the bot carries (see [planTrip]). At the bank, the trip is planned
     * again before anything is withdrawn, and the bank is only opened if that trip needs something from it.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @param attempts How many times to try, which is at least 1. The default is to fail fast and leave it to the script.
     * @param bank The most of the bank that the trip may withdraw. [BankAccess.NONE] never goes to the bank.
     * @return How it went, which is that of the last attempt.
     */
    suspend fun webWalk(bot: Bot, destination: Position, radius: Int = 0, attempts: Int = 1,
                        bank: BankAccess = BankAccess.ALL): WebWalkResult {
        require(attempts >= 1) { "attempts must be at least 1, was $attempts" }
        if (isWithin(bot.position, destination, radius)) {
            return WebWalkResult(true, "Already at the destination.")
        }
        missingData()?.let { return WebWalkResult(false, it) }
        return withAttempts(bot, attempts) { walkOnce(bot, destination, radius, bank) }
    }

    /**
     * Walks a bot to the nearest bank, which is the one that the cheapest trip ends at. Must be called from a bot script, on
     * the game thread. It is walked like any other trip, see [webWalk], and the bank that the bot is sent to is in the result.
     * Returns at once if the bot is within the radius of a bank already.
     *
     * The bot ends up on the [api.bot.webwalk.model.BankDefinition.stand] tile of the bank, which is in front of the booth
     * and not behind the counter. The trip is planned with what the bot carries, and never makes a bank detour itself.
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
     * Chooses a trip and takes the bot along it once, with the combat reflex already disabled by the caller.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @param bank The most of the bank that the trip may withdraw.
     * @return How it went.
     */
    private suspend fun walkOnce(bot: Bot, destination: Position, radius: Int, bank: BankAccess): WebWalkResult {
        val trip = try {
            planTrip(bot, destination, bank).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not plan a web walk for ${bot.username}!", e)
            return WebWalkResult(false, "Could not plan the trip: $e")
        }
        return when (trip) {
            is TripPlan.NoTrip -> WebWalkResult(false, trip.reason)
            is TripPlan.Direct -> {
                trip.turnedDown?.let { bot.log("WebWalk: not going by the bank, ${compare(it)}.") }
                follow(bot, trip.plan, destination, radius)
            }
            is TripPlan.ViaBank -> walkByBank(bot, trip, destination, radius)
        }
    }

    /**
     * Takes the bot along a trip by its bank: to the bank, then plans the trip from there again, withdraws what it needs if
     * it needs anything, and takes it. If something goes wrong on the way to the bank or at it, the bot takes the trip with
     * what it carries instead.
     *
     * @param bot The bot.
     * @param trip The trip by the bank.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @return How it went.
     */
    private suspend fun walkByBank(bot: Bot, trip: TripPlan.ViaBank, destination: Position, radius: Int): WebWalkResult {
        bot.log("WebWalk: going by the bank ${trip.bank.id} (${trip.access}), ${compare(trip)}.")
        val toBank = follow(bot, trip.toBank, trip.bank.stand, 0)
        if (!toBank.success) {
            return walkCarried(bot, destination, radius, "could not get to the bank: ${toBank.message}")
        }

        // The trip from the bank is planned again, with the bot and its bank as they are now, before anything is withdrawn.
        val fromBank = try {
            planFromBankAsync(bot, destination, trip.access).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not plan a web walk from a bank for ${bot.username}!", e)
            return WebWalkResult(false, "Could not plan the trip from the bank: $e")
        } ?: return walkCarried(bot, destination, radius, "there is no trip from the bank")
        bot.log("WebWalk: from the bank: ${fromBank.plan}")

        val withdrawal = fromBank.withdrawal
        if (withdrawal == null) {
            bot.log("WebWalk: the trip from the bank needs nothing from it, so the bank isn't opened.")
            return follow(bot, fromBank.plan, destination, radius)
        }
        val items = fromBank.items ?: return walkCarried(bot, destination, radius,
            "${TripPlanner.describe(withdrawal.required)} from the bank doesn't fit in the inventory")

        val handler = bot.actionHandler
        if (!BankWithdrawal.openBank(bot, handler, trip.bank)) {
            return walkCarried(bot, destination, radius, "could not open the bank")
        }
        val withdrawn = BankWithdrawal.withdraw(bot, handler, items)
        handler.widgets.clickCloseInterface()
        bot.log("WebWalk: withdrew ${withdrawn.joinToString { "${it.amount} x ${itemName(it.id)}" }}.")

        // The withdrawal only moved items from the bank to the inventory, so the trip from the bank still holds.
        val short = fromBank.needs.withdrawal(BotCapabilities.snapshot(bot, graph.teleports))
        if (short != null) {
            return walkCarried(bot, destination, radius,
                               "still short of ${TripPlanner.describe(short.required)} after the bank").copy(withdrawn = withdrawn)
        }
        return follow(bot, fromBank.plan, destination, radius).copy(withdrawn = withdrawn)
    }

    /**
     * Plans the trip with what the bot carries, after a trip by the bank didn't work out, and takes it.
     *
     * @param bot The bot.
     * @param destination Where it is going.
     * @param radius How close to the destination, in tiles on the same plane, is close enough.
     * @param why Why the trip by the bank didn't work out.
     * @return How it went.
     */
    private suspend fun walkCarried(bot: Bot, destination: Position, radius: Int, why: String): WebWalkResult {
        bot.log("WebWalk: $why, so the trip is made with what the bot carries.")
        val plan = try {
            planAsync(bot, destination).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not plan a web walk for ${bot.username}!", e)
            return WebWalkResult(false, "Could not plan the trip: $e")
        } ?: return WebWalkResult(false, "No trip from ${bot.position} to $destination with what the bot carries: $why.")
        return follow(bot, plan, destination, radius)
    }

    /**
     * Describes how a trip by the bank compares with the trip without it, for the log, with the costs that were compared.
     */
    private fun compare(trip: TripPlan.ViaBank): String {
        val direct = trip.comparedWith?.let { "%.1f".format(it.cost) } ?: "none"
        return "by the bank ${"%.1f".format(trip.cost)} (with ${planner.costs.bankTicks} at the bank) vs direct $direct"
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
