package game.player.command

import api.predef.*

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) {
        if (it.rights >= plr.rights) {
            plr.sendMessage("You can't kick ${it.username}.")
            return@getPlayer
        }
        it.forceLogout()
        plr.sendMessage("You have kicked ${it.username}.")
    }
}
