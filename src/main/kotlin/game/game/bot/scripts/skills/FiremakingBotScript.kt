package game.bot.scripts.skills

import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FiremakingScriptFactory
import engine.bot.coordinator.BankResourceCoordinator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.firemaking.Firemaking
import game.skill.firemaking.Firemaking.TINDERBOX
import game.skill.firemaking.Log
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trains Firemaking by burning banked logs in straight lines at a [FiremakingSpot].
 *
 * The script withdraws a tinderbox and up to [LOGS_PER_TRIP] logs of one [log] type, walks to the eastern end of a
 * [FiremakingLane] beside the bank, and lights one log after another. Each fire steps the bot one tile west, so the
 * fires form a straight line. When the lane runs out or a fire is in the way, the bot picks another lane, and once its
 * logs are gone it banks for more. When fuel runs out, the remaining session time passes to compatible log gathering
 * and its explicit burning continuation.
 *
 * Only one bot firemakes at each spot. A bot that finds its spot taken, or every lane there burning, rejects the zone
 * so another one can be tried.
 *
 * Partial final batches are burned before gathering more logs. Startup counts inventory and bank supplies;
 * each bank visit sizes its batch from deposited bank stock. Inherited withdrawal verification requires
 * both unnoted logs and the tinderbox to reach the inventory. Forced bank requests use the inherited
 * bounded retry budget; verified withdrawals reset it, and snapshots retain it across resumes.
 * Pausing, finishing, and changing zones release the claimed spot and discard the transient lane.
 * Bank-relief sessions require an already owned tinderbox, consume only unreserved logs and never gather replacements.
 *
 * @property log The type of log this script burns.
 * @property bankRelief Whether this is a bounded owned-resource bank-relief session.
 * @author lare96
 * @author TheLining
 */
