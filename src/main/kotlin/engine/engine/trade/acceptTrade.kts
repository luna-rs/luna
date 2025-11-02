package engine.trade

import api.predef.*
import api.predef.ext.*

/**
 * Accept on offer interface.
 */
button(3420) { plr.overlays[OfferTradeInterface::class]?.accept(plr) }

/**
 * Accept on confirm interface.
 */
button(3546) { plr.overlays [ConfirmTradeInterface::class]?.accept(plr) }