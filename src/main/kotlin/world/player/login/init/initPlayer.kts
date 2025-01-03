package world.player.login.init

import api.predef.*
import world.player.punishment.PunishmentHandler
import io.luna.Luna
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.SkillUpdateMessageWriter
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter
import world.minigame.partyRoom.dropParty.DropPartyOption.depositItems
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
        if (PunishmentHandler.isPermanent(plr.unmuteInstant)) {
            plr.sendMessage("You are permanently muted. You cannot appeal this.")
        } else {
            val lift = dateFormatter.format(plr.unmuteInstant)
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
    plr.depositItems.setListeners(PlayerRefreshListener(plr, "You can only deposit 8 items at a time."))

    plr.inventory.refreshPrimary(plr)
    plr.equipment.refreshPrimary(plr)

    plr.queue(UpdateRunEnergyMessageWriter())

    plr.skills.forEach { plr.queue(SkillUpdateMessageWriter(it.id)) }

    plr.sendMessage("Welcome to Luna.")
    if (Luna.settings().game().betaMode()) {
        plr.sendMessage("Server currently running in ${Luna.settings().game().runtimeMode()} mode.")
    }
    checkMute(plr)
}

/**
 * Listens for login events, initializes [Player]s.
 */
on(LoginEvent::class) { init(plr) }
