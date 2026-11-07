package engine.player.punishment

import api.predef.*
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

/**
 * Determines the punish duration instant.
 */
fun punish(msg: CommandEvent, listener: (Instant) -> Unit) {
    fun openInput(plr: Player, unit: TemporalUnit) {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) {
                listener(Instant.now().plus(value.toLong(), unit))
                plr.overlays.closeWindows()
            }
        })
    }

    val plr = msg.plr
    plr.newDialogue().options("Days", { openInput(plr, ChronoUnit.DAYS) },
                              "Hours", { openInput(plr, ChronoUnit.HOURS) },
                              "Minutes", { openInput(plr, ChronoUnit.MINUTES) }).open()
}

cmd("ip_ban", RIGHTS_ADMIN) {
    val username = usernameFrom(this)
    PunishmentHandler.ipBan(plr, username)
}

cmd("perm_ban", RIGHTS_ADMIN) {
    val username = usernameFrom(this)
    PunishmentHandler.permBan(plr, username)
}

cmd("perm_mute", RIGHTS_MOD) {
    val username = usernameFrom(this)
    PunishmentHandler.permMute(plr, username)
}

cmd("ban", RIGHTS_MOD) {
    val username = usernameFrom(this)
    punish(this) { PunishmentHandler.ban(plr, username, it) }
}

cmd("mute", RIGHTS_MOD) {
    val username = usernameFrom(this)
    punish(this) { PunishmentHandler.mute(plr, username, it) }
}

cmd("unmute", RIGHTS_MOD) {
    val username = usernameFrom(this)
    PunishmentHandler.unmute(plr, username)
}

cmd("unban", RIGHTS_MOD) {
    val username = usernameFrom(this)
    PunishmentHandler.unban(plr, username)
}
