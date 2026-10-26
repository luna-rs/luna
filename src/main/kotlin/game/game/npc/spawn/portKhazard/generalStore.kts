package game.npc.spawn.portKhazard

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * The general store in Port Khazard.
 */
ShopHandler.create("Khazard General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Bronze pickaxe" x 5 every 100
        "Pot" x 3 every 100
        "Jug" x 2 every 100
        "Shears" x 2 every 100
        "Bucket" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 5 every 100
        "Rope" x 30 every 20
        "Pot of flour" x 30 every 100
        "Bailing bucket" x 30 every 100
        "Swamp paste" x 500 every 20
        "Knife" x 2 every 100
    }

    open {
        npc2 += 555
    }
}

npc1(555) { GeneralStore.greet(plr, 555, "Khazard General Store", Expression.DEFAULT) }
