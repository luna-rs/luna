import api.predef.*
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

shop {
    name = "General Store"
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Pot" x 15
        "Jug" x 15
        "Tinderbox" x 15
        "Chisel" x 15
        "Hammer" x 15
        "Newcomer map" x 15
        "Bucket" x 15
        "Bowl" x 15
        "Anti-dragon shield" x 50
        "Lobster" x 150
    }

    open {
        npc2 = 520
    }
}