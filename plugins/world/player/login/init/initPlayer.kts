package world.player.login.init

import api.predef.*
import api.punishment.PunishmentHandler
import io.luna.Luna
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.AssignmentMessageWriter
import io.luna.net.msg.out.SkillUpdateMessageWriter
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter
import java.time.format.DateTimeFormatter

/**
 * Formats dates into the specified pattern.
 */
val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, uuuu")!!

/**
 * If the player is muted, send the mute details.
 */
fun checkMute(plr: Player) {
    if (plr.isMuted) {
        if (PunishmentHandler.isPermanent(plr.unmuteDate)) {
            plr.sendMessage("You are permanently muted. You cannot appeal this.")
        } else {
            val lift = dateFormatter.format(plr.unmuteDate)
            plr.sendMessage("You are muted. You will be unmuted on $lift.")
        }
    }
}

/**
 * Final initialization of the player before gameplay.
 */
fun init(plr: Player) {
    plr.tabs.resetAll()

    plr.interactions.show(INTERACTION_FOLLOW)
    plr.interactions.show(INTERACTION_TRADE)

    plr.equipment.loadBonuses()
    plr.inventory.refreshPrimary(plr)
    plr.equipment.refreshPrimary(plr)

    plr.queue(UpdateRunEnergyMessageWriter())
    plr.queue(AssignmentMessageWriter(true))

    plr.skills.forEach { plr.queue(SkillUpdateMessageWriter(it.id)) }

    plr.sendMessage("Welcome to Luna.")
    if (Luna.settings().betaMode()) {
       plr.sendMessage("Server currently running in ${Luna.settings().runtimeMode()} mode.")
    }
    checkMute(plr)
}

/**
 * Listens for login events, initializes [Player]s.
 */
on(LoginEvent::class) { init(plr) }
