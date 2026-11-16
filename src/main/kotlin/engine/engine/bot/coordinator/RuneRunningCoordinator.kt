package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.action.BotCombatActionHandler
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.RuneCrafterBotScript
import game.bot.scripts.RuneRunnerBotScript
import game.bot.scripts.RuneRunningWaitBotScript
import game.bot.scripts.RuneRunningDialogue
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import game.obj.entrances.Zanaris
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Matches low-level essence runners with funded skiller crafters on the game thread.
 * Reservations contain live sessions only; saved scripts rebuild them without trusting old player references.
 * A host serves at most three runners and owns one exact exchange until both participants leave it.
 *
 * @author lare96
 */
object RuneRunningCoordinator : BotCoordinator {

    const val ESSENCE = 7936
    const val BATCH = 25
    const val COINS = 995
    const val NATURE_FARE = 60
    const val MAX_RUNNERS = 3

    /**
     * Every native altar may host deliveries. Travel still requires an actual usable web-walk route.
     */
    val ALTARS = Altar.ALL

    /**
     * The six introductory runes accept ordinary essence; higher runes require pure essence.
     */
    fun essence(altar: Altar): Int = if (CraftableRune.ALTAR_TO_RUNE.getValue(altar).level <= 20) 1436 else ESSENCE

    /**
     * Old saved Air jobs used ordinary essence; every other previously saved route used pure essence.
     */
    fun legacyEssence(altar: Altar): Int = if (altar == Altar.AIR) 1436 else ESSENCE

    fun validEssence(altar: Altar, id: Int): Boolean = id == essence(altar) || id == ESSENCE

    private fun suppliedEssence(bot: Bot, altar: Altar): Int? =
        listOf(essence(altar), ESSENCE).distinct().firstOrNull { id ->
            bot.bank.computeAmountForId(id).toLong() + bot.inventory.computeAmountForId(id) >= BATCH * 11
        }

    /**
     * Long mainland approaches get enough time for ordinary travel without enabling recovery teleports.
     */
    fun tripTimeout(altar: Altar): Duration = if (altar == Altar.NATURE || altar == Altar.DEATH) 10.minutes else 5.minutes

    /**
     * A single immutable trade contract; completion flags belong to the two owning coroutines.
     *
     * @author lare96
     */
    class Delivery internal constructor(val runner: Bot, val crafter: Bot, val altar: Altar,
                                       val seed: Boolean, val crafterGives: List<Item>,
                                       val expires: Long, val essenceId: Int = essence(altar)) {
        val runnerGives: List<Item> = if (seed) emptyList() else listOf(Item(essenceId, BATCH))
        internal var runnerDone = false
        internal var crafterDone = false
        internal var runnerStarted = false
        val dialogue = RuneRunningDialogue(this)
    }

    /**
     * A prepared crafter and its bounded group of runners.
     *
     * @author lare96
     */
    private class Host(val script: RuneCrafterBotScript) {
        val runners = linkedSetOf<Bot>()
        var delivery: Delivery? = null
    }

    private val hosts = linkedMapOf<Bot, Host>()
    private val runners = hashMapOf<Bot, Bot>()
    private val preparing = linkedMapOf<Bot, RuneCrafterBotScript>()
    private var demandCheckedAt = 0L
    private var demand = emptyMap<Altar, List<Bot>>()

    /**
     * Counts usable owned currency before dispatch or packing, without relying on cached trackers.
     */
    internal fun coins(bot: Bot): Long = bot.bank.computeAmountForId(COINS).toLong() +
        bot.inventory.computeAmountForId(COINS)

    private fun owns(bot: Bot, id: Int): Boolean = bot.bank.computeAmountForId(id) > 0 ||
        bot.inventory.computeAmountForId(id) > 0 || bot.equipment.any { it?.id == id }

    /**
     * Shares one bounded demand snapshot for five seconds instead of scanning the population per idle skiller.
     * Each altar retains enough candidates for its three slots and one excluded host; admission rechecks live state.
     */
    private fun demand(): Map<Altar, List<Bot>> {
        val now = System.currentTimeMillis()
        if (now >= demandCheckedAt && now - demandCheckedAt < 5_000) return demand
        val next = ALTARS.associateWith { mutableListOf<Bot>() }
        for (player in world.players) {
            val bot = player as? Bot ?: continue
            if (bot in runners || bot.scriptStack.current() is RuneCrafterBotScript || !runnerEligible(bot)) continue
            for ((altar, candidates) in next) {
                if (!canRun(bot, altar)) continue
                if (candidates.size < MAX_RUNNERS + 1) candidates += bot
                else if (bot.preferences.runeRunning.isIntroductory) {
                    val ordinary = candidates.indexOfFirst { !it.preferences.runeRunning.isIntroductory }
                    if (ordinary >= 0) candidates[ordinary] = bot
                }
            }
            if (next.values.all { candidates -> candidates.size >= MAX_RUNNERS + 1 &&
                    candidates.all { it.preferences.runeRunning.isIntroductory } }) break
        }
        demand = next
        demandCheckedAt = now
        return demand
    }

