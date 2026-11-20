package engine.bot.coordinator

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.isRealBot
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.RuneCrafterBotScript
import game.bot.scripts.RuneRunnerBotScript
import game.bot.scripts.RuneRunningDialogue
import game.bot.scripts.RuneRunningWaitBotScript
import game.obj.entrances.Zanaris
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.util.RandomUtils
import org.apache.logging.log4j.LogManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Matches runner requests with passive offers on the game thread. Each altar and essence pair has
 * bounded queues. Script lifecycle owns registration, and matching rechecks claims in its own queue.
 *
 * @author lare96
 */
object RuneRunningCoordinator : BotCoordinator {

    const val BATCH = 25
    const val COINS = 995
    const val NATURE_FARE = 60
    internal const val MAX_RUNNERS = 3
    internal const val MAX_TRIPS_PER_RUNNER = 3
    private const val PURE_ESSENCE = 7936
    private const val MAX_HOSTS = 4
    private const val MAX_REQUESTS = 8
    private const val SEARCH_MILLIS = 35_000L
    private const val PREFER_ACTIVE_MILLIS = 8_000L
    private const val ACTIVE_OFFER_MILLIS = 45_000L
    private const val HOST_RETRY_MILLIS = 90_000L

    private val logger = LogManager.getLogger(RuneRunningCoordinator::class.java)
    private var nextDeliveryId = 0L
    private var Bot.nextHostConsideration by Attr.long()
    private var Bot.lastRunePartner by Attr.string()
    private var Bot.lastRunePartnerUntil by Attr.long()

    /**
     * The exact essence form used for a particular native altar. Pure essence is also valid at the early altars.
     *
     * @author lare96
     */
    data class Route(val altar: Altar, val essenceId: Int) {
        val rune get() = CraftableRune.ALTAR_TO_RUNE.getValue(altar)
    }

    /**
     * Routes are fixed, independent of the player population. Hosts and requests are indexed within each route.
     */
    private val routes = Altar.entries.flatMap { altar ->
        listOf(essence(altar), PURE_ESSENCE).distinct().map { Route(altar, it) }
    }

    /**
     * A host carries a finite number of paid loads. An equipped tiara leaves room for more consecutive trades.
     *
     * @author lare96
     */
    internal data class HostTerms(val fee: Int, val loads: Int, val capacity: Int, val tiara: Boolean,
                                  val training: Boolean)

    /**
     * A bot that decided to run and is awaiting a funded partner at one route.
     *
     * @author lare96
     */
    private class Request(val owner: RuneRunningWaitBotScript, val route: Route, val minimumFee: Int, val start: Long) {
        val bot get() = owner.bot
        var expires = start + SEARCH_MILLIS
        var preparation: RuneCrafterBotScript? = null
    }

    /**
     * A live crafting script that volunteered at its current altar.
     *
     * @author lare96
     */
    private data class ActiveOffer(val owner: BotScript, val terms: HostTerms, val expires: Long)

    /**
     * Tracks the host's finite purse and one in-flight native trade.
     *
     * @author lare96
     */
    private class Host(val script: RuneCrafterBotScript, val terms: HostTerms, val expires: Long,
                       val active: Boolean) {
        val route = Route(script.altar, script.essenceId)
        val partnerships = LinkedHashMap<Bot, Partnership>()
        var ready = false
        var receiving = false
        var unspentLoads = terms.loads
        var delivery: Delivery? = null
        var admitting = true
        var nextPriceCheck = 0L
    }

    /**
     * A temporary allocation of whole funded loads to one runner script.
     *
     * @author lare96
     */
    private class Partnership(val runner: RuneRunnerBotScript, val host: Host, var expires: Long, var loads: Int)

    /**
     * The exact native trade and its settlement result. The crafter acknowledges the receipt once,
     * then consumes the received loose essence through the normal altar action.
     *
     * @author lare96
     */
    class Delivery internal constructor(val id: Long, val runner: Bot, val crafter: Bot,
                                        val route: Route, val fee: Int, val expires: Long) {
        val runnerGives = listOf(Item(route.essenceId, BATCH))
        val crafterGives = listOf(Item(itemDef(route.essenceId).notedId.asInt, BATCH), Item(COINS, fee))
        val dialogue = RuneRunningDialogue(this)
        var paid = false
            internal set
        internal var runnerDone = false
        internal var crafterDone = false
    }

