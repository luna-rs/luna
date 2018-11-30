import api.*
import io.luna.game.model.item.shop.Currency.COINS

shop {
    name = "General Store"
    buy = BUY_ALL
    restock = RESTOCK_FAST
    currency = COINS

    sell {
        item("Abyssal whip") x 100
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