    /**
     * Runner eligibility is based on actual account state and combat level, never a Runecrafting requirement.
     */
    internal fun runnerEligible(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && bot.combatLevel < 50 &&
        bot.isAlive && (!bot.inWilderness() || (bot.scriptStack.current() as? RuneRunnerBotScript)?.altar == Altar.CHAOS) &&
        ActionSafety.NON_COMBAT.allows(bot)

    /**
     * A nature runner must fund its initial return fare. Each paid delivery reimburses the next round trip.
     */
    internal fun canRun(bot: Bot, altar: Altar): Boolean = altar in ALTARS && runnerEligible(bot) &&
        (altar != Altar.NATURE || coins(bot) >= NATURE_FARE) &&
        (altar != Altar.COSMIC || owns(bot, Zanaris.STAFF))

    /**
     * The new-account coordinator dispatches only when a real prepared host has capacity.
     */
    fun canDispatch(bot: Bot): Boolean = bot.preferences.runeRunning.isIntroductory &&
        bot.preferences.runeRunning.canStartSession(System.currentTimeMillis()) &&
        bot.scriptStack.size() == 0 && (availableHost(bot) != null || hasPreparingHost(bot))

    override fun accept(bot: Bot) {
        if (!canDispatch(bot)) return
        if (!bot.preferences.runeRunning.trySession(System.currentTimeMillis(), 3.minutes.inWholeMilliseconds)) return
        val host = reserve(bot)
        if (host == null) {
            val timeout = preparing.values.filter { it.bot !== bot && canRun(bot, it.altar) }
                .maxOfOrNull { tripTimeout(it.altar).inWholeMilliseconds * 2 } ?: return
            bot.scriptStack.push(RuneRunningWaitBotScript(bot, System.currentTimeMillis() + timeout))
            return
        }
        val essence = runnerEssence(bot) ?: return
        bot.scriptStack.push(RuneRunnerBotScript(bot, host, introductory = true, duration = 30.minutes, essenceId = essence))
    }

    /**
     * Waits only while a real supplied host owns an altar reservation and is preparing its native trip.
     */
    fun hasPreparingHost(bot: Bot): Boolean {
        prune()
        return preparing.values.any { it.bot !== bot && canRun(bot, it.altar) }
    }

    /**
     * A waiting account queues an actual runner once capacity is ready; initialization reserves its live slot.
     */
    fun waitingRunner(bot: Bot): BotScript? {
        val host = availableHost(bot) ?: return null
        return RuneRunnerBotScript(bot, host.script.altar, introductory = true, duration = 30.minutes,
            essenceId = host.script.essenceId)
    }

    /**
     * Offers an occasional bounded money-making session to established low-level accounts.
     */
    fun profitScript(bot: Bot): BotScript? {
        if (!runnerEligible(bot) || availableHost(bot) == null || !rand(0.25)) return null
        if (!bot.preferences.runeRunning.trySession(System.currentTimeMillis(), 45.minutes.inWholeMilliseconds)) return null
        val altar = reserve(bot) ?: return null
        val essence = runnerEssence(bot) ?: return null
        return RuneRunnerBotScript(bot, altar, introductory = false, duration = rand(15, 30).minutes, essenceId = essence)
    }

    /**
     * Supplies introductory demand before ordinary Runecrafting, and occasionally hosts other eligible runners.
     * The account's skiller identity remains authoritative even if another account has identical skill levels.
     */
    fun crafterScript(bot: Bot, duration: Duration): BotScript? {
        return crafterScript(bot, duration, introductoryOnly = false)
    }

    /**
     * Gives willing, supplied skillers an early opportunity to serve real introductory runner demand.
     */
    fun introductoryCrafterScript(bot: Bot): BotScript? {
        val weight = bot.preferences.activities[BotActivity.TRAINING_SKILLS] ?: return null
        if (!weight.isFinite() || weight <= 0.0 || !bot.preferences.isSkiller ||
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_RUNECRAFTING)) return null
        return crafterScript(bot, rand(45, 90).minutes, introductoryOnly = true)
    }

