package engine.player

import api.predef.*
import game.minigame.partyRoom.dropParty.DropPartyOption.depositItems
import io.luna.Luna
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener
import io.luna.net.msg.out.SkillUpdateMessageWriter
import io.luna.net.msg.out.UpdatePrivacyOptionMessageWriter
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter
import engine.player.punishment.PunishmentHandler
import io.luna.game.event.EventPriority

/**
 * Final initialization of the player before gameplay.
 */
on(LoginEvent::class, EventPriority.HIGH) {
    plr.tabs.resetAll()

    plr.contextMenu.show(OPTION_FOLLOW)
    plr.contextMenu.show(OPTION_TRADE)

    plr.equipment.loadBonuses()
    plr.depositItems.setListeners(PlayerRefreshListener(plr, "You can only deposit 8 items at a time."))

    plr.inventory.updatePrimaryWidget(plr)
    plr.equipment.updatePrimaryWidget(plr)

    plr.queue(UpdateRunEnergyMessageWriter())
    plr.queue(UpdatePrivacyOptionMessageWriter())

    plr.skills.forEach { plr.queue(SkillUpdateMessageWriter(it.id)) }
    plr.sendMessage("Welcome to Luna.")
    PunishmentHandler.notifyIfMuted(plr)
    if (Luna.settings().game().betaMode()) {
        plr.sendMessage("Server currently running in ${Luna.settings().game().runtimeMode()} mode.")
    }
}
