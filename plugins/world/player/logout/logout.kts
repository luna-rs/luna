import api.predef.*
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.model.mob.Player

/**
 * Perform any pre-disconnection operations.
 */
fun onLogout(plr: Player) {
    plr.closeInterfaces()
}

/**
 * Listen for player logout.
 */
on(LogoutEvent::class) { onLogout(it.plr) }

/**
 * Disconnect player if the logout button is clicked.
 */
button(2458) { it.plr.logout() }