package game.npc.spawn.shilo

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Obli's General Store in Shilo Village.
 */
ShopHandler.create("Obli's General Store.") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Tinderbox" x 2 every 500
        "Vial" x 10 every 500
        "Pestle and mortar" x 3 every 500
        "Pot" x 3 every 500
        "Bronze axe" x 3 every 500
        "Bronze pickaxe" x 2 every 500
        "Iron axe" x 5 every 700
        "Leather body" x 12 every 500
        "Leather gloves" x 10 every 500
        "Leather boots" x 10 every 500
        "Cooked meat" x 2 every 500
        "Bread" x 10 every 500
        "Bronze bar" x 10 every 500
        "Spade" x 10 every 500
        "Candle" x 10 every 500
        596 x 10 every 500 // Unlit torch
        "Chisel" x 10 every 500
        "Hammer" x 10 every 500
        "Papyrus" x 50 every 200
        "Charcoal" x 50 every 200
        "Vial of water" x 50 every 200
        "Machete" x 50 every 200
        "Rope" x 10 every 200
    }

    open {
        npc2 += 516
    }
}

npc1(516) {
    plr.newDialogue()
        .npc(516, "Welcome to Obli's General Store Bwana!", "Would you like to see my items?")
        .options("Yes please!", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please!")
                .then { GeneralStore.open(it, "Obli's General Store.") }
                .open()
        }, "No, but thanks for the offer.", {
            it.newDialogue().player("No, but thanks for the offer.").open()
        })
        .open()
}
