package game.bot.scripts.trade

/**
 * Holds the active trade intent for a bot.
 *
 * A trade state describes what the bot is trying to accomplish, which items are involved, and whether a matching trade
 * partner or offer has already been found.
 *
 * @property purpose The reason this trade state exists, such as buying, selling, or bartering.
 * @property items The mutable set of items this bot wants to trade.
 */
class TradeState(
    val purpose: TradePurpose,
    val items: MutableSet<TradeItem> = mutableSetOf()
) {

    /**
     * The current matching trade offer or trade partner.
     *
     * This remains `null` until the bot finds a valid match for this trade state.
     */
    var match: TradeMatch? = null

    /**
     * Checks whether this trade state already has a matched offer or partner.
     *
     * @return `true` if [match] is not `null`.
     */
    fun hasMatched(): Boolean =
        match != null
}