    private val requests = HashMap<Bot, Request>()
    private val waiting = routes.associateWith { LinkedHashMap<Bot, Request>() }
    private val offers = routes.associateWith { LinkedHashMap<Bot, ActiveOffer>() }
    private val hosts = HashMap<Bot, Host>()
    private val byRoute = routes.associateWith { LinkedHashMap<Bot, Host>() }
    private val partnerships = HashMap<Bot, Partnership>()
    private var nextIdleRoute = 0
    private var recruitAfter = Long.MAX_VALUE

    fun essence(altar: Altar): Int = if (CraftableRune.ALTAR_TO_RUNE.getValue(altar).level <= 20) 1436 else PURE_ESSENCE

    fun legacyEssence(altar: Altar): Int = if (altar == Altar.AIR) 1436 else PURE_ESSENCE

    fun validEssence(altar: Altar, id: Int): Boolean = id == essence(altar) || id == PURE_ESSENCE

    fun fare(altar: Altar): Int = if (altar == Altar.NATURE) NATURE_FARE else 0

    private fun returnFare(altar: Altar): Int = fare(altar) / 2

    fun tripTimeout(altar: Altar): Duration = if (altar == Altar.NATURE || altar == Altar.DEATH) 10.minutes else 5.minutes

    internal fun owned(bot: Bot, id: Int): Long =
        bot.bank.computeAmountForId(id).toLong() + bot.inventory.computeAmountForId(id)

    private fun owns(bot: Bot, id: Int): Boolean = owned(bot, id) > 0 || bot.equipment.any { it?.id == id }

    private fun ownedEssence(bot: Bot, route: Route): Long {
        val note = itemDef(route.essenceId).notedId.orElse(-1)
        return if (note < 0) 0 else owned(bot, route.essenceId) + owned(bot, note)
    }

    private fun availableBot(bot: Bot): Boolean = bot.isRealBot() && !bot.isConnectedPlayer && !bot.isTemporary &&
        bot.state == EntityState.ACTIVE && bot.isAlive && ActionSafety.NON_COMBAT.allows(bot)

    internal fun runnerEligible(bot: Bot, altar: Altar? = null): Boolean = availableBot(bot) &&
        bot.combatLevel < 50 && (!bot.inWilderness() || altar == Altar.CHAOS)

    internal fun crafterEligible(bot: Bot, route: Route): Boolean = availableBot(bot) &&
        validEssence(route.altar, route.essenceId) && bot.runecrafting.level >= route.rune.level &&
        (!bot.inWilderness() || route.altar == Altar.CHAOS)

    private fun canRun(bot: Bot, route: Route): Boolean = runnerEligible(bot, route.altar) &&
        (route.altar != Altar.COSMIC || owns(bot, Zanaris.STAFF)) &&
        ownedEssence(bot, route) >= BATCH && owned(bot, COINS) >= fare(route.altar)

    private fun hasFirstLoad(bot: Bot): Boolean =
        ownedEssence(bot, Route(Altar.AIR, 1436)) >= BATCH ||
            ownedEssence(bot, Route(Altar.COSMIC, PURE_ESSENCE)) >= BATCH

    fun canDispatch(bot: Bot): Boolean = bot.preferences.runeRunning.isIntroductory &&
        bot.preferences.runeRunning.canStartSession(System.currentTimeMillis()) && bot.scriptStack.size() == 0 &&
        runnerEligible(bot) && hasFirstLoad(bot)

    override fun accept(bot: Bot) {
        if (canDispatch(bot)) bot.scriptStack.push(RuneRunningWaitBotScript(bot, introductory = true))
    }

    fun profitScript(bot: Bot): BotScript? {
        if (!runnerEligible(bot) || !bot.preferences.runeRunning.canStartSession(System.currentTimeMillis()) ||
            !rand(0.25) || !hasFirstLoad(bot)) return null
        return RuneRunningWaitBotScript(bot, introductory = false)
    }

