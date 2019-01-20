import api.predef.*
import io.luna.game.event.entity.player.LogoutEvent
import io.luna.game.model.mob.Player

/**
 * Perform any pre-disconnection operations.
 */
fun onLogout(plr: Player) {
    plr.interfaces.close()
}

/**
 * Listen for player logout.
 */
on(LogoutEvent::class) { onLogout(plr) }

/**
 * Disconnect player if the logout button is clicked.
 */
button(2458) { plr.logout() }