import api.predef.*
import io.luna.game.event.impl.LoginEvent

/**
 * Configure and show saved settings.
 */
on(LoginEvent::class) {
    plr.settings.setPlayer(plr)
    plr.settings.showAll()
}