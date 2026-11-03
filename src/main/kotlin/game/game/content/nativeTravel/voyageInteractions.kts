package game.content.nativeTravel

import api.predef.*

for (id in NativeVoyages.Route.entries.map { it.npcId }.distinct()) {
    npc1(id) { NativeVoyages.talk(plr, targetNpc) }
    npc2(id) { NativeVoyages.travel(plr, targetNpc) }
}
