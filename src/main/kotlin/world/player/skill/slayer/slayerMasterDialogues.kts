package world.player.skill.slayer

import api.predef.npc1
import api.predef.npc4

// Set dialogues and shops for all slayer masters.
for (master in SlayerMaster.VALUES) {
    npc1(master.id) {
        Slayer.openDialogue(plr, master)
    }
    npc4(master.id) {
        Slayer.openShop(plr)
    }
}