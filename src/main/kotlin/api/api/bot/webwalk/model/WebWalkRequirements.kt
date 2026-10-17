package api.bot.webwalk.model

/**
 * An item (and how many of it) that a bot must carry, in its inventory or equipment, to use an edge.
 *
 * @property id The item id.
 * @property amount The amount needed.
 *
 * @author Hydrozoa
 */
data class ItemRequirement(val id: Int, val amount: Int = 1)

/**
 * What a bot needs to use an edge or teleport of the web-walker graph.
 *
 * Requirements are declarative data. They are evaluated against a snapshot of the bot, never against the live bot, so
 * that planning can run off the game thread.
 *
 * @property skills The minimum skill levels, keyed by skill id (see [io.luna.game.model.mob.Skill]).
 * @property items The items that must be carried (inventory or equipment, never the bank).
 * @property coins The coins that must be carried.
 * @property flags Names of unlocks the bot must have, such as `fairy_rings`.
 * @property maxWilderness The deepest wilderness level the bot may be at to use this, or `null` for no limit.
 *
 * @author Hydrozoa
 */
data class Requirements(val skills: Map<Int, Int> = emptyMap(),
                        val items: List<ItemRequirement> = emptyList(),
                        val coins: Int = 0,
                        val flags: Set<String> = emptySet(),
                        val maxWilderness: Int? = null) {

    companion object {

        /**
         * Requirements that every bot meets.
         */
        val NONE = Requirements()
    }

    /**
     * @return `true` if nothing is required.
     */
    val isEmpty: Boolean
        get() = this == NONE
}
