package game.npc.spawn.dwarvenMine

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * The general store in the Dwarven Mine, run by a dwarf.
 */
ShopHandler.create("Dwarven shopping store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS

    sell {
        "Pot" x 3 every 100
        "Jug" x 2 every 100
        "Shears" x 2 every 100
        "Bucket" x 3 every 10
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
    }

    open {
        npc2 += 582
    }
}

npc1(582) { GeneralStore.greet(plr, 582, "Dwarven shopping store", Expression.DEFAULT) }