    private fun crafterScript(bot: Bot, duration: Duration, introductoryOnly: Boolean): BotScript? {
        prune()
        if (!bot.preferences.isSkiller || !ActionSafety.NON_COMBAT.allows(bot)) return null
        val demand = demand().mapValues { (altar, candidates) ->
            candidates.filter { it !== bot && it !in runners && it !in preparing && it !in hosts &&
                it.scriptStack.current() !is RuneCrafterBotScript && canRun(it, altar) }
        }
        val introductory = demand.values.any { candidates -> candidates.any { it.preferences.runeRunning.isIntroductory } }
        if (introductoryOnly && !introductory) return null
        if (!introductory && !rand(0.20)) return null
        val candidates = ALTARS.filter { altar ->
            bot.runecrafting.level >= CraftableRune.ALTAR_TO_RUNE.getValue(altar).level &&
                suppliedEssence(bot, altar) != null &&
                bot.bank.computeAmountForId(altar.talisman).toLong() + bot.inventory.computeAmountForId(altar.talisman) > 0 &&
                hosts.values.none { it.script.altar == altar } && preparing.values.none { it.altar == altar } &&
                demand.getValue(altar).isNotEmpty() &&
                (altar != Altar.NATURE || coins(bot) >= NATURE_FARE * 11L) &&
                (altar != Altar.COSMIC || owns(bot, Zanaris.STAFF))
        }
        val early = candidates.filter { CraftableRune.ALTAR_TO_RUNE.getValue(it).level <= 20 }
        val altar = (if (introductory && early.isNotEmpty()) early else candidates).randomOrNull() ?: return null
        return RuneCrafterBotScript(bot, altar, duration, suppliedEssence(bot, altar) ?: return null)
            .also {
                it.trainingGoal = SKILL_RUNECRAFTING
                preparing[bot] = it
            }
    }

    /**
     * Removes abandoned reservations and hosts before offering new work.
     */
    private fun prune() {
        preparing.keys.toList().forEach { bot ->
            if (bot.state != EntityState.ACTIVE || !bot.preferences.isSkiller ||
                bot.scriptStack.current() !== preparing[bot] &&
                    bot.scriptStack.scripts.none { it === preparing[bot] }) preparing.remove(bot)
        }
        hosts.keys.toList().forEach { bot ->
            val host = hosts[bot] ?: return@forEach
            if (bot.state != EntityState.ACTIVE || !bot.preferences.isSkiller ||
                bot.scriptStack.current() !== host.script) unregister(bot)
        }
        runners.keys.toList().forEach { bot ->
            val script = bot.scriptStack.current()
            if (bot.state != EntityState.ACTIVE || script !is RuneRunnerBotScript || !runnerEligible(bot)) release(bot)
        }
    }

    private fun availableHost(bot: Bot, altar: Altar? = null, essenceId: Int? = null, resuming: Boolean = false): Host? {
        prune()
        return hosts.values.filter { host -> host.script.bot !== bot &&
            (altar == null || host.script.altar == altar) &&
            (essenceId == null || host.script.essenceId == essenceId) &&
            (canRun(bot, host.script.altar) || resuming && host.script.altar == Altar.NATURE && runnerEligible(bot) &&
                bot.position.isWithinDistance(host.script.altar.exit, 3) && coins(bot) >= 30) &&
            host.runners.size < MAX_RUNNERS && host.script.canSupply() }
            .minByOrNull { host -> host.runners.size * 100 + CraftableRune.ALTAR_TO_RUNE.getValue(host.script.altar).level }
    }

    /**
     * Reserves one host before dispatch; a resumed runner may reacquire only its saved altar.
     */
    fun reserve(bot: Bot, altar: Altar? = null, essenceId: Int? = null): Altar? {
        runners[bot]?.let { owner ->
            val existing = hosts[owner]
            if (existing != null && (altar == null || existing.script.altar == altar) &&
                (essenceId == null || existing.script.essenceId == essenceId) && runnerEligible(bot))
                return existing.script.altar
            release(bot)
        }
        val host = availableHost(bot, altar, essenceId, resuming = altar != null) ?: return null
        host.runners += bot
        runners[bot] = host.script.bot
        return host.script.altar
    }

    /**
     * Reserves the altar while a selected or restored host is packing and walking to it.
     */
    fun prepare(script: RuneCrafterBotScript): Boolean {
        prune()
        if (hosts.values.any { it.script.altar == script.altar && it.script !== script } ||
            preparing.values.any { it.altar == script.altar && it !== script }) return false
        preparing[script.bot] = script
        return true
    }

    /**
     * Registers only a fully prepared, present, eligible skiller session.
     */
    fun register(script: RuneCrafterBotScript): Boolean {
        prune()
        val bot = script.bot
        if (bot.state != EntityState.ACTIVE || bot.scriptStack.current() !== script ||
            !bot.preferences.isSkiller || script.altar !in ALTARS || !script.canSupply() ||
            hosts.values.any { it.script.altar == script.altar && it.script !== script }) return false
        hosts[bot] = Host(script)
        preparing.remove(bot)
        return true
    }

