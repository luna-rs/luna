package game.content.questRewards

import api.predef.*
import game.content.questRewards.FamilyCrest.Son

npc1(FamilyCrest.DIMINTHEIS) { FamilyCrest.talkToDimintheis(plr) }

for (son in Son.values()) {
    npc1(son.npc) { FamilyCrest.talkToSon(plr, son) }
}
