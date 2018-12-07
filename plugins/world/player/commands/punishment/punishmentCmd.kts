import api.predef.*
import com.google.common.io.Files
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import java.io.File
import java.time.LocalDate

/**
 * Performs a lookup for the person we're punishing.
 */
fun getPlayer(msg: CommandEvent, action: (Player) -> Unit) =
    world.getPlayer(msg.name).ifPresent(action)

/**
 * Construct a string with punishment lift date ~ [yyyy-mm-dd].
 */
fun punishDuration(msg: CommandEvent): String {
    val args = msg.args
    val years = if (args.size == 4) args[3].toLong() else 0
    val months = if (args.size == 3) args[2].toLong() else 0
    val days = args[1].toLong()

    return LocalDate.now()
        .plusYears(years)
        .plusMonths(months)
        .plusDays(days)
        .toString()
}

/**
 * Perform an IP ban on a player.
 */
cmd("ip_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        async {
            val writeString = System.lineSeparator() + it.client.ipAddress
            Files.write(writeString.toByteArray(), File("./data/players/blacklist.txt"))
        }
        it.logout()
    }
}

/**
 * Perform a permanent ban on a player.
 */
cmd("perm_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        it.unbanDate = "never"
        it.logout()
    }
}

/**
 * Perform a permanent mute on a player.
 */
cmd("perm_mute", RIGHTS_MOD) {
    getPlayer(this) {
        it.unmuteDate = "never"
        it.logout()
    }
}

/**
 * Perform a temporary ban on a player.
 */
cmd("ban", RIGHTS_MOD) {
    getPlayer(this) {
        it.unbanDate = punishDuration(this)
        it.logout()
    }
}

/**
 * Perform a temporary mute on a player.
 */
cmd("mute", RIGHTS_MOD) {
    getPlayer(this) {
        it.unmuteDate = punishDuration(this)
        it.logout()
    }
}

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) { it.logout() }
}
