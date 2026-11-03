package game.content.randomEvents

import api.predef.*

for (parts in MiningRandomEvents.Parts.entries) {
    useItem(parts.head).onItem(MiningRandomEvents.HANDLE) { MiningRandomEvents.reattach(plr, parts.head) }
    useItem(parts.broken).onNpc(594) { MiningRandomEvents.offerRepair(plr, parts.broken) }
}

for (id in MiningRandomEvents.GAS_ROCKS.values) {
    object1(id) { MiningRandomEvents.mineGas(plr, gameObject) }
    object3(id) { MiningRandomEvents.mineGas(plr, gameObject) }
}
