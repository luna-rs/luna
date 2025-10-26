package engine.trade

import api.attr.Attr
import io.luna.game.model.mob.Player

/**
 * The current trading partner.
 */
var Player.tradingWith by Attr.int(-1)
