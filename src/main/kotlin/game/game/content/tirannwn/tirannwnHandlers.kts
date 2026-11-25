package game.content.tirannwn

import api.predef.*
import game.content.tirannwn.CrystalElves.ILFEEN
import game.content.tirannwn.CrystalElves.ISLWYN
import game.content.tirannwn.CrystalElves.SEED

object1(LletyaTrees.TREE) { LletyaTrees.pass(plr) }

npc1(ISLWYN) { CrystalElves.talkToIslwyn(plr) }
npc1(ILFEEN) { CrystalElves.talkToIlfeen(plr) }
useItem(SEED).onNpc(ILFEEN) { CrystalElves.offerChant(plr) }
