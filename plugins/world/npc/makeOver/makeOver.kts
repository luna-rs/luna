package world.npc.makeOver

import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.mob.PlayerAppearance

/**
 * Dialogue for "Talk" option.
 */
npc1(599) {
    plr.newDialogue()
            .npc(npc.id, "Would you like to change your appearance?")
            .options(
                    "Yes", {plr.interfaces.open(PlayerAppearance.DesignPlayerInterface()) },
                    "No", {}).open()
}

/**
 * Spawn make-over mage NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(id = 599,
                 x = 3092,
                 y = 3250)
}