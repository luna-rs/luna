package game.content.randomEvents

import api.predef.*
import game.skill.woodcutting.cutTree.Axe

for (npc in WoodcuttingRandomEvents.TREE_ENTS.values.distinct()) {
    npc1(npc, false) { WoodcuttingRandomEvents.chopEnt(plr, targetNpc) }
    npc3(npc, false) { WoodcuttingRandomEvents.chopEnt(plr, targetNpc) }
    for (axe in Axe.entries) {
        useItem(axe.id).onNpc(npc) { WoodcuttingRandomEvents.chopEnt(plr, targetNpc) }
    }
}

for (parts in WoodcuttingRandomEvents.Parts.entries) {
    useItem(parts.head).onItem(WoodcuttingRandomEvents.HANDLE) {
        WoodcuttingRandomEvents.reattach(plr, parts.head)
    }
    useItem(parts.broken).onNpc(519) { WoodcuttingRandomEvents.offerRepair(plr, parts.broken) }
}
