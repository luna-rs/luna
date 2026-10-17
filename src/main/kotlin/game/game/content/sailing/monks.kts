package game.content.sailing

import api.predef.*
import game.content.sailing.Sailing.ENTRANA_MONKS
import game.content.sailing.Sailing.PORT_SARIM_MONKS
import game.content.sailing.Sailing.READY_TO_GO
import game.content.sailing.Sailing.READY_TO_LEAVE
import game.content.sailing.Sailing.sail
import io.luna.game.model.mob.Player

/**
 * Searches [plr], then sails them to Entrana if they carry no weapons or armour.
 */
fun search(plr: Player, npc: Int) {
    val route = ShipRoute.PORT_SARIM_TO_ENTRANA
    if (route.search.turnsAway(plr)) {
        plr.newDialogue()
            .npc(npc, "NO WEAPONS OR ARMOUR are permitted on holy",
                 "Entrana AT ALL. We will not allow you to travel there",
                 "in breach of mighty Saradomin's edict.")
            .npc(npc, "Do not try and deceive us again. Come back when you",
                 "have laid down your Zamorakian instruments of death.")
            .open()
    } else {
        plr.newDialogue().npc(npc, "All is satisfactory. You may board the boat now.").then {
            sail(it, route, "After a quick search, the monk smiles at you and allows you to board.")
        }.open()
    }
}

for (id in PORT_SARIM_MONKS) {
    npc1(id) {
        val npc = targetNpc.id
        plr.newDialogue()
            .npc(npc, "Do you seek passage to holy Entrana? If so, you must",
                 "leave your weaponry and armour behind. This is",
                 "Saradomin's will.")
            .options("No, not right now.", {
                it.newDialogue().player("No, not right now.").npc(npc, "Very well.").open()
            }, READY_TO_GO, {
                it.newDialogue()
                    .player(READY_TO_GO)
                    .npc(npc, "Very well. One moment please.")
                    .text("The monk quickly searches you.")
                    .then { search(it, npc) }
                    .open()
            }).open()
    }
}

for (id in ENTRANA_MONKS) {
    npc1(id) {
        val npc = targetNpc.id
        plr.newDialogue()
            .npc(npc, "Do you wish to leave holy Entrana?")
            .options(READY_TO_LEAVE, {
                it.newDialogue().player(READY_TO_LEAVE).npc(npc, "Okay, let's board...").then {
                    sail(it, ShipRoute.ENTRANA_TO_PORT_SARIM, "The ship takes you to Port Sarim.")
                }.open()
            }, "Not just yet.", {
                it.newDialogue().player("Not just yet.").open()
            }).open()
    }
}
