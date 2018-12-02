import api.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LogoutEvent
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
on(LogoutEvent::class).run { onLogout(it.plr) }

/**
 * Disconnect player if the logout button is clicked.
 */
on(ButtonClickEvent::class)
    .args(2458)
    .run { it.plr.logout() }