package game.content.rogueTrader

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/*
 * The stocks Ali Morrisane gets from his Rogue Trader suppliers. He opens them from his dialogue.
 */

fun aliStock(name: String, stock: List<Pair<Int, Int>>) {
    ShopHandler.create(name) {
        buy = BuyPolicy.EXISTING
        restock = RestockPolicy.BALANCED
        currency = Currency.COINS
        sell {
            stock.forEach { (id, amount) -> id x amount every 50 }
        }
    }
}

aliStock(RogueTrader.ELEMENTAL_RUNES, listOf(554 to 20, 555 to 20, 556 to 20, 557 to 20))
aliStock(RogueTrader.CATALYTIC_RUNES, listOf(561 to 100, 563 to 100, 562 to 100, 560 to 100, 558 to 100, 559 to 100,
                                            565 to 100, 564 to 10, 566 to 100))
aliStock(RogueTrader.OFFENSIVE_BLACKJACKS, listOf(6408 to 25, 6412 to 25, 6418 to 25, 6416 to 25, 4600 to 25))
aliStock(RogueTrader.DEFENSIVE_BLACKJACKS, listOf(6410 to 25, 6414 to 25, 6420 to 25, 6416 to 25, 4600 to 25))
aliStock(RogueTrader.DESERT_CLOTHES, listOf(6382 to 25, 6384 to 25, 6386 to 25, 6388 to 25, 6390 to 25))
aliStock(RogueTrader.MENAPHITE_CLOTHES, listOf(6392 to 25, 6394 to 25, 6396 to 25, 6398 to 25, 6400 to 25, 6402 to 25,
                                              6404 to 25, 6406 to 25))
