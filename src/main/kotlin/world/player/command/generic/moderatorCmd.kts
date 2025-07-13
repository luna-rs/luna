package world.player.command.generic

import api.predef.RIGHTS_MOD
import api.predef.cmd
import api.predef.getPlayer

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) {
        it.logout()
        plr.sendMessage("You have kicked ${it.username}.")
    }
}
