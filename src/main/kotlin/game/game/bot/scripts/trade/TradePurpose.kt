package game.bot.scripts.trade

/**
 * Describes the high-level reason a bot is participating in a trade.
 *
 * The purpose controls how the trade script interprets expected items, validates the other side's offer, and decides
 * whether the bot is trying to buy, sell, bundle items, accept equivalent value, or look for merchanting deals.
 *
 * @author lare96
 */
enum class TradePurpose {

    /**
     * Buys individual items from another bot.
     *
     * The other bot is expected to offer one or more matching items, usually in exchange for coins.
     */
    BUY,

    /**
     * Buys a bundle of items using coins.
     *
     * The other bot is expected to offer the full bundle, while this bot pays the agreed coin value.
     */
    BUY_BUNDLE_FOR_COINS,

    /**
     * Buys a bundle of items using equivalent-value items instead of pure coins.
     *
     * This is useful for barter-style trades where the bot accepts a value match rather than an exact coin payment.
     */
    BUY_BUNDLE_FOR_EQUIVALENT,

    // TODO BUY_GEARSET, SELL_GEARSET - Trade items as a specific equipment set.

    /**
     * Sells individual items to another bot.
     *
     * The bot offers matching items one by one and expects payment from the other side.
     */
    SELL,

    /**
     * Sells a bundle of items for coins.
     *
     * This can later support gear sets or other grouped item packages.
     */
    SELL_BUNDLE_FOR_COINS,

    /**
     * Sells a bundle of items for equivalent-value items.
     *
     * This supports barter-style selling where the bot accepts items worth roughly the expected trade value.
     */
    SELL_BUNDLE_FOR_EQUIVALENT,

    /**
     * Looks for undervalued items for merchanting.
     *
     * This is intended for merchant bots that buy opportunistically when the other side's offer appears underpriced.
     */
    DEALS
}