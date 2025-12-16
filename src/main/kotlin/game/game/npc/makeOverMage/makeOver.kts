package game.npc.makeOverMage

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.block.PlayerAppearance

/**
 * Dialogue for "Talk" option.
 */
npc1(599) {
    plr.newDialogue()
            .npc(targetNpc.id, "Would you like to change your appearance?")
            .options(
                "Yes", {plr.overlays.open(PlayerAppearance.DesignPlayerInterface()) },
                "No", { plr.overlays.closeWindows() }).open()
}

/**
 * Spawn make-over mage NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(id = 599,
                 x = 3092,
                 y = 3250)
}