package game.content.mosLeHarmless

import api.predef.*

for (id in listOf(BillTeach.IN_PUB, BillTeach.ON_ISLAND)) npc1(id) { BillTeach.ashore(plr, targetNpc) }
for (id in listOf(BillTeach.AT_PHASMATYS, BillTeach.AT_MOS_LE_HARMLESS)) npc1(id) { BillTeach.aboard(plr, targetNpc) }

// Bamboo ladders, then the ships' ladders: deck up, hold down, hold up, upper deck down.
for (id in listOf(4772, 11289, 11308)) object1(id) { BillTeach.climb(plr, gameObject, true) }
for (id in listOf(4778, 11309, 11290)) object1(id) { BillTeach.climb(plr, gameObject, false) }
