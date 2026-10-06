package game.bot.scripts.skills

import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.WoodcuttingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.firemaking.Firemaking
import game.skill.firemaking.Firemaking.TINDERBOX
import game.skill.firemaking.Log
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.path.astar.PlayerPathfinder
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trains Firemaking by burning banked logs in straight lines at a [FiremakingSpot].
 *
 * The script withdraws a tinderbox and [LOGS_PER_TRIP] logs of one [log] type, walks to the eastern end of a
 * [FiremakingLane] beside the bank, and lights one log after another. Each fire steps the bot one tile west, so the
 * fires form a straight line. When the lane runs out or a fire is in the way, the bot picks another lane, and once its
 * logs are gone it banks for more. When the bank runs low, the script queues a Woodcutting script and stops.
 *
 * Only one bot firemakes at each spot. A bot that finds its spot taken, or every lane there burning, rejects the zone
 * so another one can be tried.
 *
 * @property log The type of log this script burns.
 * @author TheLining
 */
class FiremakingBotScript(bot: Bot, val log: Log, duration: Duration, zones: MutableList<SubZone>) :
    InventoryBotScript(bot, duration, zones) {

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
         * This extends [ZonedBotScriptData] with the [Log] type being burned, allowing the script to resume with the
         * same logs, remaining duration, and candidate zones.
         */
        class FiremakingData : ZonedBotScriptData() {

            /**
             * The type of log the bot should burn.
             */
            var log: Log = Log.NORMAL

            override fun load(data: JsonObject) {
                super.load(data)
                log = Log.valueOf(data.get("log").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("log", log.name)
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
        AL_KHARID(SubZone.AL_KHARID_BANK, 3279 to 3178, 3279 to 3176, 3279 to 3159);

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
    constructor(bot: Bot, data: FiremakingData) : this(bot, data.log, data.duration, data.zones)

    /**
     * The lane the bot is burning logs along, or `null` if it needs a new one.
     */
    private var lane: FiremakingLane? = null

    override fun withdraw(): List<Item> {
        if (bot.firemaking.staticLevel < log.level) {
            bot.log("I need Firemaking level ${log.level} to burn ${itemName(log.id)}.")
            stop()
            return emptyList()
        }
        if (bot.itemTracker.count(log.id) < LOGS_PER_TRIP) {
            bot.log("I don't have enough ${itemName(log.id)} to burn.")
            chopMoreLogs()
            return emptyList()
        }
        if (TINDERBOX !in bot.itemTracker) {
            // Emergency spawn tinderbox if needed.
            bot.bank.add(Item(TINDERBOX))
        }
        return listOf(Item(TINDERBOX), Item(log.id, LOGS_PER_TRIP))
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (log.id !in bot.inventory && bot.bank.computeAmountForId(log.id) < LOGS_PER_TRIP) {
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
        // We start with a full inventory, so only bank once we run out of logs.
        return forceBanking
    }

    override fun onNewActiveZone(lastZone: SubZone?) {
        // Our lane and spot belong to the zone we're leaving. This also runs when the script pauses.
        lane = null
        FiremakingSpot.leave(this)
    }

    override fun snapshot(): BotScriptData {
        val data = FiremakingData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.log = log
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
     * Walks onto [tile]. Navigating would stop the bot a tile short, so this walks a player path like
     * [api.bot.zone.WalkingTravelStrategy] does.
     *
     * @return `true` if the bot is standing on [tile].
     */
    private suspend fun walkTo(tile: Position): Boolean {
        if (bot.position == tile) {
            return true
        }
        val path =
            bot.navigator.findPath(bot.position, tile, PlayerPathfinder(world.collisionManager, bot.z), true).await()
        if (path?.peekLast() != tile) {
            return false
        }
        bot.walking.replacePath(path)
        return waitFor(30.seconds) { bot.position == tile }
    }

    /**
     * Queues a Woodcutting script so the bot can chop more logs to burn, then stops this script.
     */
    private fun chopMoreLogs() {
        val script =
            WoodcuttingScriptFactory.getScript(bot, bot.woodcutting.staticLevel, mutableListOf(), randBoolean())
        bot.scriptStack.pushTail(script, 2)
        stop()
    }
}
