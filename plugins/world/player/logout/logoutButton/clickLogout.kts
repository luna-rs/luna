package world.player.logout.clickLogout

import api.predef.*
import io.luna.game.model.Music

/**
 * Disconnect player if the logout button is clicked.
 */
button(2458) {
    Music.stopMusic(plr)
    plr.logout()
}