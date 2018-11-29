import api.*
import com.google.common.io.Files
import io.luna.game.event.impl.CommandEvent
import java.io.File
import java.time.LocalDate

/**
 * Performs a lookup for the person we're punishing.
 */
fun findPunish(msg: CommandEvent) = world.getPlayer(msg.args[0])

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
on(CommandEvent::class)
    .args("ip_ban", RIGHTS_ADMIN)
    .run {
        val plr = it.plr
        findPunish(it).ifPresent {
            async {
                val writeString = System.lineSeparator() + plr.address()
                Files.write(writeString.toByteArray(), File("./data/players/blacklist.txt"))
            }
            plr.logout()
        }
    }

/**
 * Perform a permanent ban on a player.
 */
on(CommandEvent::class)
    .args("perm_ban", RIGHTS_ADMIN)
    .run {
        val plr = it.plr
        findPunish(it).ifPresent {
            plr.attr("unban_date", "never")
            plr.logout()
        }
    }

/**
 * Perform a permanent mute on a player.
 */
on(CommandEvent::class)
    .args("perm_mute", RIGHTS_MOD)
    .run {
        val plr = it.plr
        findPunish(it).ifPresent {
            plr.attr("unmute_date", "never")
            plr.logout()
        }
    }

/**
 * Perform a temporary ban on a player.
 */
on(CommandEvent::class)
    .args("ban", RIGHTS_MOD)
    .run { msg ->
        val plr = msg.plr
        findPunish(msg).ifPresent {
            plr.attr("unban_date", punishDuration(msg))
            plr.logout()
        }
    }

/**
 * Perform a temporary mute on a player.
 */
on(CommandEvent::class)
    .args("mute", RIGHTS_MOD)
    .run { msg ->
        val plr = msg.plr
        findPunish(msg).ifPresent {
            plr.attr("unmute_date", punishDuration(msg))
            plr.logout()
        }
    }

/**
 * Perform a forced disconnect on a player.
 */
on(CommandEvent::class)
    .args("kick", RIGHTS_MOD)
    .run { msg ->
        findPunish(msg).ifPresent { it.logout() }
    }
