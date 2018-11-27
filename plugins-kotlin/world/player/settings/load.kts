import api.on
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.ConfigMessageWriter


fun Player.sendConfig(id: Int, state: Int) {
    queue(ConfigMessageWriter(id, state))
}

fun Player.sendConfig(id: Int, state: Boolean) {
    queue(ConfigMessageWriter(id, if (state) 1 else 0))
}

/* The run button id. */
private val RUN_BUTTON = 173

fun sendLoginConfig(plr: Player) {
    plr.sendConfig(RUN_BUTTON, plr.walkingQueue.isRunning)
}

/* Configure interface states. */
on(LoginEvent::class).run { sendLoginConfig(it.plr) }