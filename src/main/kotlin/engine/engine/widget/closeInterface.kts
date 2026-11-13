package engine.widget

import api.predef.*
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.CloseInterfaceEvent

/**
 * Closes the currently open interface.
 */
on(CloseInterfaceEvent::class, EventPriority.HIGH) {
    try {
        val offer = plr.overlays[OfferTradeInterface::class]
            ?: plr.overlays[ConfirmTradeInterface::class]?.offer
        offer?.notifyDeclinedBy(plr)
    } finally {
        plr.overlays.closeWindows()
    }
}
