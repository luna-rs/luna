package game.npc.spawn.nardah

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Nardah General Store, run by Kazemde.
 */
ShopHandler.create("Nardah General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Pot" x 5 every 10
        "Jug" x 2 every 100
        "Shears" x 2 every 100
        "Bucket" x 3 every 10
        "Bowl" x 2 every 50
        "Cake tin" x 2 every 50
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
        "Cooked meat" x 4 every 100
    }

    open {
        npc2 += 3039
    }
}

npc1(3039) {
    plr.newDialogue()
        .npc(3039, "Hello, and welcome to my shop.")
        .options("Let me see what you have.", {
            GeneralStore.open(it, "Nardah General Store")
        }, "Goodbye.", {
            it.overlays.closeWindows()
        })
        .open()
}
