package api.bot.action.trading

/**
 * Describes why a bot is participating in a trade.
 *
 * The purpose tells the trade script how to evaluate the trade: whether the bot expects a specific item, wants coins or
 * equivalent value, is bundling items, or is trying to merchant for profit.
 *
 * @author lare96
 */
enum class BotTradePurpose {

    // TODO DONATE/GIFT and SWAP entries?

    /**
     * The bot is buying a specific item.
     *
     * The bot expects the other player to offer the desired item and is willing to pay with coins, items, or both.
     */
    BUY,

    /**
     * The bot is selling a specific item.
     *
     * The bot expects the other player to offer coins, items of equivalent value, or both.
     */
    SELL,

    /**
     * The bot is buying items that appear undervalued.
     *
     * This is used for merchanting behavior where the bot is looking for profitable buy opportunities instead of a
     * normal item requirement.
     */
    MERCHANT_BUY,

    /**
     * The bot is selling items for profit.
     *
     * This is used for merchanting behavior where the bot is trying to sell acquired items at a favorable price.
     */
    MERCHANT_SELL
}