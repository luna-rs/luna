package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.RuneCrafterBotScript
import game.bot.scripts.RuneRunnerBotScript
import game.bot.scripts.RuneRunningDialogue
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
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
     * Surface routes supported by this activity. Death's mainland approach remains unverified.
     */
    val ALTARS = setOf(Altar.NATURE, Altar.LAW, Altar.AIR)

    /**
     * Air runners can carry the ordinary essence available from level-one mining.
     */
    fun essence(altar: Altar): Int = if (altar == Altar.AIR) 1436 else ESSENCE

    /**
     * A single immutable trade contract; completion flags belong to the two owning coroutines.
     *
     * @author lare96
     */
    class Delivery internal constructor(val runner: Bot, val crafter: Bot, val altar: Altar,
                                       val seed: Boolean, val crafterGives: List<Item>,
                                       val expires: Long) {
        val runnerGives: List<Item> = if (seed) emptyList() else listOf(Item(essence(altar), BATCH))
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

    /**
     * Counts usable owned currency before dispatch or packing, without relying on cached trackers.
     */
    internal fun coins(bot: Bot): Long = bot.bank.computeAmountForId(COINS).toLong() +
        bot.inventory.computeAmountForId(COINS)

    /**
     * Runner eligibility is based on actual account state and combat level, never a Runecrafting requirement.
     */
    internal fun runnerEligible(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && bot.combatLevel < 50 &&
        bot.isAlive && !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)

    /**
     * A nature runner must fund its initial return fare. Each paid delivery reimburses the next round trip.
     */
    internal fun canRun(bot: Bot, altar: Altar): Boolean = altar in ALTARS && runnerEligible(bot) &&
        (altar != Altar.NATURE || coins(bot) >= NATURE_FARE)

    /**
     * The new-account coordinator dispatches only when a real prepared host has capacity.
     */
    fun canDispatch(bot: Bot): Boolean = bot.preferences.runeRunning.isIntroductory &&
        bot.preferences.runeRunning.canStartSession(System.currentTimeMillis()) &&
        bot.scriptStack.size() == 0 && availableHost(bot) != null

    override fun accept(bot: Bot) {
        if (!canDispatch(bot)) return
        if (!bot.preferences.runeRunning.trySession(System.currentTimeMillis(), 3.minutes.inWholeMilliseconds)) return
        val host = reserve(bot) ?: return
        bot.scriptStack.push(RuneRunnerBotScript(bot, host, introductory = true, duration = 30.minutes))
    }

    /**
     * Offers an occasional bounded money-making session to established low-level accounts.
     */
    fun profitScript(bot: Bot): BotScript? {
        if (!runnerEligible(bot) || availableHost(bot) == null || !rand(0.02)) return null
        if (!bot.preferences.runeRunning.trySession(System.currentTimeMillis(), 45.minutes.inWholeMilliseconds)) return null
        val altar = reserve(bot) ?: return null
        return RuneRunnerBotScript(bot, altar, introductory = false, duration = rand(15, 30).minutes)
    }

    /**
     * Occasionally replaces an ordinary Runecrafting session when actual runner demand and owned supplies exist.
     * The account's skiller identity remains authoritative even if another account has identical skill levels.
     */
    fun crafterScript(bot: Bot, duration: Duration): BotScript? {
        prune()
        if (!bot.preferences.isSkiller || !ActionSafety.NON_COMBAT.allows(bot) || !rand(0.20)) return null
        val demand = world.players.filterIsInstance<Bot>().filter { it !== bot && runnerEligible(it) && it !in runners }
        if (demand.isEmpty()) return null
        val candidates = ALTARS.filter { altar ->
            bot.runecrafting.level >= CraftableRune.ALTAR_TO_RUNE.getValue(altar).level &&
                bot.bank.computeAmountForId(essence(altar)).toLong() + bot.inventory.computeAmountForId(essence(altar)) >= BATCH * 11 &&
                bot.bank.computeAmountForId(altar.talisman).toLong() + bot.inventory.computeAmountForId(altar.talisman) > 0 &&
                hosts.values.none { it.script.altar == altar } && demand.any { canRun(it, altar) } &&
                (altar != Altar.NATURE || coins(bot) >= NATURE_FARE * 11L)
        }
        val weighted = candidates.flatMap { altar -> List(if (altar == Altar.NATURE) 6 else if (altar == Altar.LAW) 2 else 1) { altar } }
        val altar = weighted.randomOrNull() ?: return null
        return RuneCrafterBotScript(bot, altar, duration)
    }

    /**
     * Removes abandoned reservations and hosts before offering new work.
     */
    private fun prune() {
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

    private fun availableHost(bot: Bot, altar: Altar? = null, resuming: Boolean = false): Host? {
        prune()
        return hosts.values.filter { host -> host.script.bot !== bot &&
            (altar == null || host.script.altar == altar) &&
            (canRun(bot, host.script.altar) || resuming && runnerEligible(bot) &&
                bot.position.isWithinDistance(host.script.altar.exit, 3) && coins(bot) >= 30) &&
            host.runners.size < MAX_RUNNERS && host.script.canSupply() }
            .minByOrNull { host -> host.runners.size * 10 + when (host.script.altar) {
                Altar.NATURE -> 0
                Altar.LAW -> 1
                else -> 2
            } }
    }

    /**
     * Reserves one host before dispatch; a resumed runner may reacquire only its saved altar.
     */
    fun reserve(bot: Bot, altar: Altar? = null): Altar? {
        runners[bot]?.let { owner ->
            val existing = hosts[owner]
            if (existing != null && (altar == null || existing.script.altar == altar) && runnerEligible(bot))
                return existing.script.altar
            release(bot)
        }
        val host = availableHost(bot, altar, resuming = altar != null) ?: return null
        host.runners += bot
        runners[bot] = host.script.bot
        return host.script.altar
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
        return true
    }

    fun host(bot: Bot): Bot? = runners[bot]?.takeIf { owner ->
        hosts[owner]?.script?.let { it.bot.state == EntityState.ACTIVE &&
            it.bot.scriptStack.current() === it && it.bot.preferences.isSkiller } == true
    }

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
        val essence = essence(host.script.altar)
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
            System.currentTimeMillis() + 4.minutes.inWholeMilliseconds).also { host.delivery = it }
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
        return host.delivery === delivery && runners[delivery.runner] === delivery.crafter &&
            delivery.crafter.state == EntityState.ACTIVE &&
            host.script.bot.scriptStack.current() === host.script && host.script.bot.preferences.isSkiller &&
            delivery.runner.scriptStack.current() is RuneRunnerBotScript &&
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
        val host = hosts.remove(bot) ?: return
        host.delivery?.dialogue?.close()
        host.runners.forEach { runners.remove(it) }
    }
}
