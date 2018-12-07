import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.ConfigMessageWriter

/**
 * An ext. function to shortcut to [ConfigMessageWriter] (with a boolean value).
 */
fun Player.sendConfig(id: Int, state: Boolean) {
    sendConfig(id, if (state) 1 else 0)
}

/**
 * The run button config id.
 */
val runButton = 173

/**
 * Sends all config messages on login.
 */
on(LoginEvent::class) {
    plr.sendConfig(runButton, plr.walking.isRunning)
}