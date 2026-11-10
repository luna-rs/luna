package api.bot.action.trading

/**
 * One purchase or sale described in a player's public advertisement, before a response is chosen.
 *
 * Item ids are unnoted commodities supplied by the name resolver. An empty or grouped result requires a broad buyer
 * to inspect the actual trade; it never selects an arbitrary variant. Missing quantity means unspecified stock.
 *
 * @author lare96
 */
data class PlayerTradeAdvertisement(val itemName: String,
                                    val itemIds: Set<Int>,
                                    val quantity: Int?,
                                    val price: Price?,
                                    val direction: Direction = Direction.SELL) {

    /**
     * The player's side of the advertised exchange.
     *
     * @author lare96
     */
    enum class Direction {
        BUY,
        SELL
    }

    /**
     * How the advertised coins relate to the goods. Unclear wording must not become an implicit unit price.
     *
     * @author lare96
     */
    enum class PriceBasis {
        EACH,
        TOTAL,
        UNCLEAR
    }

    /**
     * An exact whole-coin quote. Total prices retain their original quantity rather than rounding a unit price.
     *
     * @author lare96
     */
    data class Price(val coins: Long, val basis: PriceBasis) {
        init {
            require(coins > 0)
        }
    }

    init {
        require(itemName.isNotBlank())
        require(quantity == null || quantity > 0)
    }

    /**
     * Whether ordinary buyers must leave this offer to merchants or curious marketplace visitors.
     */
    val requiresInspection: Boolean
        get() = itemIds.size != 1 || price?.basis == PriceBasis.UNCLEAR ||
                price?.basis == PriceBasis.TOTAL && quantity == null
}
