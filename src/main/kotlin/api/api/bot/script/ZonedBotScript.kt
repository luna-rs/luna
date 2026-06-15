package api.bot.script

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import io.luna.game.model.Entity
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.mob.bot.Bot
import io.luna.util.GsonUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Provides the shared lifecycle for bot scripts that operate inside one selected [SubZone] at a time.
 *
 * A zoned script is built around a reusable loop:
 * - validate subclass setup
 * - optionally perform initial banking
 * - select a reachable candidate zone
 * - run subclass activity while the bot is close enough to that zone
 * - bank when required
 * - recover if the bot wanders too far away
 * - complete once the configured duration expires
 *
 * This class is intended for skilling, harvesting, thieving, combat training, resource collecting, and other bot
 * activities where the same behaviour can run in multiple candidate zones.
 *
 * Subclasses own the actual activity through [executeInZone]. They may also customize setup, banking, and pause
 * cleanup through [onInit], [onBankRequested], [onBankOpen], and [onPaused].
 *
 * @param bot The bot running this script.
 * @param duration The remaining amount of time this script should run before completing normally.
 * @param zones The mutable candidate-zone list used by this script while selecting and rotating active zones.
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
         * Serializable state shared by all [ZonedBotScript] implementations.
         *
         * This stores the generic state needed to resume a zoned script: the remaining duration and the candidate zones
         * that may still be selected. Subclasses may extend this type when they need to persist script-specific state.
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
     * The last wall-clock timestamp used to subtract elapsed runtime from [duration].
     */
    private var timestamp = 0L

    /**
     * The bank object currently cached for the selected active zone.
     *
     * This avoids resolving and clicking a bank from scratch every banking cycle. It is cleared when the active zone
     * changes, when the cached bank can no longer be interacted with, or when the script is paused.
     */
    protected var cachedBank: Entity? = null

    /**
     * Forces the next script cycle to perform a banking trip.
     *
     * Subclasses can set this when the bot should bank before its inventory is full, such as when supplies are low,
     * required tools are missing, loot should be deposited early, or the current activity cannot continue.
     */
    protected var forceBanking = false

    /**
     * Forces the script to deposit the entire inventory whenever a banking trip is performed.
     *
     * Subclasses can set this when the bot should mindlessly deposit its entire inventory.
     */
    protected var depositInventoryWhenBanking = true

    /**
     * The subzone currently selected as this script's working area.
     *
     * The bot keeps this zone until the script completes, the zone becomes unreachable, or [executeInZone] rejects
     * it. The script may keep an active zone while the bot is walking back toward it, but subclass execution is
     * only called once the bot is close enough to the zone.
     */
    protected var activeZone: SubZone? = null
        private set

    /**
     * The original candidate-zone list used for one retry pass after travel-based selection failures.
     *
     * Runtime selection may remove zones from [zones]. This snapshot lets the script make one rebuilt retry pass
     * before deciding that no zone can be reached.
     */
    val originalZones = zones.toList()

    final override suspend fun init(resumed: Boolean): Boolean {
        bot.log("Initializing. resumed=$resumed, duration=$duration, candidateZones=${zones.size}")

        if (originalZones.isEmpty()) {
            bot.log("Initialization failed. No candidate zones were configured.")
            return true
        }

        if (!onInit(resumed)) {
            bot.log("Initialization hook returned false. Stopping script.")
            return true
        }

        timestamp = System.currentTimeMillis()

        if (!resumed) {
            bot.log("Initial setup complete. Equipping required gear. candidateZones=${zones.size}")
            val equipmentLocator = equipment()
            if (equipmentLocator != null && !equipmentLocator.locateAndEquip()) {
                bot.log("Gear required for this script could not be equipped.")
                // TODO Function here that controls behaviour? Some scripts may require a cancellation, re-try, etc.
                bot.naturalDelay()
            }
            bot.log("Starting initial banking.")
            if (onBankRequested(true)) {
                if (!handler.banking.travelToBankDepositAll()) {
                    bot.log("Initial banking failed. Could not travel to bank and deposit inventory.")
                    return true
                }

                bot.log("Initial bank deposit complete. Running initial banking hook.")
                onBankOpen(true)
                bot.log("Initial banking hook complete.")
            } else {
                bot.log("Initial banking skipped. Banking request hook returned false.")
            }

            zones.shuffle()
        }

        bot.log("Initialization complete.")
        return false
    }

    final override suspend fun run(): Boolean {
        if (bot.runEnergy >= 50 && !bot.walking.isRunning) {
            bot.walking.isRunning = true
            bot.log("Enabled running. runEnergy=${bot.runEnergy}")
        }

        if (!checkDuration()) {
            return true
        }

        if (!ensureActiveZone()) {
            bot.log("No active zone could be selected. Stopping script. remainingCandidates=${zones.size}")
            return true
        }

        val zone = activeZone
        if (!forceBanking && zone != null && zone.isWithinDistance(bot, 64)) {
            if (!executeInZone()) {
                val removed = zones.remove(zone)
                bot.log(
                    "Execution rejected active zone. zone=$zone, removed=$removed, " +
                            "remainingCandidates=${zones.size}"
                )
                cachedBank = null
                activeZone = null

                if (zones.isEmpty()) {
                    bot.log("No candidate zones remain after execution rejection. Stopping script.")
                    return true
                }
            }
        }

        bankIfNeeded()
        bot.naturalMicroDelay()
        return false
    }

    final override fun paused() {
        bot.log("Pausing script. Clearing cached bank and active zone.")

        cachedBank = null
        activeZone = null
        onPaused()
    }

    /**
     * Runs one cycle of subclass-specific behaviour inside the selected active zone.
     *
     * Implementations should perform the actual activity for the script, such as chopping a tree, mining a rock,
     * fighting an NPC, stealing from a stall, collecting resources, or selecting a target inside the zone.
     *
     * This method is only called when the bot is close enough to the active zone and banking is not currently forced.
     * Return `false` when the zone should be removed from the current candidate list and another zone should be
     * selected on a later cycle.
     *
     * @return `true` if {@code zone} is still usable, or `false` to reject it.
     */
    abstract suspend fun executeInZone(): Boolean

    /**
     * Builds the equipment request used by this script.
     *
     * This method describes what gear the bot should try to wear during script execution. Implementations should
     * return a [BotGearLocator] that represents the desired equipment.
     *
     * @return A locator that can find and equip the gear required by this script, `null` to leave equipment as-is.
     */
    open suspend fun equipment(): BotGearLocator? {
        val purpose = if (randBoolean() || bot.personality.isSocial) setOf(BotGearPurpose.SHOW_OFF)
        else setOf(BotGearPurpose.SKILLING)
        return BotGearSelector.find(bot, purpose).buildLocator()
    }

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
     * Runs immediately before this script attempts a banking cycle.
     *
     * This hook is called after the script determines that banking is required, but before it resolves or travels to a
     * bank. Subclasses can override this to stop actions, clear targets, cancel combat or skilling state, prepare local
     * flags, or block banking while another state must finish first.
     *
     * @param initial `true` when called during script initialization, or `false` during normal banking.
     *
     * @return `false` to abort the banking request for this cycle, or `true` to start trying to bank.
     */
    open suspend fun onBankRequested(initial: Boolean): Boolean {
        return true
    }

    /**
     * Runs after the bot has opened a bank and deposited its inventory during a banking cycle.
     *
     * Subclasses can override this to withdraw tools, food, runes, ammo, potions, teleport items, or any other supplies
     * required before returning to the active zone.
     *
     * This hook is suspendable by design. The base class assumes that once this method returns, the subclass has either
     * prepared enough state to continue or has intentionally chosen to let a later script cycle request banking again.
     *
     * @param initial `true` when called during script initialization, or `false` during normal banking.
     */
    open suspend fun onBankOpen(initial: Boolean) {

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
        val now = System.currentTimeMillis()
        val elapsed = (now - timestamp).milliseconds
        duration = duration.minus(elapsed)
        timestamp = now

        if (duration <= Duration.ZERO) {
            bot.log("Duration expired. Completing script normally. elapsed=$elapsed")
            return false
        }

        return true
    }

    /**
     * Ensures this script has an active zone selected and starts travel toward it when needed.
     *
     * If an active zone already exists, this method succeeds immediately when the bot is close enough or already walking
     * back toward it. Otherwise, it attempts to return to that zone and clears it if travel fails.
     *
     * If no active zone exists, this method selects a candidate from [zones]. Dextrous bots prefer the closest
     * candidate, while other bots use the current shuffled list order. Failed travel candidates are removed from the
     * runtime candidate list. If all candidates fail once, the original candidate list is restored for one final retry
     * pass before the script gives up.
     *
     * @return `true` if an active zone exists or travel toward one has started, otherwise `false`.
     */
    private suspend fun ensureActiveZone(): Boolean {
        activeZone?.let { zone ->
            if (zone.isWithinDistance(bot, 64) || !bot.walking.isEmpty) {
                return true
            }

            bot.log("Bot wandered too far from active zone. Returning. activeZone=$zone")
            if (handler.travelTo(zone)) {
                bot.log("Return travel started for active zone. activeZone=$zone")
                return true
            }

            bot.log("Could not return to active zone. Clearing it. activeZone=$zone")
            cachedBank = null
            activeZone = null
        }

        bot.log("Selecting active zone. remainingCandidates=${zones.size}, dextrous=${bot.personality.isDextrous}")

        var rebuilt = false
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

            bot.log("Trying candidate zone. zone=$newZone, remainingCandidates=${zones.size}")

            if (bot.subZone == newZone) {
                activeZone = newZone
                cachedBank = null
                bot.log("Selected active zone because bot is already inside it. zone=$newZone")
                return true
            }

            if (handler.travelTo(newZone)) {
                activeZone = newZone
                cachedBank = null
                bot.log("Selected active zone after successful travel request. zone=$newZone")
                return true
            }

            zones.remove(newZone)
            bot.log("Failed to travel to candidate zone. zone=$newZone, remainingCandidates=${zones.size}")

            if (zones.isEmpty()) {
                if (rebuilt) {
                    bot.log("All candidate zones failed after rebuilt retry pass. Stopping script.")
                    return false
                }

                zones.addAll(originalZones)
                if (zones.size > 1) {
                    zones.shuffle()
                }

                bot.log("Rebuilt candidate zone list for final retry pass. candidateZones=${zones.size}")
                rebuilt = true
            }
        }

        bot.log("Could not generate active zone. Trying again next script cycle.")
        return false
    }

    /**
     * Handles banking when required and starts recovery travel when the bot drifts away from the active zone.
     *
     * If the bot does not need to bank, this method only checks whether the bot has wandered too far from the active
     * zone and starts return travel when possible.
     *
     * If the inventory is full [forceBanking] is set, this method calls [onBankRequested]. Subclasses
     * can return `false` there to delay banking, such as while combat, death, or another action state is still
     * resolving. When banking is allowed, the script resolves a bank from the active zone's parent, falls back to the home
     * bank when needed, deposits the inventory, runs [onBankOpen], then travels back to the active zone.
     */
    private suspend fun bankIfNeeded() {
        val activeZone = activeZone ?: return
        if (!bot.inventory.isFull && !forceBanking) {
            if (!activeZone.isWithinDistance(bot, 64) && bot.walking.isEmpty) {
                bot.log("Bot is outside active zone after activity cycle. Returning. activeZone=$activeZone")
                handler.travelTo(activeZone)
            }
            return
        }

        if (!onBankRequested(false)) {
            bot.log(
                "Banking request aborted by hook. inventoryFull=${bot.inventory.isFull}, " +
                        "forceBanking=$forceBanking, activeZone=$activeZone"
            )
            return
        }

        bot.log(
            "Banking required. reason=${if (forceBanking) "forceBanking" else "inventoryFull"}, " +
                    "activeZone=$activeZone"
        )

        val parent = activeZone.parent(bot)

        if (cachedBank == null) {
            bot.log("Resolving bank for parent zone. parent=$parent, bankCount=${parent.banks.size}")

            if (parent.banks.isNotEmpty() && handler.travelTo(parent)) {
                val sortedBanks =
                    if (bot.personality.isDextrous || rand(bot.personality.dexterity)) {
                        parent.banks.sortedWith(LocatableDistanceComparator(bot))
                    } else {
                        parent.banks
                    }

                for (bank in sortedBanks) {
                    val option: Int = bank.def().actions.filter { it != "null" }.size
                    if (handler.interactions.interact(option, bank)) {
                        cachedBank = bank
                        bot.log("Cached bank selected. parent=$parent, bank=$bank, option=$option")
                        break
                    }
                }

                if (cachedBank == null) {
                    bot.log("No parent bank could be interacted with. parent=$parent, bankCount=${parent.banks.size}")
                    bot.log("Trying to find banking NPC.")
                    val npc = world.locator.findNpcs(activeZone.inside, activeZone.area.tileRadius)
                    { it.def().actions.contains("Bank") }.firstOrNull()
                    if (npc != null) {
                        bot.log("Banking NPC ${npc.id} found!")
                        val option: Int = npc.def().actions.filter { it != "null" }.size
                        if (handler.interactions.interact(option, npc)) {
                            cachedBank = npc
                        }
                    }
                    if (cachedBank == null) {
                        bot.log("Could not access parent bank anchors. Falling back to home bank. parent=$parent")
                        if (handler.travelTo(SubZone.HOME)) {
                            cachedBank = handler.banking.homeBank()
                            bot.log("Home bank fallback result. cachedBank=$cachedBank")
                        } else {
                            bot.log("Home bank fallback failed. Could not travel to home subzone.")
                        }
                    }
                }
            }
        }

        if (cachedBank == null) {
            bot.log("Banking aborted because no usable bank was resolved. activeZone=$activeZone")
            forceBanking = true
            return
        }

        bot.log("Preparing to interact with bank. cachedBank=$cachedBank, bankOpen=${bot.bank.isOpen}")
        if (!bot.bank.isOpen && !handler.interactions.interact(2, cachedBank)) {
            bot.log("Banking aborted because cached bank interaction failed. Clearing cached bank. cachedBank=$cachedBank")
            cachedBank = null
            forceBanking = true
            return
        }

        if (depositInventoryWhenBanking && !handler.banking.depositInventory()) {
            bot.log("Auto-inventory deposit failed. Will retry banking next cycle. activeZone=$activeZone")
            forceBanking = true
            return
        }
        bot.log("Running banking hook. initial=false")
        onBankOpen(false)
        bot.log("Banking hook complete. Returning to active zone. activeZone=$activeZone")
        forceBanking = false
        handler.travelTo(activeZone)
    }
}
