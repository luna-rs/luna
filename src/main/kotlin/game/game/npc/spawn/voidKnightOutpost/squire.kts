package game.npc.spawn.voidKnightOutpost

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Void Knight General Store at the Void Knights' Outpost, run by a squire.
 */
ShopHandler.create("Void Knight General Store") {
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
        "Bronze axe" x 10 every 100
        "Field ration" x 25 every 10
    }

    open {
        npc2 += 3799
    }
}

npc1(3799) {
    plr.newDialogue()
        .npc(3799, "Hi, how can I help you?")
        .options("What do you have for sale?", {
            GeneralStore.open(it, "Void Knight General Store")
        }, "I'm fine thanks.", {
            it.newDialogue().player("I'm fine thanks.").open()
        })
        .open()
}
