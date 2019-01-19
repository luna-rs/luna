
import api.predef.*
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.net.LunaChannelFilter
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The date formatter.
 */
val df = DateTimeFormatter.ofPattern("MMMM d, uuuu")!!

/**
 * The blacklist file.
 */
val blacklistFile = File("./data/punishment/blacklist.txt")

/**
 * Performs a lookup for the person we're punishing.
 */
fun getPlayer(msg: CommandEvent, action: (Player) -> Unit) =
    world.getPlayer(msg.args[0]).ifPresent(action)

/**
 * Construct a string with punishment lift date ~ [yyyy-mm-dd].
 */
fun punishDuration(msg: CommandEvent): LocalDate {
    val args = msg.args
    val years = if (args.size == 4) args[3].toLong() else 0
    val months = if (args.size == 3) args[2].toLong() else 0
    val days = args[1].toLong()

    return LocalDate.now()
        .plusYears(years)
        .plusMonths(months)
        .plusDays(days)
}

/**
 * Perform an IP ban on a player.
 */
cmd("ip_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        async {
            val client = it.client
            FileWriter(blacklistFile, true).use { fw -> fw.write(client.ipAddress) }

            val channelFilter = client.channel.attr(LunaChannelFilter.KEY).get()
            channelFilter.blacklist.add(client.ipAddress)
        }
        it.logout()
        plr.sendMessage("You have IP banned ${it.username}.")
    }
}

/**
 * Perform a permanent ban on a player.
 */
cmd("perm_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        it.unbanDate = "never"
        it.logout()
        plr.sendMessage("You have permanently banned ${it.username}.")
    }
}

/**
 * Perform a permanent mute on a player.
 */
cmd("perm_mute", RIGHTS_MOD) {
    getPlayer(this) {
        it.unmuteDate = "never"
        it.logout()
        plr.sendMessage("You have permanently muted ${it.username}.")
    }
}

/**
 * Perform a temporary ban on a player.
 */
cmd("ban", RIGHTS_MOD) {
    getPlayer(this) {
        val duration = punishDuration(this)
        it.unbanDate = duration.toString()
        it.logout()
        plr.sendMessage("You have banned ${it.username} until ${df.format(duration)}.")
    }
}

/**
 * Perform a temporary mute on a player.
 */
cmd("mute", RIGHTS_MOD) {
    getPlayer(this) {
        val duration = punishDuration(this)
        it.unmuteDate = duration.toString()
        it.logout()
        plr.sendMessage("You have muted ${it.username} until ${df.format(duration)}.")
    }
}

/**
 * Perform a forced disconnect on a player.
 */
cmd("kick", RIGHTS_MOD) {
    getPlayer(this) {
        it.logout()
        plr.sendMessage("You have kicked ${it.username}.")
    }
}
