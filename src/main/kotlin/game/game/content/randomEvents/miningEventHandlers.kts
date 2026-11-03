package game.content.randomEvents

import api.predef.*

for (parts in MiningRandomEvents.Parts.entries) {
    useItem(parts.head).onItem(MiningRandomEvents.HANDLE) { MiningRandomEvents.reattach(plr, parts.head) }
}
