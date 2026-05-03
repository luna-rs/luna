package api.bot

import api.bot.Suspendable.naturalMicroDelay
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.base.Stopwatch
import com.google.gson.JsonObject
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.util.GsonUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Base script for bot activities that operate inside one selected subzone at a time.
 *
 * A zoned script owns the common lifecycle for activities that can be performed in multiple locations. It selects a
 * reachable active zone, travels there, delegates the actual activity to subclasses, keeps the bot near the selected
 * zone, handles banking, and completes once its configured duration expires.
 *
 * This is useful for skilling, harvesting, thieving, combat training, collecting resources, or any other activity where
 * the same behaviour can run in several candidate [SubZone] values.
 *
 * Subclasses provide the actual zone-specific behaviour through [executeInZone]. They can also customize setup, banking,
 * and pause cleanup through [onInit], [onBankRequired], [onBankItems], and [onPaused].
 *
 * @param bot The bot running this script.
 * @param duration The remaining amount of time this script should run before completing normally.
 * @param zones The candidate zones this script may operate in.
 *
 * @author lare96
 */
abstract class ZonedBotScript(bot: Bot, var duration: Duration, val zones: MutableList<SubZone>) :
    BotScript(bot) {

    /**
     * Creates a zoned script from persisted script data.
     *
     * @param bot The bot running this script.
     * @param data The saved zoned script data to restore from.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones)

    companion object {

        /**
         * Serializable data used to save and restore a [ZonedBotScript].
         *
         * This stores the generic state shared by all zoned scripts: remaining duration and candidate zones. Subclasses
         * may extend this type when they need to persist additional script-specific state.
         */
        open class ZonedBotScriptData : BotScriptData() {

            /**
             * The remaining amount of time this script should continue running for.
             */
            var duration: Duration = Duration.ZERO

            /**
             * The candidate zones this script can operate in.
             */
            var zones: MutableList<SubZone> = ArrayList(0)

            override fun load(data: JsonObject) {
                duration = data.get("duration").asLong.milliseconds
                zones = GsonUtils.getAsType(data.get("zones"), Array<String>::class.java)
                    .map { SubZone.valueOf(it) }
                    .toMutableList()
            }

            override fun save(data: JsonObject) {
                val zoneNames = zones.map { it.name }
                data.addProperty("duration", duration.inWholeMilliseconds)
                data.add("zones", GsonUtils.toJsonTree(zoneNames))
            }
        }
    }

    /**
     * Tracks elapsed real time between duration checks.
     *
     * The timer is reset after each duration update so [checkDuration] only subtracts the time that passed since the
     * previous script cycle.
     */
    private var timer = Stopwatch.createUnstarted()

    /**
     * The bank object currently cached for the selected active zone.
     *
     * This avoids resolving or re-clicking a bank object every banking cycle. It is cleared when the active zone changes,
     * when banking cannot be resolved, or when the script is paused.
     */
    private var cachedBank: GameObject? = null

    /**
     * Forces the next script cycle to perform a banking trip.
     *
     * Subclasses can set this when the bot should bank before its inventory is full, such as when supplies are low,
     * required tools are missing, loot should be deposited early, or the current activity cannot continue.
     */
    protected var forceBanking = false

    /**
     * The subzone currently selected as this script's working area.
     *
     * Once assigned, the bot will continue using this zone until the script completes, the zone becomes unusable, or
     * [executeInZone] returns `false`.
     */
    private var activeZone: SubZone? = null

    private val originalZones = zones.toList()

    /**
     * Initializes this zoned script.
     *
     * The script starts its duration timer, shuffles candidate zones, runs subclass initialization, travels to a bank,
     * deposits the bot's starting inventory, and lets subclasses withdraw initial supplies through [onBankItems].
     *
     * @param resumed Whether this script is being resumed from a previous paused or saved state.
     *
     * @return `true` if the script should stop during initialization, otherwise `false`.
     */
    final override suspend fun init(resumed: Boolean): Boolean {
        bot.log("Initializing. resumed=$resumed, duration=$duration, candidateZones=${zones.size}")

        timer.start()
        zones.shuffle()

        if (!onInit(resumed)) {
            bot.log("Initialization hook returned false. Stopping script.")
            return true
        }

        bot.log("Initial setup complete. Travelling to bank to deposit inventory.")

        if (!handler.banking.travelToBankDepositAll()) {
            bot.log("Failed initial bank deposit. Could not travel to bank and deposit inventory.")
            return true
        }

        bot.log("Initial bank deposit complete. Running initial banking hook.")
        onBankItems(true)

        bot.log("Initialization complete.")
        return false
    }

    /**
     * Runs one cycle of this zoned script.
     *
     * Each cycle enables running when enough run energy is available, checks whether the script duration has expired,
     * ensures an active zone exists, delegates activity behaviour to [executeInZone], handles banking if needed, and
     * applies a small natural delay.
     *
     * If [executeInZone] returns `false`, the current active zone is cleared so another candidate zone can be selected.
     *
     * @return `true` if the script should complete, otherwise `false`.
     */
    final override suspend fun run(): Boolean {
        if (bot.runEnergy >= 50 && !bot.walking.isRunning) {
            bot.walking.isRunning = true
            bot.log("Enabled running. runEnergy=${bot.runEnergy}")
        }

        if (!checkDuration()) {
            return true
        }

        if (!ensureActiveZone()) {
            bot.log("No active zone could be selected. Stopping script.")
            return true
        }

        if (!forceBanking && activeZone != null) {
            val zone = activeZone!!
            if (!executeInZone(zone)) {
                bot.log("Execution reported zone failure. Clearing active zone: $zone")
                activeZone = null
            }
        }
        bankIfNeeded()
        bot.naturalMicroDelay()
        return false
    }

    /**
     * Pauses this script and clears cached route-specific state.
     *
     * Paused time does not count against [duration]. The cached bank is cleared because the bot may resume from a
     * different location or after world state has changed.
     */
    final override fun paused() {
        bot.log("Pausing script. Clearing cached bank and stopping timer.")

        timer.stop()
        cachedBank = null

        onPaused()
    }

    /**
     * Runs one cycle of subclass-specific behaviour inside the selected active zone.
     *
     * Implementations should perform the actual activity for the script, such as chopping a tree, mining a rock,
     * fighting an NPC, stealing from a stall, collecting resources, or selecting a target inside the zone.
     *
     * @param zone The currently selected active zone.
     *
     * @return `true` if [zone] is still usable, or `false` to clear it and allow another zone to be selected.
     */
    abstract suspend fun executeInZone(zone: SubZone): Boolean

    /**
     * Runs subclass-specific initialization before the normal script loop begins.
     *
     * Subclasses can use this to validate configuration, choose tools, prepare temporary state, reject invalid goals, or
     * perform setup that must happen before the initial banking trip.
     *
     * @param resumed Whether this script is being resumed from a previous paused or saved state.
     *
     * @return `true` if initialization succeeded, otherwise `false`.
     */
    open fun onInit(resumed: Boolean): Boolean {
        return true
    }

    /**
     * Runs immediately before the script performs a banking cycle.
     *
     * This hook is called after the script determines that banking is required, but before it resolves or travels to a
     * bank. Subclasses can override this to stop actions, clear targets, cancel combat/skilling state, prepare inventory
     * state, or set any flags needed before banking begins.
     */
    open suspend fun onBankRequired() {

    }

    /**
     * Runs after the bot has deposited its inventory during a banking cycle.
     *
     * Subclasses can override this to withdraw tools, food, runes, ammo, potions, teleport items, or any other supplies
     * required before returning to the active zone.
     *
     * @param initial `true` when called during script initialization, or `false` when called during a normal banking
     * cycle.
     */
    open suspend fun onBankItems(initial: Boolean) {

    }

    /**
     * Runs when this script is paused.
     *
     * Subclasses can override this to clear temporary state, release selected targets, reset local counters, or discard
     * cached activity-specific data.
     */
    open fun onPaused() {

    }

    /**
     * Updates [duration] using the elapsed time since the previous duration check.
     *
     * @return `true` if this script still has time remaining, otherwise `false`.
     */
    private fun checkDuration(): Boolean {
        val elapsed = timer.elapsed().toMillis().milliseconds
        duration = duration.minus(elapsed)
        timer.reset().start()

        if (duration.isNegative()) {
            bot.log("Duration expired after subtracting elapsed=$elapsed. Completing script normally.")
            return false
        }

        return true
    }

    /**
     * Ensures this script has a reachable active zone.
     *
     * If [activeZone] is already set, this method succeeds immediately. Otherwise, it selects a candidate from [zones].
     * Dextrous bots prefer the closest candidate zone, while other bots use the shuffled list order. The first zone the
     * bot is already inside or can successfully travel to becomes the active zone.
     *
     * Unreachable dextrous candidates are removed so the bot does not keep selecting the same failed zone forever.
     *
     * @return `true` if an active zone is available, otherwise `false`.
     */
    private suspend fun ensureActiveZone(): Boolean {
        if (activeZone != null) {
            return true
        }

        bot.log("Selecting active zone. remainingCandidates=${zones.size}, dextrous=${bot.personality.isDextrous}")

        while (activeZone == null) {
            val newZone =
                if (bot.personality.isDextrous) {
                    zones.minByOrNull { bot.position.computeLongestDistance(it.inside) }
                } else {
                    zones.firstOrNull()
                }

            if (newZone == null) {
                bot.log("Failed to select active zone because no candidate zones remain.")
                return false
            }

            bot.log("Trying candidate zone: $newZone")

            if (bot.subZone == newZone) {
                activeZone = newZone
                cachedBank = null
                bot.log("Selected active zone because bot is already inside it: $newZone")
                return true
            }

            if (handler.travelTo(newZone)) {
                activeZone = newZone
                cachedBank = null
                bot.log("Selected active zone after successful travel: $newZone")
                return true
            }

            bot.log("Failed to travel to candidate zone: $newZone")
            zones.remove(newZone)
            if (zones.isEmpty()) {
                zones.addAll(originalZones)
                return true
            }
        }
        bot.log("Could not generate active zone. Trying again next script cycle.")
        return false
    }

    /**
     * Handles banking and active-zone recovery when needed.
     *
     * If the bot does not need to bank, this method only checks whether the bot has wandered too far from the active
     * zone and returns it when possible.
     *
     * If the inventory is full or [forceBanking] is set, and the bot isn't currently in combat, this method calls
     * [onBankRequired], resolves a usable bank from the active zone's parent, deposits the inventory, calls
     * [onBankItems], and returns the bot to the active zone.
     */
    private suspend fun bankIfNeeded() {
        val activeZone = activeZone ?: return

        if (!bot.inventory.isFull && !forceBanking) {
            if (!activeZone.isWithinDistance(bot, 64) && bot.walking.isEmpty) {
                bot.log("Bot wandered too far from active zone. Returning to $activeZone")
                handler.travelTo(activeZone)
            }
            return
        }

        onBankRequired()
        bot.log("Banking required. reason=${
            if (forceBanking) "forceBanking=true"
            else "inventoryFull=true"
        }, activeZone=$activeZone")

        val parent = activeZone.parent(bot)

        if (cachedBank == null) {
            bot.log("No cached bank. Resolving bank for parent zone: $parent")
            if (parent.banks.isNotEmpty() && handler.travelTo(parent)) {
                val sortedBanks = if (bot.personality.isDextrous || rand(bot.personality.dexterity))
                    parent.banks.sortedWith(LocatableDistanceComparator(bot)) else parent.banks
                for (bank in sortedBanks) {
                    if (handler.interactions.interact(2, bank)) {
                        cachedBank = bank
                        break
                    }
                }
            } else {
                bot.log("Could not access parent bank anchors. Falling back to home bank.")
                if (handler.travelTo(SubZone.HOME)) {
                    cachedBank = handler.banking.homeBank()
                }
            }
        }

        if (cachedBank != null) {
            bot.log("Interacting with cached bank.")
            if (!bot.bank.isOpen) {
                // We are interacting with a cached bank.
                if (!handler.interactions.interact(2, cachedBank)) {
                    bot.log("Banking aborted because interaction failed.")
                    cachedBank = null
                    forceBanking = true
                    return
                }
            }
            if (handler.banking.depositInventory()) {
                bot.log("Running banking hook. initial=false")
                onBankItems(false)

                bot.log("Returning to active zone after banking: $activeZone")
                forceBanking = false
                handler.travelTo(activeZone)
            } else {
                forceBanking = true
            }
        } else {
            bot.log("Banking aborted because cachedBank=null.")
        }
    }
}