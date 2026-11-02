package game.obj.entrances

import api.predef.*

/**
 * Native Paterdomus gate interactions shared by players and walking bots.
 *
 * @author lare96
 */
for (gate in PaterdomusGates.IDS) {
    object1(gate) { PaterdomusGates.open(plr, gameObject) }
}
