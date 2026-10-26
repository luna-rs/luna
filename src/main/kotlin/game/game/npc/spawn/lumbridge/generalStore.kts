package game.npc.spawn.lumbridge

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * The general store in Lumbridge, run by a shop keeper and an assistant.
 */
ShopHandler.create("Lumbridge General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS

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
        "Newcomer map" x 5 every 100
    }

    open {
        npc2 += 521
    }
}

// The keeper of Draynor's "General Store" shares the shop keeper's id, so he's left to the Draynor store's script.
npc1(520) {
    if (!GeneralStore.isDraynorKeeper(targetNpc)) {
        GeneralStore.greet(plr, 520, "Lumbridge General Store")
    }
}

npc2(520) {
    if (!GeneralStore.isDraynorKeeper(targetNpc)) {
        GeneralStore.open(plr, "Lumbridge General Store")
    }
}

npc1(521) { GeneralStore.greet(plr, 521, "Lumbridge General Store") }
