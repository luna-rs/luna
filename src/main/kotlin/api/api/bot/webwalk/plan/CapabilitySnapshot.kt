package api.bot.webwalk.plan

import api.bot.webwalk.model.Requirements

/**
 * What a bot can do at the time that a trip is planned, copied from the bot so that planning can run on any thread.
 *
 * The things that edges and teleports ask for are all checked against it by [meets].
 *
 * @property skillLevels The level of each skill that the bot has, keyed by skill id. A skill that is missing is level 1.
 * @property items The amount of each item that the bot carries, in its inventory and equipment but not in its bank, keyed
 * by item id.
 * @property flags The unlocks that the bot has, such as [FAIRY_RINGS].
 * @property wildernessLevel The wilderness level where the bot is, or `0`.
 * @property usableTeleports The ids of the teleports (see `teleports.jsonc`) that the bot can use right now, because it has
 * the level, the runes or charges, and nothing is stopping it.
 *
 * @author Hydrozoa
 */
class CapabilitySnapshot(val skillLevels: Map<Int, Int> = emptyMap(),
                         val items: Map<Int, Int> = emptyMap(),
                         val flags: Set<String> = emptySet(),
                         val wildernessLevel: Int = 0,
                         val usableTeleports: Set<String> = emptySet()) {

    companion object {

        /**
         * The id of coins.
         */
        const val COINS = 995

        /**
         * The unlock that is needed to travel by fairy ring.
         */
        const val FAIRY_RINGS = "fairy_rings"
    }

    /**
     * The coins that the bot carries.
     */
    val coins: Int
        get() = items[COINS] ?: 0

    /**
     * Determines if the bot meets requirements.
     *
     * @param requirements What is needed.
     * @param wildernessAt The wilderness level where the requirements are used, which is where the bot is by default.
     * @return `true` if all of it is met.
     */
    fun meets(requirements: Requirements, wildernessAt: Int = wildernessLevel): Boolean {
        if (requirements.isEmpty) {
            return true
        }
        for ((skill, level) in requirements.skills) {
            if ((skillLevels[skill] ?: 1) < level) {
                return false
            }
        }
        for (item in requirements.items) {
            if ((items[item.id] ?: 0) < item.amount) {
                return false
            }
        }
        if (coins < requirements.coins || !flags.containsAll(requirements.flags)) {
            return false
        }
        val maxWilderness = requirements.maxWilderness
        return maxWilderness == null || wildernessAt <= maxWilderness
    }
}
