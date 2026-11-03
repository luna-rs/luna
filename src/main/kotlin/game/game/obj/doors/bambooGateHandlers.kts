package game.obj.doors

import api.predef.*

for (id in BambooGate.IDS) {
    object1(id, { _, _ -> BambooGate.POLICY }) { BambooGate.use(plr, gameObject) }
}
