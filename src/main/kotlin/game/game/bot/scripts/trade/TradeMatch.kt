package game.bot.scripts.trade

import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.bot.Bot

/**
 * Represents a possible trade match between two bots.
 *
 * A trade match stores the local bot, the other bot, the other bot's trade state, and the items expected from the
 * trade. The match can then be checked against an actual trade container using a [TradeScrutiny] strategy.
 *
 * @property bot The bot evaluating or initiating the trade.
 * @property other The other bot involved in the trade.
 * @property otherState The other bot's current trade state.
 * @property expected The items expected from the other side of the trade.
 * @author lare96
 */
class TradeMatch(
    val bot: Bot,
    val other: Bot,
    val otherState: TradeState,
    val expected: List<Item>
) {

    /**
     * Whether this match has already been offered to the other bot.
     *
     * This can be used to prevent repeatedly sending the same trade offer for the same match.
     */
    var offered = false

    /**
     * Returns whether the supplied container matches this trade's expectations.
     *
     * The actual comparison is delegated to [scrutiny], allowing different trade scripts to use stricter or looser
     * validation rules.
     *
     * @param container The trade container to inspect.
     * @param scrutiny The scrutiny strategy used to compare the container against this match.
     * @return `true` if the container satisfies this match's expected trade contents.
     */
    fun isAsExpected(container: ItemContainer, scrutiny: TradeScrutiny): Boolean {
        return scrutiny.searchFunction(this, container)
    }
}