package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Gunslik's Assorted Items, the general store in Keldagrim.
 */
ShopHandler.create("Gunslik's Assorted Items") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Jug" x 2 every 100
        "Bucket" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
        "Candle" x 10 every 500
        596 x 10 every 500 // Unlit torch
        "Charcoal" x 2 every 200
        "Leather gloves" x 10 every 500
        "Vial" x 10 every 500
        "Pestle and mortar" x 3 every 500
        "Rope" x 10 every 200
    }

    open {
        npc2 += 2154
    }
}

npc1(2154) {
    plr.newDialogue()
        .npc(2154, "What can I interest you in? We have something of", "everything here!")
        .options("Oh good!", {
            it.newDialogue()
                .player(Expression.HAPPY, "Oh good!")
                .then { GeneralStore.open(it, "Gunslik's Assorted Items") }
                .open()
        }, "Nothing, thanks.", {
            it.newDialogue().player(Expression.HAPPY, "Nothing, thanks.").open()
        })
        .open()
}
