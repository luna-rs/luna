package game.obj.doors

import api.predef.*

TrollArenaGates.GATES.forEach { gate ->
    object1(gate.id) { TrollArenaGates.cross(plr, gameObject) }
}
