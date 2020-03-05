package world.player.command.generic

import api.predef.*
import world.player.command.cmd
import world.player.command.getPlayer

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) {
        it.logout()
        plr.sendMessage("You have kicked ${it.username}.")
    }
}
