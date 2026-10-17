package api.bot.webwalk.plan

import game.content.sailing.ShipSearch
import io.luna.game.model.mob.bot.Bot

/**
 * Conditions that a bot meets or not, as flags that requirements can ask for. The
 * data names a flag, and the rule that decides it stays in the game's code.
 *
 * There is a flag for each [ShipSearch] that searches for something, which a bot has if the search finds nothing on it,
 * such as [shipSearchFlag] of [ShipSearch.ENTRANA] for a bot without weapons or armour.
 *
 * Can also be used to check if spirit trees are unlocked, or if Zanaris is unlocked, and if a dramen or lunar staff is available in the inventory.
 *
 * @author Hydrozoa
 */
object BotConditions {

    /**
     * The start of the flags of [ShipSearch]es.
     */
    const val SHIP_SEARCH_PREFIX = "ship_search_"

    /**
     * The conditions, keyed by the name of their flag.
     */
    private val conditions: Map<String, (Bot) -> Boolean> =
        ShipSearch.entries.mapNotNull { search ->
            shipSearchFlag(search)?.let { flag -> flag to { bot: Bot -> !search.turnsAway(bot) } }
        }.toMap()

    /**
     * The names of every condition flag.
     */
    val flags: Set<String>
        get() = conditions.keys

    /**
     * The flag that a bot has when [search] finds nothing on it, or `null` for [ShipSearch.NONE], which needs no flag.
     */
    fun shipSearchFlag(search: ShipSearch): String? =
        if (search == ShipSearch.NONE) null else SHIP_SEARCH_PREFIX + search.name.lowercase()

    /**
     * Finds the flags of the conditions that a bot meets. Must be called on the game thread, because it reads the bot as it
     * is.
     *
     * @param bot The bot.
     * @return The flags.
     */
    fun met(bot: Bot): Set<String> = conditions.filterValues { it(bot) }.keys
}
