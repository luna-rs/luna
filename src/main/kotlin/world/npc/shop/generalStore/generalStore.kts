package world.npc.shop.generalStore

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * General store shop.
 */
ShopHandler.create("General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Pot" x 15
        "Jug" x 15
        "Tinderbox" x 15
        "Chisel" x 15
        "Hammer" x 15
        "Bucket" x 15
        "Bowl" x 15
        "Anti-dragon shield" x 50
        "Lobster" x 150
    }

    open {
        npc2 += 520
    }
}

/**
 * Dialogue for "Talk" option.
 */
npc1(520) {
    plr.newDialogue()
        .npc(targetNpc.id, "Hi, here's what I have in stock for today!")
        .then { it.interfaces.openShop("General Store") }.open()
}

/**
 * Spawn general store NPC.
 */
on(ServerLaunchEvent::class) {
    world.addNpc(id = 520,
                 x = 3091,
                 y = 3250)
}
