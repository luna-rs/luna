package api.bot.webwalk.plan

/**
 * What taking a step of a trip uses up, such as the runes of a teleport spell or a charge of teleport jewellery. The planner
 * counts it against what a bot has, so that a trip never spends more than the bot carries.
 *
 * @property items The amount of each item that is used up, keyed by item id. Coins are items too.
 * @property charges The charges of teleport jewellery that are used up, keyed by the name of the jewellery (see
 * [game.item.degradable.jewellery.TeleportJewellery]).
 *
 * @author Hydrozoa
 */
data class Spend(val items: Map<Int, Int> = emptyMap(), val charges: Map<String, Int> = emptyMap()) {

    companion object {

        /**
         * Nothing is used up.
         */
        val NONE = Spend()
    }

    /**
     * @return `true` if nothing is used up.
     */
    val isEmpty: Boolean
        get() = items.isEmpty() && charges.isEmpty()
}