class FiremakingBotScript(bot: Bot, val log: Log, duration: Duration, zones: MutableList<SubZone>,
                          val bankRelief: Boolean = false) :
    InventoryBotScript(bot, duration, zones) {

    override val verifiedProductionWithdrawals = true
    override val ownedSuppliesOnly get() = bankRelief
    override val trainingSkill get() = if (bankRelief) SKILL_FIREMAKING else null
    override fun allowsLoot(item: io.luna.game.model.item.GroundItem): Boolean = !bankRelief && super.allowsLoot(item)

    // Lighting a log drops it on the bot's tile first.
    override val ignoredLoot = setOf(log.id)

    companion object {

        /**
         * The number of logs withdrawn every trip, leaving one slot for the tinderbox.
         */
        const val LOGS_PER_TRIP = 27

        /**
         * The number of fires in every lane. This is about half an inventory of logs, so a bot burns a full inventory
         * across two lines.
         */
        private const val LANE_LENGTH = 14

        /**
         * Determines if a fire is burning on [position]. Lanes are on open ground, so fires are the only thing that
         * can block them.
         */
        private fun hasFire(position: Position): Boolean =
            world.objects.findAll(position).anyMatch { it.id == Firemaking.FIRE_OBJECT }

        /**
         * Serializable data used to save and restore a [FiremakingBotScript].
         *
         * This extends [InventoryScriptData] with the [Log] type being burned, allowing the script to resume with the
         * same logs, remaining duration, candidate zones, and retry counters. Older snapshots default counters to zero.
         *
         * @author lare96
         */
        class FiremakingData : InventoryScriptData() {

            /**
             * The type of log the bot should burn.
             */
            var log: Log = Log.NORMAL

            /**
             * Whether this session may only consume expendable owned logs without gathering or acquiring supplies.
             */
            var bankRelief = false

            override fun load(data: JsonObject) {
                super.load(data)
                log = Log.valueOf(data.get("log").asString)
                bankRelief = data.get("bank_relief")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("log", log.name)
                if (bankRelief) data.addProperty("bank_relief", true) else data.remove("bank_relief")
            }
        }
    }

    /**
     * A straight row of tiles that a firemaking bot burns logs along.
     *
     * Lighting a log steps the player west whenever it can (see [game.skill.firemaking.LightLogAction]), so every lane
     * starts at its eastern end and runs [LANE_LENGTH] tiles west.
     *
     * @property start The eastern end of the lane, where the first log is lit.
     */
    data class FiremakingLane(val start: Position) {

        /**
         * Determines if [position] is one of this lane's tiles.
         */
        operator fun contains(position: Position): Boolean =
            position.y == start.y && position.z == start.z && start.x - position.x in 0 until LANE_LENGTH

        /**
         * Returns how many of the first [logs] tiles of this lane, in a row from its start, have no fire on them.
         */
        fun clearLength(logs: Int): Int =
            (0 until logs.coerceAtMost(LANE_LENGTH)).takeWhile { !hasFire(start.translate(-it, 0)) }.count()
    }

    /**
     * A location where bots train Firemaking: a few [FiremakingLane]s in the open ground beside a bank.
     *
     * Only one bot firemakes at a spot at a time, so bots don't block each other's lanes with fires. Each spot has a
     * few lanes, so one is usually clear while the others burn out.
     *
     * @property zone The subzone bots travel to and bank in.
     * @param starts The (x, y) start of each lane, in order of preference.
     */
    enum class FiremakingSpot(val zone: SubZone, vararg starts: Pair<Int, Int>) {

        /**
         * The road south of Varrock west bank.
         */
        VARROCK_WEST(SubZone.HOME, 3198 to 3431, 3198 to 3429, 3184 to 3427),

        /**
         * The road north of Varrock east bank, just west of its entrance.
         */
        VARROCK_EAST(SubZone.VARROCK_EAST_BANK, 3248 to 3429, 3248 to 3430, 3262 to 3429),

        /**
         * The open ground north and south of Draynor bank.
         */
        DRAYNOR(SubZone.DRAYNOR_MAIN, 3098 to 3248, 3101 to 3237, 3108 to 3235),

        /**
         * The square north of Falador east bank's entrance.
         */
        FALADOR_EAST(SubZone.FALADOR_EAST_BANK, 3023 to 3361, 3023 to 3363, 3023 to 3365),

        /**
         * The road south of Seers' Village bank.
         */
        SEERS_VILLAGE(SubZone.SEERS_VILLAGE_MAIN, 2733 to 3485, 2728 to 3484, 2714 to 3484),

        /**
         * The roads north and south of Al-Kharid bank.
         */
        AL_KHARID(SubZone.AL_KHARID_BANK, 3279 to 3178, 3279 to 3176, 3279 to 3159),

        /**
         * Open lanes north and west of Catherby's bank and range.
         */
        CATHERBY(SubZone.CATHERBY_FIREMAKING, 2815 to 3447, 2808 to 3433, 2819 to 3436),

        /**
         * The roads west and south-west of Yanille bank.
         */
        YANILLE(SubZone.YANILLE_FIREMAKING, 2611 to 3087, 2607 to 3097, 2614 to 3083),

        /**
         * Three roads around Ardougne's northern bank building.
         */
        ARDOUGNE_NORTH(SubZone.ARDOUGNE_NORTH_FIREMAKING, 2616 to 3342, 2615 to 3345, 2626 to 3338),

        /**
         * Outdoor rows west, north-east and south of Ardougne's southern bank.
         */
        ARDOUGNE_SOUTH(SubZone.ARDOUGNE_SOUTH_FIREMAKING, 2643 to 3289, 2669 to 3299, 2657 to 3277),

        /**
         * Open rows north and south of Falador's western bank.
         */
        FALADOR_WEST(SubZone.FALADOR_WEST_FIREMAKING, 2951 to 3375, 2952 to 3357, 2963 to 3381);

        companion object {

            /**
             * Mappings of subzones to the firemaking spot inside them.
             */
            val ZONE_TO_SPOT = entries.associateBy { it.zone }

            /**
             * The script firemaking at each spot. Bot scripts all run on the game thread, so no locking is needed.
             */
            private val firemakers = HashMap<FiremakingSpot, FiremakingBotScript>()

            /**
             * Removes [script] from its spot.
             */
            fun leave(script: FiremakingBotScript) {
                firemakers.values.remove(script)
            }
        }

        /**
         * The lanes bots burn logs along.
         */
        val lanes = starts.map { (x, y) -> FiremakingLane(Position(x, y)) }

        /**
         * Determines if a script other than [script] is firemaking here. A script that stopped, or whose bot logged
         * out, without leaving doesn't count.
         */
        fun isTaken(script: FiremakingBotScript? = null): Boolean {
            val current = firemakers[this] ?: return false
            return current != script && current.isRunning() && current.bot.state == EntityState.ACTIVE
        }

        /**
         * Makes [script] the firemaker here if no other bot is firemaking here.
         *
         * @return `true` if [script] is now firemaking here.
         */
        fun join(script: FiremakingBotScript): Boolean {
            if (isTaken(script)) {
                return false
            }
            firemakers[this] = script
            return true
        }

        /**
         * Returns the lane with the longest clear stretch for [logs] logs, preferring earlier lanes, or `null` if every
         * lane is burning at its start.
         */
        fun findLane(logs: Int): FiremakingLane? =
            lanes.maxByOrNull { it.clearLength(logs) }?.takeIf { it.clearLength(logs) > 0 }
    }

    /**
     * Recreates a firemaking script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved firemaking script data.
     */
    constructor(bot: Bot, data: FiremakingData) : this(bot, data.log, data.duration, data.zones, data.bankRelief) {
        restoreInventoryState(data)
    }

    /**
     * The lane the bot is burning logs along, or `null` if it needs a new one.
     */
    private var lane: FiremakingLane? = null

    /**
     * A depleted session hands off only once, even if another withdrawal callback arrives before cancellation settles.
     */
    private var gatheringQueued = false

    override fun withdraw(): List<Item> = withdrawBatch(bankOnly = false)

    override fun bankWithdraw(): List<Item> = withdrawBatch(bankOnly = true)

    /**
     * Sizes a partial batch from owned startup supplies or deposited bank stock after banking.
     */
    private fun withdrawBatch(bankOnly: Boolean): List<Item> {
        if (!bankOnly && productionRetriesExhausted()) { stop(); return emptyList() }
        if (bankRelief && (bot.firemaking.staticLevel < log.level ||
                !BankResourceCoordinator.canProcess(bot, SKILL_FIREMAKING, listOf(Item(log.id)), setOf(TINDERBOX)))) {
            stop()
            return emptyList()
        }
        if (bot.firemaking.staticLevel < log.level) {
            bot.log("I need Firemaking level ${log.level} to burn ${itemName(log.id)}.")
            stop()
            return emptyList()
        }
        val stock = if (bankOnly) bot.bank.computeAmountForId(log.id) else bot.itemTracker.count(log.id)
        if (stock < 1) {
            bot.log("I don't have enough ${itemName(log.id)} to burn.")
            chopMoreLogs()
            return emptyList()
        }
        if (TINDERBOX !in bot.itemTracker) {
            if (bankRelief || productionPlan != null) { stop(); return emptyList() }
            // Emergency spawn tinderbox if needed.
            bot.bank.add(Item(TINDERBOX))
        }
        if (bankRelief && !bankOnly) productionWithdraw(listOf(Item(log.id)), setOf(TINDERBOX), requestMissing = false)
        return listOf(Item(TINDERBOX), Item(log.id, minOf(stock, LOGS_PER_TRIP)))
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (bankRelief && (bot.firemaking.staticLevel < log.level ||
                !BankResourceCoordinator.canProcess(bot, SKILL_FIREMAKING, listOf(Item(log.id)), setOf(TINDERBOX)))) {
            stop()
            return true
        }
        if (log.id !in bot.inventory && bot.bank.computeAmountForId(log.id) < 1) {
            bot.log("I've burned all of my ${itemName(log.id)}.")
            chopMoreLogs()
            return true
        }

        if (TINDERBOX !in bot.inventory || log.id !in bot.inventory) {
            bot.log("No ${itemName(log.id)} or tinderbox left in inventory; requesting bank trip.")
            lane = null
            forceBanking = true
            return true
        }

        val lane = lane ?: return takeLane()
        val tile = bot.position
        if (tile !in lane || hasFire(tile)) {
            // We walked off the end of the lane, something stopped our step west, or a fire was lit in our way.
            bot.log("Done with $lane.")
            this.lane = null
            return true
        }

        val logs = bot.inventory.computeAmountForId(log.id)
        handler.inventory.useItem(TINDERBOX).onItem(log.id)
        if (!waitFor(2.seconds) { bot.inventory.computeAmountForId(log.id) < logs }) {
            bot.log("Couldn't light ${itemName(log.id)} on $tile. Trying another spot.")
            return false
        }

        // Lighting ends with a step off the new fire. Less dextrous bots are more often slow to use the next log.
        waitFor(10.seconds) { bot.position != tile }
        if (!rand(bot.personality.dexterity)) {
            bot.naturalMicroDelay()
        }
        return true
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        // Only bank once the carried batch runs out of logs.
        return forceBanking && requestProductionBank(canContinue = false)
    }

    override fun onPaused() = releaseSpot()

    override suspend fun finish() = releaseSpot()

    override suspend fun onNewActiveZone(lastZone: SubZone?) = releaseSpot()

    /**
     * Releases this session's spot claim and lane without changing another firemaker's claim.
     */
    private fun releaseSpot() {
        lane = null
        FiremakingSpot.leave(this)
    }

    override fun snapshot(): BotScriptData {
        val data = FiremakingData()
        saveInventoryState(data)
        data.log = log
        data.bankRelief = bankRelief
        return data
    }

    /**
     * Claims the bot's spot and walks to the start of its clearest lane.
     *
     * @return `false` to reject the spot if another bot has it, every lane there is burning, or the lane can't be
     * reached, otherwise `true`.
     */
    private suspend fun takeLane(): Boolean {
        val spot = FiremakingSpot.ZONE_TO_SPOT[activeZone!!] ?: return false
        if (!spot.join(this)) {
            bot.log("Another bot is firemaking at $spot. Trying another spot.")
            return false
        }

        val next = spot.findLane(bot.inventory.computeAmountForId(log.id))
        if (next == null) {
            bot.log("Every lane at $spot is burning. Trying another spot.")
            return false
        }

        bot.log("Walking to the start of $next.")
        if (!walkTo(next.start)) {
            bot.log("Couldn't reach the start of $next. Trying another spot.")
            return false
        }
        lane = next
        return true
    }

    /**
     * Walks onto the exact firemaking-lane start through the web, including doors on the approach.
     *
     * @return `true` if the bot is standing on [tile].
     */
    private suspend fun walkTo(tile: Position): Boolean = withTimeoutOrNull(30.seconds) {
        handler.webWalkToAny(listOf(tile)) && bot.position == tile && bot.walking.isEmpty
    } == true


    /**
     * Hands off remaining time to compatible fuel preparation, then stops this script.
     */
    private fun chopMoreLogs() {
        if (gatheringQueued) return
        gatheringQueued = true
        releaseSpot()
        if (bankRelief || productionPlan != null) { stop(); return }
        val remaining = remainingDuration()
        if (remaining > Duration.ZERO) {
            val script = FiremakingScriptFactory.getTrainingScript(bot, bot.firemaking.staticLevel,
                mutableListOf(), remaining)
            bot.scriptStack.softPushHead(script)
        }
        stop()
    }
}
