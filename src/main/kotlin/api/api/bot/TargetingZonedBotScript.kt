package api.bot

import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * A [ZonedBotScript] that searches for, selects, and maintains focus on a target entity.
 *
 * This is useful for scripts where the bot repeatedly interacts with world entities inside an active zone, such as
 * trees, rocks, fishing spots, stalls, NPCs, or other interactable resources.
 *
 * The script handles the common targeting loop:
 * - Clear cached targets when the active zone changes.
 * - Reuse previously found options while they remain valid.
 * - Sort candidates by distance for dextrous bots.
 * - Attempt each candidate until one can be interacted with.
 * - Maintain the successful entity as the current focus.
 * - Abandon overcrowded zones when the bot is intelligent enough to do so.
 *
 * Subclasses provide the actual search logic through [find], the interaction option through [interactionOption], and
 * optional per-cycle behaviour through [onExecuteInZone].
 *
 * @param E The type of entity this script targets.
 * @param bot The bot running this script.
 * @param duration How long this script should continue running before completing normally.
 * @param zones The candidate zones this script may operate in.
 */
abstract class TargetingZonedBotScript<E : Entity>(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>
) : ZonedBotScript(bot, duration, zones) {

    /**
     * The entity currently being targeted or interacted with.
     *
     * This is cleared when the active zone changes, when the script pauses, or when the focus becomes inactive,
     * unreachable, no longer visible, or no longer associated with an active bot action.
     */
    private var focus: E? = null

    /**
     * Cached targeting options from the most recent search.
     *
     * This prevents the script from repeatedly scanning the zone every cycle. Options are removed as they are attempted,
     * so failed or invalid targets are not retried until a new search is performed.
     */
    private var lastOptions: MutableCollection<E> = mutableListOf()

    /**
     * Executes one targeting cycle inside the selected active zone.
     *
     * This method maintains a focused target entity for scripts that repeatedly interact with world entities, such as
     * trees, rocks, stalls, fishing spots, or NPCs.
     *
     * If the current focus is still valid, this method calls [onExecuteInZone] with `searching` set to `false` and keeps
     * using the existing focus.
     *
     * If the current focus is missing or stale, this method:
     *
     * - Clears the stale focus.
     * - Runs [onExecuteInZone] with `searching` set to `true`.
     * - Reuses cached target options when available.
     * - Performs a fresh target search only when the option cache is empty.
     * - Optionally sorts cached targets by distance for dextrous bots.
     * - Attempts each target until one interaction succeeds.
     * - Stores the first successfully interacted target as the new focus.
     *
     * If a fresh search was performed and no target could be interacted with, intelligent bots may abandon crowded zones
     * so [ZonedBotScript] can select another active zone.
     *
     * @param zone The active zone selected by [ZonedBotScript].
     *
     * @return `true` if this zone should remain active, or `false` if this script should abandon the zone and allow
     * [ZonedBotScript] to choose another one.
     */
   final override suspend fun executeInZone(zone: SubZone): Boolean {
        val currentFocus = focus
        val invalidReason = getInvalidFocusReason(currentFocus)
        if (invalidReason != null || refocus()) {
            focus = null

            var freshSearch = false
            bot.log("Searching for a new target in $zone because $invalidReason.")

            onExecuteInZone(true, focus)

            if (lastOptions.isEmpty()) {
                val searchRadius = zone.area.tileRadius
                lastOptions = find(searchRadius)
                freshSearch = true

                bot.log("Found ${lastOptions.size} target option(s) in $zone using search radius $searchRadius.")
            }
            val dexterity = bot.personality.dexterity
            val sortChance = dexterity * 0.75
            val sortByDistance = rand(sortChance)
            bot.log(
                "Target option ordering roll: dexterity=$dexterity, sortChance=$sortChance, rollPassed=$sortByDistance, options=${lastOptions.size}"
            )
            if (sortByDistance && lastOptions.size > 1) {
                lastOptions = lastOptions.toSortedSet(LocatableDistanceComparator(bot))
                bot.log("Sorted ${lastOptions.size} target option(s) by distance due to dexterity.")
            } else if (lastOptions is MutableList<E>) {
                (lastOptions as MutableList<E>).shuffle()
                bot.log("Shuffled ${lastOptions.size} target option(s); dexterity distance-sort did not trigger.")
            }

            val option = interactionOption()
            val iterator = lastOptions.iterator()

            while (iterator.hasNext()) {
                val target = iterator.next()
                iterator.remove()

                if (target.state != EntityState.ACTIVE) {
                    continue
                }

                if (!handler.interactions.interact(option, target)) {
                    bot.log("Failed to interact with target ${describeTarget(target)} using option $option.")
                    continue
                }

                focus = target
                bot.log("Selected new focus target ${describeTarget(target)}.")
                return true
            }

            if (freshSearch) {
                val visiblePlayers =
                    world.locator.findPlayers(zone.area.centerPosition, zone.area.tileRadius) { true }.size
                val shouldLeaveCrowdedZone =
                    rand(bot.personality.intelligence) &&
                            zones.isNotEmpty() &&
                            visiblePlayers > 20

                if (bot !in zone.area && handler.travelTo(zone)) {
                    bot.log("No interactable targets found. Moving closer to active zone.")
                    return true
                }
                if (shouldLeaveCrowdedZone) {
                    bot.log("Abandoning $zone because no target was interactable and the area is crowded. Viewable players: $visiblePlayers.")
                    return false
                }

                bot.log("No interactable targets found in $zone, but staying because no better zone decision was made.")


                return true
            }
        }
        onExecuteInZone(false, focus)
        return true
    }

    /**
     * Clears cached targeting state when this script is paused.
     *
     * This prevents the bot from resuming with stale entity references or cached options from an old zone.
     */
    final override fun onPaused() {
        clearTargetCache("script paused")
    }

    /**
     * Clears cached targeting state when this script requires banking.
     *
     * This prevents the bot from resuming with stale entity references or cached options.
     */
    final override suspend fun onBankRequired() {
        clearTargetCache("banking required")
        onTargetingBankRequired()
    }

    /**
     * Invoked before target searching. Returning `true` forces this script to look for another target.
     *
     * @return `true` if this script should be forced to look for another target.
     */
    open suspend fun refocus(): Boolean {
        return false
    }

    /**
     * Runs after targeting state has been cleared for an upcoming banking cycle.
     *
     * Subclasses can override this to perform additional cleanup before banking begins, such as clearing script-specific
     * targets, resetting local action state, cancelling temporary behaviour, or preparing any state needed before the bot
     * travels to a bank.
     */
    open suspend fun onTargetingBankRequired() {

    }

    /**
     * Finds target entities that this bot can attempt to interact with.
     *
     * Implementations should return mutable results because this base script removes options as they are attempted.
     * Returned targets may be unsorted; dextrous bots may sort them by distance before attempting interaction.
     *
     * @param searchRadius The tile radius to search within, usually based on the active zone size.
     *
     * @return A mutable collection of candidate target entities.
     */
    abstract fun find(searchRadius: Int): MutableCollection<E>

    /**
     * The interaction option used when attempting to interact with a target entity.
     *
     * Most first-option actions use `1`, such as chop, mine, fish, attack, steal, or pick depending on the target type.
     *
     * @return The interaction option index to send.
     */
    open fun interactionOption(): Int = 1

    /**
     * Called during each targeting cycle.
     *
     * Subclasses can use this to update state, emit speech, adjust behaviour, or perform script-specific logic while
     * either searching for a target or continuing an existing focused action.
     *
     * @param searching `true` when the script is searching for a new target, or `false` when it is continuing with the
     * @param focus The current mob being focused.
     * current focus.
     */
    open suspend fun onExecuteInZone(searching: Boolean, focus: E?) {

    }

    /**
     * Returns the reason the current focus cannot be reused.
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

            !bot.isViewableFrom(currentFocus) ->
                "the current focus is no longer viewable: ${describeTarget(currentFocus)}"

            bot.actions.size() == 0 ->
                "the bot has no active action for the current focus: ${describeTarget(currentFocus)}"

            else -> null
        }
    }

    /**
     * Clears the current focus, cached options, and cached zone.
     *
     * @param reason The reason cached targeting state is being reset.
     */
    private fun clearTargetCache(reason: String) {
        focus = null
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