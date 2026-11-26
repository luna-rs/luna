package game.content.mosLeHarmless

import api.predef.*

for (id in listOf(BillTeach.IN_PUB, BillTeach.ON_ISLAND)) npc1(id) { BillTeach.ashore(plr, targetNpc) }
for (port in BillTeach.Port.entries) npc1(port.npcId) { BillTeach.aboard(plr, port) }

for (id in BillTeach.ladderDirections.keys) object1(id) { BillTeach.climb(plr, gameObject) }