    internal fun submit(owner: RuneRunningWaitBotScript): Boolean {
        val bot = owner.bot
        val now = System.currentTimeMillis()
        if (requests.containsKey(bot) || partnerships.containsKey(bot) || hosts.containsKey(bot) ||
            owner.introductory && !bot.preferences.runeRunning.isIntroductory ||
            !bot.preferences.runeRunning.trySession(now, if (owner.introductory) 3.minutes.inWholeMilliseconds
                else 45.minutes.inWholeMilliseconds)) return false

        val candidates = ArrayList<Triple<Route, Int, Double>>()
        for (route in routes) {
            if (!canRun(bot, route)) continue
            trimRequests(route, now)
            if (waiting.getValue(route).size >= MAX_REQUESTS) continue
            val minimum = RuneRunningEconomics.minimumFee(bot, route.altar, owner.introductory)
            val ready = chooseHost(bot, route, minimum, now)
            val active = usableOffers(route, now).values.any {
                it.terms.fee >= minimum && compatiblePartners(bot, it.owner.bot)
            }
            val priority = when {
                ready != null -> 2
                active -> 1
                else -> 0
            }
            val distance = bot.position.computeLongestDistance(route.altar.exit).coerceAtMost(600)
            val margin = RuneRunningEconomics.forecast(bot, route).coerceIn(0, 2_000)
            val introductory = if (owner.introductory && route.rune.level <= 20) 2.0 else 1.0
            val familiar = if (ready != null && prefers(bot, ready.script.bot, now)) 2.0 else 1.0
            val weight = introductory * familiar * (1.0 + margin / 400.0) /
                ((1 + waiting.getValue(route).size) * (1.0 + distance / 200.0))
            candidates += Triple(route, priority, weight)
        }
        val priority = candidates.maxOfOrNull { it.second } ?: return false
        val routes = candidates.filter { it.second == priority }.associate { it.first to it.third }
        val route = RandomUtils.weightedRoll(routes) ?: return false
        val request = Request(owner, route, RuneRunningEconomics.minimumFee(bot, route.altar, owner.introductory), now)
        requests[bot] = request
        waiting.getValue(route)[bot] = request
        recruitAfter = minOf(recruitAfter, now + PREFER_ACTIVE_MILLIS)
        return true
    }

    internal fun match(owner: RuneRunningWaitBotScript): RuneRunnerBotScript? {
        val request = requests[owner.bot]?.takeIf { it.owner === owner } ?: return null
        val now = System.currentTimeMillis()
        if (!requestLive(request, now)) {
            remove(request)
            return null
        }
        val host = chooseHost(request.bot, request.route, request.minimumFee, now) ?: return null
        val runner = RuneRunnerBotScript(request.bot, request.route.altar, owner.introductory,
            30.minutes, request.route.essenceId, request.minimumFee)
        reserve(runner, host, now)
        remove(request)
        return runner
    }

    internal fun waiting(owner: RuneRunningWaitBotScript): Boolean {
        val request = requests[owner.bot]?.takeIf { it.owner === owner } ?: return false
        val preparation = request.preparation
        if (preparation != null && hosts[preparation.bot]?.script !== preparation) {
            request.expires = request.start + SEARCH_MILLIS
            request.preparation = null
        }
        return requestLive(request, System.currentTimeMillis())
    }

    internal fun withdraw(owner: RuneRunningWaitBotScript) {
        requests[owner.bot]?.takeIf { it.owner === owner }?.let(::remove)
    }

    private fun requestLive(request: Request, now: Long): Boolean =
        now < request.expires && request.bot.scriptStack.current() === request.owner &&
            runnerEligible(request.bot, request.route.altar)

    private fun remove(request: Request) {
        if (requests.remove(request.bot, request)) waiting.getValue(request.route).remove(request.bot)
        if (requests.isEmpty()) recruitAfter = Long.MAX_VALUE
    }

    private fun trimRequests(route: Route, now: Long) {
        waiting.getValue(route).values.toList().filterNot { requestLive(it, now) }.forEach(::remove)
    }

    /**
     * At its own altar checkpoint, a normal crafting script can offer the same altar for paid service.
     * Eligibility and terms are cached on this visit, then checked again when the host packs.
     */
    internal fun advertise(owner: BotScript, route: Route, training: Boolean) {
        val bot = owner.bot
        val now = System.currentTimeMillis()
        if (bot.nextHostConsideration > now || !crafterEligible(bot, route) || bot.scriptStack.current() !== owner)
            return
        bot.nextHostConsideration = now + HOST_RETRY_MILLIS
        val choices = usableOffers(route, now)
        if (choices.size >= MAX_HOSTS || !rand(0.25 + bot.personality.social.coerceIn(0.0, 1.0) * 0.75)) return
        val minimum = fare(route.altar) + RuneRunningEconomics.routeWork(route.altar)
        val terms = terms(bot, route, minimum, training) ?: return
        choices[bot] = ActiveOffer(owner, terms, now + ACTIVE_OFFER_MILLIS)
    }

