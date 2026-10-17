package api.bot.action.trading

import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Represents an item involved in a bot trade, including its price, trading purpose, and owning bot.
 *
 * @property item The item being traded.
 * @property price The price assigned to the item.
 * @property purpose The reason the item is being traded.
 * @property bot The bot associated with the trade item.
 * @author lare96
 */
class BotTradeItem(val item: Item, val price: Int, val purpose: BotTradePurpose, val bot: Bot)