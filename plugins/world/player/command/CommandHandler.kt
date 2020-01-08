package world.player.command

import api.event.Matcher
import api.predef.*
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights

/**
 * The [CommandEvent] matcher function.
 */
fun cmd(name: String, rights: PlayerRights, action: CommandEvent.() -> Unit) {
    val matcher = Matcher.get<CommandEvent, CommandKey>()
    matcher[CommandKey(name, rights)] = {
        if (plr.rights >= rights) {
            action(this)
        }
    }
}

/**
 * Performs a lookup for a player based on the arguments from [index] onwards. By default, it starts from index 0.
 */
fun getPlayer(msg: CommandEvent, index: Int = 0, action: (Player) -> Unit) =
    world.getPlayer(msg.getInputFrom(index)).ifPresent(action)