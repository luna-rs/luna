package world.player.item.banking.regularBank

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Load all banking objects, make them open the bank.
on(ServerLaunchEvent::class) {
    Banking.loadBankingObjects()
    for (id in Banking.bankingObjects) {
        object1(id) { plr.bank.open() }
    }
}