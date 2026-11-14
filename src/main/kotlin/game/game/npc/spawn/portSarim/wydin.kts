/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.portSarim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: wydinstore.
ShopHandler.create("Food Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.01, 1.0)
    botAccess = { true }

    sell {
        1933 x 3 every 100 // Pot of flour
        2132 x 1 every 100 // Raw beef
        2138 x 1 every 100 // Raw chicken
        1965 x 3 every 100 // Cabbage
        1963 x 3 every 100 // Banana
        1951 x 1 every 100 // Redberries
        2309 x 0 every 100 // Bread
        1973 x 1 every 100 // Chocolate bar
        1985 x 3 every 100 // Cheese
        1982 x 3 every 100 // Tomato
        1942 x 1 every 100 // Potato
    }

    open {
        npc2 += 557
    }
}

npc1(557) { GeneralStore.open(plr, "Food Store") }
