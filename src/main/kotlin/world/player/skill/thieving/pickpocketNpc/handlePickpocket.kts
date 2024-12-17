package world.player.skill.thieving.pickpocketNpc

import api.predef.*
import io.luna.game.model.def.NpcDefinition

/* Add NPC interactions for all NPCs able to be stolen from. */
for (def in NpcDefinition.ALL) {
    if (def != null && def.actions.contains("Pickpocket")) {
        val thievable = ThievableNpc.NAME_TO_NPC[def.name]
        if (thievable != null) {
            npc2(def.id) {
                plr.submitAction(PickpocketAction(plr, targetNpc, thievable))
            }
        }
    }
}