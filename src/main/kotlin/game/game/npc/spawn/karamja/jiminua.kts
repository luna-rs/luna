package game.npc.spawn.karamja

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * Jiminua's Jungle Store on Karamja.
 */
ShopHandler.create("Jiminua's Jungle Store.") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Tinderbox" x 2 every 500
        "Candle" x 10 every 500
        596 x 10 every 500 // Unlit torch
        "Pot" x 3 every 500
        "Rope" x 10 every 200
        "Leather body" x 12 every 500
        "Leather gloves" x 10 every 500
        "Leather boots" x 10 every 500
        "Cooked meat" x 2 every 500
        "Bread" x 10 every 500
        "Vial" x 10 every 500
        "Vial of water" x 50 every 200
        "Pestle and mortar" x 3 every 500
        "Antipoison(3)" x 10 every 150
        "Papyrus" x 50 every 200
        "Charcoal" x 50 every 200
        "Knife" x 2 every 100
        "Hammer" x 10 every 500
        "Machete" x 50 every 200
        "Chisel" x 10 every 500
        "Spade" x 10 every 500
        "Bronze axe" x 2 every 500
        "Bronze pickaxe" x 2 every 500
        "Iron axe" x 5 every 700
        "Bronze bar" x 10 every 500
    }

    open {
        npc2 += 560
    }
}

npc1(560) {
    plr.newDialogue()
        .npc(560, Expression.HAPPY, "Welcome to the Jungle Store.", "Can I help you at all?")
        .options("Yes please. What are you selling?", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please. What are you selling?")
                .then { GeneralStore.open(it, "Jiminua's Jungle Store.") }
                .open()
        }, "No thanks.", {
            it.newDialogue().player("No thanks.").open()
        })
        .open()
}
