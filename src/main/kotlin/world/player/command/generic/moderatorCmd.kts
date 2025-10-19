package world.player.command.generic

import api.predef.*

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) {
        it.forceLogout()
        plr.sendMessage("You have kicked ${it.username}.")
    }
}
