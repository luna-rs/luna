package game.npc.spawn.legendsGuild

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The Legends Guild General Store on the first floor of the Legends' Guild.
 */
ShopHandler.create("Legends Guild General Store.") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { combatLevel > 30 }

    sell {
        "Swordfish" x 20 every 200
        "Apple pie" x 5 every 200
        "Attack potion(3)" x 3 every 100
        "Steel arrow" x 500 every 100
    }

    open {
        npc2 += 932
    }
}

npc1(932) {
    plr.newDialogue()
        .npc(932, "Can I help you at all?")
        .options("Yes please. What are you selling?", {
            it.newDialogue()
                .player("Yes please. What are you selling?")
                .npc(932, "Take a look.")
                .then { GeneralStore.open(it, "Legends Guild General Store.") }
                .open()
        }, "No thanks.", {
            // The missing full stop is how she was in the 377 era.
            it.newDialogue().player("No thanks").open()
        })
        .open()
}
