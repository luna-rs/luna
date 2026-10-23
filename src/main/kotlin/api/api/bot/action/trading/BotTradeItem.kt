package api.bot.action.trading

import com.google.common.math.LongMath
import com.google.common.primitives.Ints
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Represents an item offered or requested during a bot trade.
 *
 * Stores the item, its assigned unit price, trade purpose, and associated bot.
 * The total trade value is calculated from the item's quantity and unit price.
 *
 * @property item The item being traded, including its quantity.
 * @property price The assigned price per unit, in coins.
 * @property purpose The purpose of the trade item.
 * @property bot The bot associated with the trade item.
 * @author lare96
 */
class BotTradeItem(val item: Item, val price: Long, val purpose: BotTradePurpose, val bot: Bot) {

    /**
     * Calculates the total trade value of this item.
     *
     * Multiplies the item's quantity by its unit price using saturated arithmetic to prevent overflow.
     * The resulting value is clamped to the 32-bit integer range.
     *
     * @return The total trade value in coins, saturated to the integer limits when necessary.
     */
    fun totalPrice(): Int = Ints.saturatedCast(LongMath.saturatedMultiply(item.amount.toLong(), price))
}