    internal fun withdrawOffer(owner: BotScript, route: Route) {
        val indexed = offers.getValue(route)
        if (indexed[owner.bot]?.owner === owner) indexed.remove(owner.bot)
    }

    private fun usableOffers(route: Route, now: Long): MutableMap<Bot, ActiveOffer> {
        val indexed = offers.getValue(route)
        indexed.values.removeIf { now >= it.expires || it.owner.bot.scriptStack.current() !== it.owner ||
            !crafterEligible(it.owner.bot, route) }
        return indexed
    }

    internal fun activeCrafterScript(owner: BotScript, route: Route): RuneCrafterBotScript? {
        val now = System.currentTimeMillis()
        val offer = usableOffers(route, now)[owner.bot]?.takeIf { it.owner === owner } ?: return null
        val request = unmetDemand(route, now) {
            it.bot !== owner.bot && compatiblePartners(it.bot, owner.bot) && it.minimumFee <= offer.terms.fee &&
                chooseHost(it.bot, route, it.minimumFee, now) == null
        } ?: return null
        val script = prepare(request, owner.bot, offer.terms, active = true, now = now) ?: return null
        withdrawOffer(owner, route)
        return script
    }

    /**
     * Idle bots can opt in at their normal activity decision or after releasing an ordinary idle visit.
     * Only existing requests can recruit, and active crafters receive the first opportunity.
     */
    @JvmOverloads
    fun idleCrafterScript(bot: Bot, idleOwner: BotScript? = null): RuneCrafterBotScript? {
        val now = System.currentTimeMillis()
        if (now < recruitAfter || bot.nextHostConsideration > now ||
            !availableBot(bot) || bot.scriptStack.size() != (if (idleOwner == null) 0 else 1) ||
            bot.scriptStack.current() !== idleOwner || hosts.containsKey(bot) || partnerships.containsKey(bot))
            return null
        val interest = bot.preferences.activities[BotActivity.TRAINING_SKILLS] ?: 0.0
        val preferred = SKILL_RUNECRAFTING in bot.preferences.skills
        if (!interest.isFinite() || interest <= 0.0 || !bot.preferences.isSkiller && !preferred ||
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_RUNECRAFTING)) return null
        var considered = false
        for (offset in routes.indices) {
            val route = routes[(nextIdleRoute + offset) % routes.size]
            if (waiting.getValue(route).isEmpty() || !crafterEligible(bot, route)) continue
            val request = unmetDemand(route, now) {
                it.bot !== bot && compatiblePartners(it.bot, bot) && now - it.start >= PREFER_ACTIVE_MILLIS &&
                    chooseHost(it.bot, route, it.minimumFee, now) == null &&
                    usableOffers(route, now).values.none { offer ->
                        offer.terms.fee >= it.minimumFee && compatiblePartners(it.bot, offer.owner.bot)
                    }
            } ?: continue
            if (!considered) {
                considered = true
                bot.nextHostConsideration = now + HOST_RETRY_MILLIS
                if (!rand(0.15 + bot.personality.social.coerceIn(0.0, 1.0) * 0.65 + if (preferred) 0.20 else 0.0))
                    return null
            }
            val terms = terms(bot, route, request.minimumFee, training = true) ?: continue
            val script = prepare(request, bot, terms, active = false, now = now) ?: continue
            nextIdleRoute = (nextIdleRoute + offset + 1) % routes.size
            return script
        }
        return null
    }

    private fun unmetDemand(route: Route, now: Long, matches: (Request) -> Boolean): Request? {
        trimRequests(route, now)
        val indexed = liveHosts(route, now)
        if (indexed.values.any { !it.ready }) return null
        return waiting.getValue(route).values.firstOrNull { it.preparation == null && matches(it) }
    }

    private fun terms(bot: Bot, route: Route, minimum: Int, training: Boolean): HostTerms? {
        val altar = route.altar
        val tiara = owns(bot, altar.tiara)
        if (!tiara && !owns(bot, altar.talisman) || altar == Altar.COSMIC && !owns(bot, Zanaris.STAFF)) return null
        val coins = owned(bot, COINS)
        val fee = RuneRunningEconomics.quoteFee(bot, route, minimum, coins, training) ?: return null
        val maxLoads = if (tiara) 6 else 2
        val loads = minOf(ownedEssence(bot, route) / BATCH, maxLoads.toLong(), (coins - fare(altar)) / (10L * fee)).toInt()
        if (loads <= 0) return null
        val capacity = if (bot.personality.social >= 0.70) MAX_RUNNERS else if (bot.personality.social >= 0.40) 2 else 1
        return HostTerms(fee, loads, capacity, tiara, training)
    }

    private fun prepare(request: Request, bot: Bot, terms: HostTerms, active: Boolean, now: Long): RuneCrafterBotScript? {
        val route = request.route
        if (hosts.containsKey(bot) || byRoute.getValue(route).size >= MAX_HOSTS) return null
        val script = RuneCrafterBotScript(bot, route.altar, 35.minutes, route.essenceId).also {
            if (BotCombatActionHandler.wantsToTrain(bot, SKILL_RUNECRAFTING)) it.trainingGoal = SKILL_RUNECRAFTING
        }
        val prepareUntil = now + (tripTimeout(route.altar) + 90.seconds).inWholeMilliseconds
        val host = Host(script, terms, prepareUntil, active)
        hosts[bot] = host
        byRoute.getValue(route)[bot] = host
        request.preparation = script
        request.expires = prepareUntil
        logger.debug("Rune running host preparing: altar={} active={} fundedLoads={} fee={}",
            route.altar, active, terms.loads, terms.fee)
        return script
    }

    internal fun hostTerms(script: RuneCrafterBotScript): HostTerms? {
        val now = System.currentTimeMillis()
        val route = Route(script.altar, script.essenceId)
        val indexed = liveHosts(route, now)
        if (indexed[script.bot]?.script === script) return indexed[script.bot]?.terms
        // A saved host can rebuild an offer against real current demand. No saved contract is restored.
        val request = unmetDemand(route, now) { it.bot !== script.bot } ?: return null
        val terms = terms(script.bot, route, request.minimumFee, training = true) ?: return null
        if (hosts.containsKey(script.bot) || indexed.size >= MAX_HOSTS) return null
        val until = now + tripTimeout(route.altar).inWholeMilliseconds
        indexed[script.bot] = Host(script, terms, until, active = false).also { hosts[script.bot] = it }
        request.preparation = script
        request.expires = until
        return terms
    }

    internal fun register(script: RuneCrafterBotScript): Boolean {
        val host = hosts[script.bot]?.takeIf { it.script === script } ?: return false
        val now = System.currentTimeMillis()
        if (!host.ready && now >= host.expires || script.bot.scriptStack.current() !== script ||
            !crafterEligible(script.bot, host.route) || !atMeeting(script.bot, host.route)) return false
        val inventory = script.bot.inventory
        val note = itemDef(host.route.essenceId).notedId.orElse(-1)
        if (note < 0) return false
        val funded = minOf(host.unspentLoads, inventory.computeAmountForId(note) / BATCH,
            (inventory.computeAmountForId(COINS) - returnFare(host.route.altar)) / host.terms.fee)
        if (funded <= 0 || funded < host.partnerships.values.sumOf { it.loads }) return false
        host.unspentLoads = funded
        if (!host.ready) host.nextPriceCheck = now + 60_000L
        host.ready = true
        host.receiving = true
        return true
    }

    internal fun hasFunds(script: RuneCrafterBotScript): Boolean =
        (hosts[script.bot]?.takeIf { it.script === script }?.unspentLoads ?: 0) > 0

    private fun liveHosts(route: Route, now: Long): MutableMap<Bot, Host> {
        val indexed = byRoute.getValue(route)
        for (host in indexed.values.toList()) {
            val bot = host.script.bot
            val current = bot.scriptStack.current()
            if (bot.state != EntityState.ACTIVE || !bot.isAlive ||
                (host.ready && current !== host.script) || !host.ready && now >= host.expires) {
                unregister(host.script)
            } else if (host.ready) expirePartnerships(host, now)
        }
        return indexed
    }

    private fun prefers(bot: Bot, partner: Bot, now: Long): Boolean = bot.lastRunePartnerUntil > now &&
        bot.lastRunePartner == partner.username && bot.personality.social >= 0.5

    private fun compatiblePartners(runner: Bot, crafter: Bot): Boolean =
        runner.preferences.getFeelingsToward(crafter.username) >= 0.20 &&
            crafter.preferences.getFeelingsToward(runner.username) >= 0.20

    private fun chooseHost(bot: Bot, route: Route, minimum: Int, now: Long): Host? =
        liveHosts(route, now).values.asSequence().filter {
            it.ready && it.script.bot !== bot && it.terms.fee >= minimum && freeLoads(it) > 0 &&
                it.partnerships.size < it.terms.capacity && crafterEligible(it.script.bot, route) &&
                compatiblePartners(bot, it.script.bot) && admitting(it, now)
        }.maxByOrNull {
            (if (it.active) 8.0 else 0.0) + (if (prefers(bot, it.script.bot, now) ||
                prefers(it.script.bot, bot, now)) 4.0 else 0.0) +
                bot.preferences.getFeelingsToward(it.script.bot.username) +
                it.script.bot.preferences.getFeelingsToward(bot.username) - it.partnerships.size
        }

    private fun admitting(host: Host, now: Long): Boolean {
        if (now < host.nextPriceCheck) return host.admitting
        val bot = host.script.bot
        host.nextPriceCheck = now + 60_000L
        host.admitting = RuneRunningEconomics.quoteFee(bot, host.route, host.terms.fee,
            owned(bot, COINS), host.terms.training) != null
        return host.admitting
    }

    private fun freeLoads(host: Host): Int = host.unspentLoads - host.partnerships.values.sumOf { it.loads }

    private fun reserve(runner: RuneRunnerBotScript, host: Host, now: Long) {
        val share = (host.terms.loads / host.terms.capacity).coerceAtLeast(1)
        val loads = minOf(freeLoads(host), share, MAX_TRIPS_PER_RUNNER - runner.paidTrips)
        val partnership = Partnership(runner, host, tripDeadline(host.route, now), loads)
        host.partnerships[runner.bot] = partnership
        partnerships[runner.bot] = partnership
    }

    private fun tripDeadline(route: Route, now: Long): Long =
        now + (tripTimeout(route.altar) * 2 + 2.minutes).inWholeMilliseconds

    private fun expirePartnerships(host: Host, now: Long) {
        for (contract in host.partnerships.values.toList()) {
            val bot = contract.runner.bot
            if (now >= contract.expires || bot.state != EntityState.ACTIVE || !bot.isAlive) release(contract.runner)
        }
    }

    internal fun attach(runner: RuneRunnerBotScript): Boolean {
        val bot = runner.bot
        val route = Route(runner.altar, runner.essenceId)
        val now = System.currentTimeMillis()
        if (!canRun(bot, route) || runner.paidTrips >= MAX_TRIPS_PER_RUNNER) return false
        val existing = partnerships[bot]
        if (existing != null) return existing.runner === runner && existing.expires > now && existing.loads > 0
        val host = chooseHost(bot, route, runner.minimumFee, now) ?: return false
        reserve(runner, host, now)
        return true
    }

    internal fun hasWork(runner: RuneRunnerBotScript): Boolean {
        val lease = partnerships[runner.bot]?.takeIf { it.runner === runner } ?: return false
        return lease.loads > 0 && System.currentTimeMillis() < lease.expires &&
            lease.host.script.bot.scriptStack.current() === lease.host.script &&
            lease.host.script.bot.state == EntityState.ACTIVE && lease.host.script.bot.isAlive
    }

    internal fun serving(script: RuneCrafterBotScript): Boolean {
        val host = hosts[script.bot]?.takeIf { it.script === script } ?: return false
        val now = System.currentTimeMillis()
        expirePartnerships(host, now)
        if (host.partnerships.isNotEmpty()) return true
        trimRequests(host.route, now)
        return waiting.getValue(host.route).values.any { host.terms.fee >= it.minimumFee }
    }

    internal fun requestDelivery(runner: RuneRunnerBotScript): Delivery? {
        val lease = partnerships[runner.bot]?.takeIf { it.runner === runner } ?: return null
        val host = lease.host
        val crafter = host.script.bot
        if (!host.receiving || !hasWork(runner) || !atMeeting(runner.bot, host.route) ||
            !atMeeting(crafter, host.route)) return null
        host.delivery?.let { return it.takeIf { it.runner === runner.bot } }
        val note = itemDef(host.route.essenceId).notedId.orElse(-1)
        if (note < 0 || crafter.inventory.computeAmountForId(note) < BATCH ||
            crafter.inventory.computeAmountForId(COINS) < host.terms.fee + returnFare(host.route.altar) ||
            runner.bot.inventory.computeAmountForId(host.route.essenceId) != BATCH) return null
        val expires = minOf(lease.expires, System.currentTimeMillis() + 2.minutes.inWholeMilliseconds)
        return Delivery(++nextDeliveryId, runner.bot, crafter, host.route, host.terms.fee, expires)
            .also { host.delivery = it }
    }

    internal fun pending(script: RuneCrafterBotScript): Delivery? =
        hosts[script.bot]?.takeIf { it.script === script }?.delivery?.takeUnless { it.crafterDone }

    internal fun atMeeting(bot: Bot, route: Route): Boolean =
        bot.position.z == route.altar.exit.z && bot.position.isWithinDistance(route.altar.exit, 3)

    internal fun valid(delivery: Delivery): Boolean {
        val lease = partnerships[delivery.runner] ?: return false
        val host = lease.host
        return host.receiving && !delivery.paid && !delivery.runnerDone && !delivery.crafterDone && lease.loads > 0 &&
            host.delivery === delivery && host.script.bot === delivery.crafter &&
            delivery.runner.scriptStack.current() === lease.runner &&
            delivery.crafter.scriptStack.current() === host.script && runnerEligible(delivery.runner, host.route.altar) &&
            crafterEligible(delivery.crafter, host.route) && atMeeting(delivery.runner, host.route) &&
            atMeeting(delivery.crafter, host.route) && System.currentTimeMillis() < delivery.expires
    }

    /**
     * Called once by native settlement after the exact two inventory transfers.
     */
    internal fun paid(delivery: Delivery) {
        if (delivery.paid) return
        val lease = partnerships[delivery.runner]?.takeIf { it.host.delivery === delivery } ?: return
        delivery.paid = true
        lease.host.receiving = false
        lease.loads--
        lease.host.unspentLoads--
        lease.runner.paidTrips++
        val now = System.currentTimeMillis()
        lease.expires = tripDeadline(delivery.route, now)
        val until = now + 4.hours.inWholeMilliseconds
        delivery.runner.lastRunePartner = delivery.crafter.username
        delivery.runner.lastRunePartnerUntil = until
        delivery.crafter.lastRunePartner = delivery.runner.username
        delivery.crafter.lastRunePartnerUntil = until
        logger.info("Rune running paid: delivery={} altar={} essence={} quantity={} fee={}",
            delivery.id, delivery.route.altar, delivery.route.essenceId, BATCH, delivery.fee)
    }

    internal fun crafted(delivery: Delivery, output: Int) {
        logger.info("Rune running crafted: delivery={} altar={} rune={} quantity={}",
            delivery.id, delivery.route.altar, delivery.route.rune.id, output)
    }

    internal fun finishDelivery(delivery: Delivery, bot: Bot) {
        if (bot === delivery.runner) delivery.runnerDone = true
        if (bot === delivery.crafter) delivery.crafterDone = true
        val host = hosts[delivery.crafter] ?: return
        if (host.delivery === delivery && delivery.runnerDone && delivery.crafterDone) {
            delivery.dialogue.close()
            host.delivery = null
        }
    }

    internal fun release(runner: RuneRunnerBotScript) {
        val lease = partnerships[runner.bot]?.takeIf { it.runner === runner } ?: return
        partnerships.remove(runner.bot)
        lease.host.partnerships.remove(runner.bot)
        lease.host.delivery?.takeIf { it.runner === runner.bot }?.let {
            it.dialogue.close()
            finishDelivery(it, runner.bot)
        }
    }

    internal fun unregister(script: RuneCrafterBotScript) {
        val host = hosts[script.bot]?.takeIf { it.script === script } ?: return
        hosts.remove(script.bot)
        byRoute.getValue(host.route).remove(script.bot)
        host.delivery?.dialogue?.close()
        for ((bot, partnership) in host.partnerships) partnerships.remove(bot, partnership)
    }
}
