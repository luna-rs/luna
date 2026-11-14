/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.yanille

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: cookeryshop.
ShopHandler.create("Frenita's Cookery Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.55, 0.01, 1.0)
    botAccess = { true }

    sell {
        2313 x 5 every 200 // Pie dish
        1955 x 2 every 200 // Cooking apple
        1887 x 2 every 200 // Cake tin
        1923 x 2 every 200 // Bowl
        1942 x 5 every 200 // Potato
        590 x 4 every 200 // Tinderbox
        1935 x 1 every 100 // Jug
        1931 x 8 every 100 // Pot
        1973 x 2 every 100 // Chocolate bar
        1933 x 8 every 100 // Pot of flour
        1980 x 20 every 50 // Empty cup
    }

    open {
        npc2 += 593
    }
}

npc1(593) { GeneralStore.open(plr, "Frenita's Cookery Shop.") }
