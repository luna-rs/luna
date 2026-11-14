/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.grandTree

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: giannerestaurant.
ShopHandler.create("Gianne's Restaurant") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.25, 0.01, 1.0)
    botAccess = { true }

    sell {
        2227 x 3 every 100 // Vegetable batta
        2219 x 3 every 100 // Worm batta
        2221 x 3 every 100 // Toad batta
        2225 x 3 every 100 // Fruit batta
        2223 x 3 every 100 // Cheese+tom batta
        2233 x 3 every 100 // Worm hole
        2231 x 3 every 100 // Tangled toad's legs
        2235 x 3 every 100 // Veg ball
        2229 x 3 every 100 // Chocolate bomb
        2237 x 3 every 100 // Worm crunchies
        2243 x 3 every 100 // Toad crunchies
        2239 x 3 every 100 // Chocchip crunchies
        2241 x 3 every 100 // Spicy crunchies
    }

    open {
        npc2 += 851
    }
}

npc1(851) { GeneralStore.open(plr, "Gianne's Restaurant") }
