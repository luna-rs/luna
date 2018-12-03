import api.*
import io.luna.game.model.item.shop.Currency.COINS
import io.luna.game.model.item.shop.RestockPolicy.FAST
import io.luna.game.model.item.shop.SellPolicy.ALL

shop {
    name = "General Store"
    buy = ALL
    restock = FAST
    currency = COINS

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
        type = TYPE_NPC
        id = 520
    }
}