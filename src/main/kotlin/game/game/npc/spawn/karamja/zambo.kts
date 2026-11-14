/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.karamja

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: boozeshop.
ShopHandler.create("Karamja Wines, Spirits, and Beers.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.02, 1.0)
    botAccess = { true }

    sell {
        1917 x 3 every 100 // Beer
        431 x 3 every 100 // Karamjan rum
        1993 x 1 every 15000 // Jug of wine
    }

    open {
        npc2 += 568
    }
}

npc1(568) { GeneralStore.open(plr, "Karamja Wines, Spirits, and Beers.") }
