package api.bot.webwalk.plan

/**
 * How much of the bank of a bot a trip may count as if it were carried, so that the bot can withdraw it on the way (see
 * [BotCapabilities.snapshot] and [api.bot.webwalk.walk.WebWalker.planTrip]).
 *
 * Only what a trip spends is ever counted: coins, the runes of teleport spells, and teleport jewellery. The one other
 * item is a knife, for the webs on the way (see [BotConditions.SLASH_WEB]). Keys, staves and other items that an edge
 * may ask for have to be carried.
 *
 * @author Hydrozoa
 */
enum class BankAccess {

    /**
     * Nothing in the bank counts. The trip is planned with what the bot carries.
     */
    NONE,

    /**
     * The coins in the bank count, for fares and tolls. A withdrawal of coins takes at most one slot, so this still fits
     * when the runes of a teleport don't.
     */
    COINS,

    /**
     * The coins in the bank count, and so do the runes of teleport spells, the charges of teleport jewellery, and a
     * knife.
     */
    ALL
}
