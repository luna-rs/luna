package game.npc.spawn.alKharid

import api.predef.*

// Using either leaf of the gate starts the same conversation as talking to one of the border guards.
object1(BorderGate.LEFT_LEAF) { BorderGate.useGate(plr) }
object1(BorderGate.RIGHT_LEAF) { BorderGate.useGate(plr) }

npc1(BorderGate.LUMBRIDGE_GUARD) { BorderGate.talk(plr, targetNpc.id) }
npc1(BorderGate.AL_KHARID_GUARD) { BorderGate.talk(plr, targetNpc.id) }
