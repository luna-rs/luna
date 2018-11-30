import api.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.ConfigMessageWriter

/**
 * An ext. function to shortcut to [ConfigMessageWriter].
 */
fun Player.sendConfig(id: Int, state: Int) {
    queue(ConfigMessageWriter(id, state))
}

/**
 * An ext. function to shortcut to [ConfigMessageWriter] (with a boolean value).
 */
fun Player.sendConfig(id: Int, state: Boolean) {
    queue(ConfigMessageWriter(id, if (state) 1 else 0))
}

/**
 * The run button config id.
 */
private val RUN_BUTTON = 173

/**
 * Sends all config messages on login.
 */
on(LoginEvent::class).run {
    val plr = it.plr

    plr.sendConfig(RUN_BUTTON, plr.walkingQueue.isRunning)
}