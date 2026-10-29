package api.bot.script

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import io.luna.game.model.mob.bot.Bot
import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.action.ActionType
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A zone-based bot script that searches for, selects, and maintains focus on a target entity.
 *
 * This base class is intended for scripts that repeatedly interact with world entities inside an active zone, such as
 * trees, rocks, fishing spots, stalls, NPCs, or other interactable resources.
 *
 * The script provides the common targeting loop:
 *
 * - Reuse a focused target while it remains valid.
 * - Invoke [refocus] every cycle before deciding whether a new target is needed.
 * - Clear stale focus state when a new target search is required.
 * - Reuse cached target options before performing another world scan.
 * - Sort target options by distance for dextrous bots.
 * - Shuffle target options when distance sorting does not trigger.
 * - Attempt each candidate until one interaction succeeds.
 * - Track repeated failed searches and optionally abandon crowded zones.
 *
 * [refocus] is intentionally called every execution cycle, even when the current focus is missing or invalid. This gives
 * subclasses a consistent hook for script-specific maintenance, cleanup, retry logic, or forced refocus decisions.
 *
 * Subclasses must provide target discovery through [find]. They may also override [interactionOption], [refocus],
 * [onExecuteInZone], [onBankRequestedTargeting], or [onAssignFocus] to customize targeting behaviour.
 *
 * @param E The type of entity this script can target.
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may operate in.
 * @author lare96
 */
