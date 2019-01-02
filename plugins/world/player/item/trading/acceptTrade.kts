import api.predef.*
import world.player.item.trading.ConfirmTradeInterface
import world.player.item.trading.OfferTradeInterface

/**
 * Accept on offer interface.
 */
button(3420) { plr.interfaces.get(OfferTradeInterface::class)?.accept(plr) }

/**
 * Accept on confirm interface.
 */
button(3546) { plr.interfaces.get(ConfirmTradeInterface::class)?.accept(plr) }