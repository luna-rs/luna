package world.player.login

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * If it's a player's first login.
 */
var Player.firstLogin by Attr.boolean(true).persist("first_login")