abstract class TargetingZonedBotScript<E : Entity>(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>
) : ZonedBotScript(bot, duration, zones) {

    /**
     * The entity currently being targeted by this script.
     *
     * A focused target is reused while it remains active, nearby, and accepted by the current script. The focus is cleared
     * when the script pauses, when banking is required, or when a new target search is started.
     */
    protected var focus: E? = null
        private set

    /**
     * Cached target options from the most recent search.
     *
     * The cache prevents this script from scanning the active zone every cycle. Options are removed as they are attempted,
     * which prevents failed, inactive, or rejected targets from being retried until a fresh search is performed.
     */
    protected var lastOptions: MutableList<E> = mutableListOf()
        private set

    /**
     * The number of consecutive fresh searches performed without selecting a new focus.
     *
     * This value resets when a target is successfully selected. Once it reaches the threshold inside [executeInZone], the
     * bot may move closer to the zone or abandon the zone if it appears crowded.
     */
    private var searchTimes = 0

    /** Maximum time spent searching without selecting a target; opt-in for each script. */
    protected open val targetSearchTimeout: Duration = Duration.INFINITE

    /** Maximum discovery travel before abandoning a distant Mob candidate. */
    protected open val targetApproachTimeout: Duration = 30.seconds

    /** Activities with changing resource priorities can request a fresh view for each selection. */
    protected open val reuseTargetOptions: Boolean = true

    /** Failed fresh searches before considering a crowded-area departure; ordinary activities keep ten. */
    protected open val crowdedSearchLimit: Int = 10

    /** Activity-specific camp decisions; other targeting scripts retain their existing policy. */
    protected open fun shouldLeaveTargetZone(): Boolean = false

    protected open fun shouldLeaveCrowdedZone(visiblePlayers: Int): Boolean =
        rand(bot.personality.intelligence) && zones.isNotEmpty() && visiblePlayers > 20

    /** Orders candidates before an attempt, including cached options whose ownership may have changed. */
    protected open fun orderTargets(options: MutableList<E>) {
        val dexterity = bot.personality.dexterity
        val sortChance = dexterity * 0.75
        val sortByDistance = rand(sortChance)
        bot.log("Target option ordering roll: dexterity=$dexterity, sortChance=$sortChance, " +
                    "rollPassed=$sortByDistance, options=${options.size}")
        if (sortByDistance && options.size > 1) {
            options.sortWith(LocatableDistanceComparator(bot))
            bot.log("Sorted ${options.size} target option(s) by distance due to dexterity.")
        } else {
            options.shuffle()
            bot.log("Shuffled ${options.size} target option(s); dexterity distance-sort did not trigger.")
        }
    }

    /** Monotonic clock, overridable for deterministic recovery tests. */
    protected open fun targetSearchTimeNanos(): Long = System.nanoTime()

    private var searchStartedAt: Long? = null
    private var searchZone: SubZone? = null

    private fun abandonExpiredSearch(): Boolean {
        val startedAt = searchStartedAt ?: return false
        val elapsed = (targetSearchTimeNanos() - startedAt).nanoseconds
        if (elapsed < targetSearchTimeout) {
            return false
        }
        bot.log("Abandoning $activeZone after $elapsed without selecting an interactable target.")
        clearTargetCache("target search timed out")
        return true
    }

    /**
     * Executes one targeting cycle inside the active zone.
     *
     * This method always calls [refocus] before deciding whether a new target search is needed. This is intentional, even
     * when the current focus is already invalid or missing, because subclasses may use [refocus] as a general per-cycle
     * hook for cleanup, state updates, retry logic, or forced target switching.
     *
     * After [refocus] runs, this method searches for a new target when either:
     *
     * - The current [focus] is missing, inactive, or too far away.
     * - [refocus] returns `true`.
     *
     * When a new target is needed, this method:
     *
     * - Calls [onExecuteInZone] with `searching` set to `true`.
     * - Clears the current focus.
     * - Reuses cached target options when available.
     * - Performs a fresh search through [find] when no cached options remain.
     * - Orders cached options based on bot dexterity.
     * - Attempts each target until one interaction succeeds.
     * - Stores the first successful target as the new focus.
     *
     * If repeated searches fail, the bot may move closer to the zone or abandon the zone when the area appears crowded.
     *
     * If no new target is needed, this method calls [onExecuteInZone] with `searching` set to `false`.
     *
     * @return `true` if this zone should remain active, or `false` if this script should abandon the zone and allow
     * [ZonedBotScript] to choose another one.
     */
    final override suspend fun executeInZone(): Boolean {
        if (!targetActionReady()) return true
        if (shouldLeaveTargetZone()) return false
        if (searchZone != activeZone) {
            clearTargetCache("active zone changed")
            searchZone = activeZone
        }
        val currentFocus = focus
        val invalidReason = getInvalidFocusReason(currentFocus)
        val refocus = refocus()
        if (!targetActionReady()) return true
        val zone = activeZone!!
        if(forceBanking) {
            // Don't proceed if we need to bank.
            return true
        }
        if (invalidReason != null || refocus) {
            if (searchStartedAt == null) {
                searchStartedAt = targetSearchTimeNanos()
            }
            if (abandonExpiredSearch()) {
                return false
            }
            onExecuteInZone(true)
            if (!targetActionReady()) return true
            focus = null
            bot.log("Searching for a new target in $zone because ${invalidReason ?: "refocus was requested"}.")

            if (lastOptions.isEmpty() || !reuseTargetOptions) {
                val searchRadius = zone.area.tileRadius
                lastOptions = find(zone.area.centerPosition, searchRadius).toMutableList()
                if (!targetActionReady()) return true
                searchTimes++
                bot.log("Found ${lastOptions.size} target option(s) in $zone using search radius $searchRadius.")
            }

            orderTargets(lastOptions)

            val iterator = lastOptions.iterator()

            while (iterator.hasNext()) {
                // Check between attempts: one scan can contain many expensive path searches.
                if (abandonExpiredSearch()) {
                    return false
                }
                if (!targetActionReady()) return true
                val target = iterator.next()
                iterator.remove()

                val accepted = target.state == EntityState.ACTIVE && onAssignFocus(target)
                if (!targetActionReady(target)) return true
                if (!accepted) continue
                val needsApproach = target is Mob && !bot.position.isViewable(target.position)
                val approached = approachTarget(target)
                if (!targetActionReady(target)) return true
                if (!approached) continue
                // Eligibility can change during travel (combat restrictions, inventory, etc.).
                if (needsApproach) {
                    val stillAccepted = onAssignFocus(target)
                    if (!targetActionReady(target)) return true
                    if (!stillAccepted) continue
                }
                if (forceBanking) {
                    return true
                }
                val option = interactionOption(target)
                if (!targetActionReady(target)) return true
                if (option == null) continue
                val interacted = interactWithTarget(option, target)
                if (!targetActionReady(target)) return true
                if (!interacted) {
                    bot.log("Failed to interact with target ${describeTarget(target)} using option $option.")
                    continue
                }

                focus = target
                searchTimes = 0
                searchStartedAt = null
                bot.log("Selected new focus target ${describeTarget(target)}.")
                return true
            }

            if (abandonExpiredSearch()) {
                return false
            }
            bot.naturalDecisionDelay()
            if (!targetActionReady()) return true

            if (searchTimes >= crowdedSearchLimit) {
                val visiblePlayers =
                    world.locator.findPlayers(zone.area.centerPosition, zone.area.tileRadius) { true }.size

                val shouldLeaveCrowdedZone = shouldLeaveCrowdedZone(visiblePlayers)

                if (bot !in zone.area && handler.webWalkTo(zone)) {
                    bot.log("No interactable targets found. Moving closer to active zone.")
                    return true
                }

                if (shouldLeaveCrowdedZone) {
                    bot.log(
                        "Abandoning $zone because no target was interactable and the area is crowded. " +
                                "Viewable players: $visiblePlayers."
                    )
                    return false
                }

                bot.log("No interactable targets found in $zone, but staying because no better zone decision was made.")
                return true
            } else {
                return true
            }
        }

        onExecuteInZone(false)
        return true
    }

    /**
     * Preserves the current zone and unattempted candidate when another activity interrupts targeting.
     * Recovery time is not treated as a failed search or included in its abandonment timeout.
     */
    private fun targetActionReady(candidate: E? = null): Boolean {
        if (isActionSafe()) return true
        searchStartedAt = null
        searchTimes = 0
        if (candidate != null) lastOptions.add(candidate)
        return false
    }

    /**
     * Approaches a newly discovered Mob through the web, with a bounded discovery deadline. Objects use
     * the shared web-backed interaction approach directly. This does not
     * weaken NavigationAction's dead/out-of-view Mob guard or continuously chase an unseen moving target.
     */
    protected suspend fun approachTarget(target: E): Boolean {
        if (!isActionSafe()) return false
        if (target.state != EntityState.ACTIVE) return false
        if (target !is Mob) return true
        if (!target.isAlive) return false
        if (bot.position.isViewable(target.position)) return true

        val remainingSearch = searchStartedAt?.let {
            targetSearchTimeout - (targetSearchTimeNanos() - it).nanoseconds
        } ?: targetSearchTimeout
        val timeout = minOf(targetApproachTimeout, remainingSearch)
        if (timeout <= Duration.ZERO || forceBanking || bot.navigator.isActive) return false

        return handler.interactions.approach(target, timeout = timeout, allowOutOfView = true) {
            isActionSafe() && !forceBanking &&
                    (searchStartedAt == null ||
                        (targetSearchTimeNanos() - searchStartedAt!!).nanoseconds < targetSearchTimeout)
        }
    }

    /**
     * Clears targeting state when this script is paused.
     */
    final override fun onPaused() {
        clearTargetCache("script paused")
    }

    /**
     * Handles a banking request and clears targeting state when banking should continue.
     *
     * Subclasses can override [onBankRequestedTargeting] to decide whether banking is actually required. If banking is
     * required, the current focus and cached options are cleared so the script does not resume stale targeting state
     * after banking completes.
     *
     * @param initial `true` if this is the first banking check for the current banking request.
     *
     * @return `true` if banking should continue, or `false` if no banking is required.
     */
    final override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (onBankRequestedTargeting(initial)) {
            clearTargetCache("banking required")
            return true
        }
        return false
    }

    /**
     * Runs once every targeting cycle before the script decides whether to search for a new target.
     *
     * This method is called even when [focus] is `null`, inactive, or otherwise invalid. The default implementation only
     * attempts to resume interaction when the current focus is active and the bot has no weak action running.
     *
     * Subclasses can override this method to perform per-cycle maintenance, clear custom state, retry an interaction,
     * react to a stale focus, or force the targeting loop to search again.
     *
     * Returning `true` forces [executeInZone] to discard the current focus and search for another target. Returning
     * `false` allows [executeInZone] to make that decision from the current focus validity check.
     *
     * @return `true` if this script should search for another target, or `false` if no forced refocus is required.
     */
    open suspend fun refocus(): Boolean {
        if (isActionSafe() && focus?.state == EntityState.ACTIVE && bot.actions.size(ActionType.WEAK) == 0) {
            val option = interactionOption(focus!!) ?: return true
            if (!isActionSafe()) return false
            interactWithTarget(option, focus!!)

            if (rand(bot.personality.dexterity)) {
                bot.naturalDelay()
            } else {
                bot.naturalDecisionDelay()
            }
        }
        return false
    }

    /**
     * Finds target entities that this bot can attempt to interact with.
     *
     * The returned targets do not need to be sorted. This base class may sort or shuffle them before interaction based on
     * the bot's personality.
     *
     * @param searchBase The tile to search from, usually the center of the active zone.
     * @param searchRadius The tile radius to search within, usually based on the active zone size.
     *
     * @return A mutable collection of candidate target entities.
     */
    abstract suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<E>

    /**
     * Returns the interaction option used when attempting to interact with a target.
     *
     * Most first-option actions use `1`, such as chop, mine, fish, attack, steal, or pick depending on the target type.
     * Subclasses can override this when a different context-menu option should be used.
     *
     * @param target The target being interacted with.
     * @return The interaction option index to send.
     */
    open suspend fun interactionOption(target: E): Int? = 1

    /**
     * Dispatches a selected interaction. Subclasses may retain live eligibility checks during navigation.
     */
    protected open suspend fun interactWithTarget(option: Int, target: E): Boolean =
        handler.interactions.interact(option, target)

    /**
     * Runs script-specific logic during each zone execution cycle.
     *
     * Subclasses can use this hook to update state, emit speech, check supplies, adjust behaviour, or perform other
     * script-specific logic while either searching for a new target or continuing an existing focus.
     *
     * @param searching `true` when this script is searching for a new target, or `false` when it is continuing normally.
     */
    open suspend fun onExecuteInZone(searching: Boolean) {
    }

    /**
     * Handles script-specific banking checks before targeting state is cleared.
     *
     * Returning `true` tells the base script that banking is required and that the current target cache should be
     * discarded. Returning `false` leaves the current focus and cached options intact.
     *
     * @param initial `true` if this is the first banking check for the current banking request.
     *
     * @return `true` if banking should continue, or `false` if no banking is required.
     */
    open suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        return true
    }

    /**
     * Validates a target immediately before it becomes the current focus.
     *
     * Subclasses can override this to reject targets that are technically found but not currently desirable, such as
     * depleted resources, occupied stalls, unreachable entities, dangerous NPCs, or objects reserved by another bot.
     *
     * @param newFocus The target candidate about to be assigned as [focus].
     *
     * @return `true` if the target may be interacted with, or `false` if it should be skipped.
     */
    open suspend fun onAssignFocus(newFocus: E): Boolean {
        return true
    }

    /**
     * Returns the reason the current focus cannot be reused.
     *
     * A `null` result means the focus is valid enough for this base script to keep using it. This check does not control
     * whether [refocus] is called; [refocus] is always called before this result is acted on.
     *
     * @param currentFocus The current focus, or `null` if no focus has been selected.
     *
     * @return A human-readable invalidation reason, or `null` if the focus is still usable.
     */
    private fun getInvalidFocusReason(currentFocus: E?): String? {
        return when {
            currentFocus == null ->
                "there is no current focus"

            currentFocus.state != EntityState.ACTIVE ->
                "the current focus is no longer active: ${describeTarget(currentFocus)}"

            !bot.isWithinDistance(currentFocus, Region.SIZE) ->
                "the current focus is no longer within region distance: ${describeTarget(currentFocus)}"

            else -> null
        }
    }

    /**
     * Clears the current focus and cached target options.
     *
     * This is used when cached target state should no longer be trusted, such as after pausing or when banking begins.
     *
     * @param reason The reason cached targeting state is being reset.
     */
    private fun clearTargetCache(reason: String) {
        focus = null
        searchTimes = 0
        searchStartedAt = null
        lastOptions = mutableListOf()
        bot.log("Cleared targeting cache because $reason.")
    }

    /**
     * Builds a compact debug description for a target entity.
     *
     * @param target The target to describe.
     *
     * @return A short debug string containing the target type and position.
     */
    private fun describeTarget(target: Entity): String {
        return "${target::class.simpleName}@${target.position}"
    }
}
