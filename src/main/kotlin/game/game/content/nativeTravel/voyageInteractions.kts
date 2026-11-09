package game.content.nativeTravel

import api.predef.*

for (id in NativeVoyages.Route.entries.map { it.npcId }.distinct()) {
    npc1(id) { NativeVoyages.talk(plr, targetNpc) }
}

for (id in NativeVoyages.Route.entries.filter { it.quickTravel }.map { it.npcId }.distinct()) {
    npc2(id) { NativeVoyages.travel(plr, targetNpc) }
}
