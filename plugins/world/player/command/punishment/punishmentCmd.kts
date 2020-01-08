package world.player.command.punishment

import api.predef.*
import api.punishment.PunishmentHandler
import io.luna.game.event.impl.CommandEvent
import world.player.command.cmd
import world.player.command.getPlayer
import java.time.LocalDateTime

/**
 * Construct a string with punishment lift date ~ [yyyy-mm-dd].
 */
fun punishDuration(msg: CommandEvent): LocalDateTime {
    val args = msg.args
    val days = if (args.size == 4) args[3].toLong() else 0
    val hours = if (args.size == 3) args[2].toLong() else 0
    val minutes = args[1].toLong()

    return LocalDateTime.now()
        .plusMinutes(minutes)
        .plusHours(hours)
        .plusDays(days)
}

/**
 * Perform an IP ban on a player.
 */
cmd("ip_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        PunishmentHandler.ipBan(it)
        plr.sendMessage("You have IP banned ${it.username}.")
    }
}

/**
 * Perform a permanent ban on a player.
 */
cmd("perm_ban", RIGHTS_ADMIN) {
    getPlayer(this) {
        PunishmentHandler.permBan(it)
        plr.sendMessage("You have permanently banned ${it.username}.")
    }
}

/**
 * Perform a permanent mute on a player.
 */
cmd("perm_mute", RIGHTS_MOD) {
    getPlayer(this) {
        PunishmentHandler.permMute(it)
        plr.sendMessage("You have permanently muted ${it.username}.")
    }
}

/**
 * Perform a temporary ban on a player.
 */
cmd("ban", RIGHTS_MOD) {
    getPlayer(this) {
        val duration = punishDuration(this)
        PunishmentHandler.ban(it, duration)
        plr.sendMessage("You have banned ${it.username} until ${PunishmentHandler.FORMATTER.format(duration)}.")
    }
}

/**
 * Perform a temporary mute on a player.
 */
cmd("mute", RIGHTS_MOD) {
    getPlayer(this) {
        val duration = punishDuration(this)
        PunishmentHandler.mute(it, duration)
        plr.sendMessage("You have muted ${it.username} until ${PunishmentHandler.FORMATTER.format(duration)}.")
    }
}