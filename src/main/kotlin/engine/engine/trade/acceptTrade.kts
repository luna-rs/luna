package engine.trade

import api.predef.*
import api.predef.ext.*

/**
 * Accept on offer interface.
 */
button(3420) { plr.interfaces.get(OfferTradeInterface::class)?.accept(plr) }

/**
 * Accept on confirm interface.
 */
button(3546) { plr.interfaces.get(ConfirmTradeInterface::class)?.accept(plr) }