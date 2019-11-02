/**
 * All globally accessible attributes go in this file. Niche attributes can be put in the files they're related to.
 */
package api.predef

import api.attr.Attr
import io.luna.game.model.mob.Player

/**
 * The current trading partner.
 */
var Player.tradingWith by Attr.int(-1)

/**
 * If it's a player's first login.
 */
var Player.firstLogin by Attr.boolean(true).persist("first_login")

/**
 * The player's current wilderness level. Will be `0` if not in the wilderness.
 */
var Player.wildernessLevel by Attr.int()