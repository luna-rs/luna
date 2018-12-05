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
        item("Pot") x 10
        item("Jug") x 15
        item("Tinderbox") x 15
        item("Chisel") x 15
        item("Hammer") x 15
        item("Newcomer map") x 15
        item("Bucket") x 15
        item("Bowl") x 15
        item("Anti-dragon shield") x 50
        item("Lobster") x 150
    }

    open {
        npc2 = 520
    }
}