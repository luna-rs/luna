package game.content.lumbridgeGuide

import api.predef.*

// Both revision 377 appearances use the same advice, wherever the guide is placed.
for (npcId in listOf(2244, 3393)) {
    npc1(npcId) {
        LumbridgeGuideDialogue(plr, targetNpc.id).open()
    }
}