    fun host(bot: Bot): Bot? = runners[bot]?.takeIf { owner ->
        hosts[owner]?.script?.let { it.bot.state == EntityState.ACTIVE &&
            it.bot.scriptStack.current() === it && it.bot.preferences.isSkiller } == true
    }

    fun runnerEssence(bot: Bot): Int? = runners[bot]?.let { hosts[it]?.script?.essenceId }

    /**
     * Starts one agreed exchange after the runner arrives; no items are removed or generated here.
     */
    fun request(bot: Bot, seed: Boolean): Delivery? {
        val owner = runners[bot] ?: return null
        val host = hosts[owner] ?: return null
        if (!runnerEligible(bot) || !host.script.canSupply() ||
            !bot.position.isWithinDistance(host.script.altar.exit, 3) ||
            !owner.position.isWithinDistance(host.script.altar.exit, 3)) return null
        host.delivery?.let { return if (it.runner === bot) it else null }
        val essence = host.script.essenceId
        val note = itemDef(essence).notedId.orElse(-1)
        if (note < 0 || owner.inventory.computeAmountForId(note) < BATCH) return null
        // A starter is recruited only when the host can also fund its first paid return.
        if (seed && (owner.inventory.computeAmountForId(note) < BATCH * 2 ||
            host.script.altar == Altar.NATURE &&
            owner.inventory.computeAmountForId(COINS) < NATURE_FARE * 2 + 30)) return null
        val rune = CraftableRune.ALTAR_TO_RUNE.getValue(host.script.altar)
        val output = owner.inventory.computeAmountForId(rune.id)
        if (!seed && (output <= 0 || bot.inventory.computeAmountForId(essence) != BATCH)) return null
        val fare = if (host.script.altar == Altar.NATURE) NATURE_FARE else 0
        if (owner.inventory.computeAmountForId(COINS) < fare) return null
        val give = listOf(Item(note, BATCH)) +
            (if (seed) emptyList() else listOf(Item(rune.id, output))) +
            (if (fare > 0) listOf(Item(COINS, fare)) else emptyList())
        return Delivery(bot, owner, host.script.altar, seed, give,
            // Spoken terms and acknowledgements surround the separately bounded native trade.
            System.currentTimeMillis() + 4.minutes.inWholeMilliseconds, essence).also { host.delivery = it }
    }

    fun pending(bot: Bot): Delivery? = hosts[bot]?.delivery?.takeUnless { it.crafterDone }

    /**
     * Marks entry before the owning coroutine can suspend in the native trade helper.
     */
    fun start(delivery: Delivery, bot: Bot) {
        if (bot === delivery.runner) delivery.runnerStarted = true
    }

    /**
     * Rechecks session ownership throughout trade waits; a suspended participant cannot be silently replaced.
     */
    fun valid(delivery: Delivery): Boolean {
        val host = hosts[delivery.crafter] ?: return false
        val runner = delivery.runner.scriptStack.current() as? RuneRunnerBotScript ?: return false
        return host.delivery === delivery && runners[delivery.runner] === delivery.crafter &&
            delivery.crafter.state == EntityState.ACTIVE &&
            host.script.bot.scriptStack.current() === host.script && host.script.bot.preferences.isSkiller &&
            runner.altar == delivery.altar && runner.essenceId == delivery.essenceId &&
            host.script.essenceId == delivery.essenceId &&
            runnerEligible(delivery.runner) && ActionSafety.NON_COMBAT.allows(delivery.crafter) &&
            System.currentTimeMillis() < delivery.expires
    }

    /**
     * Holds the host's exchange slot until both independent trade coroutines have cleaned up.
     */
    fun complete(delivery: Delivery, bot: Bot) {
        if (bot === delivery.runner) delivery.runnerDone = true
        if (bot === delivery.crafter) delivery.crafterDone = true
        val host = hosts[delivery.crafter]
        if (host?.delivery === delivery && delivery.runnerDone && delivery.crafterDone) {
            delivery.dialogue.close()
            host.delivery = null
        }
    }

    fun release(bot: Bot) {
        val owner = runners.remove(bot) ?: return
        val host = hosts[owner] ?: return
        host.runners -= bot
        // Retain an in-flight slot until its crafter coroutine observes cancellation and leaves it.
        host.delivery?.takeIf { it.runner === bot }?.let { delivery ->
            delivery.dialogue.close()
            if (!delivery.runnerStarted) delivery.runnerDone = true
            if (delivery.runnerDone && delivery.crafterDone) host.delivery = null
        }
    }

    fun unregister(bot: Bot) {
        preparing.remove(bot)
        val host = hosts.remove(bot) ?: return
        host.delivery?.dialogue?.close()
        host.runners.forEach { runners.remove(it) }
    }
}
