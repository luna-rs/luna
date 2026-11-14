/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.dwarvenMine

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: miningstore.
ShopHandler.create("Drogo's Mining Emporium.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.02, 1.0)
    botAccess = { true }

    sell {
        2347 x 4 every 100 // Hammer
        1265 x 4 every 100 // Bronze pickaxe
        436 x 0 every 15000 // Copper ore
        438 x 0 every 15000 // Tin ore
        440 x 0 every 5000 // Iron ore
        453 x 0 every 5000 // Coal
        2349 x 0 every 4000 // Bronze bar
        2351 x 0 every 4000 // Iron bar
        2357 x 0 every 3000 // Gold bar
    }

    open {
        npc2 += 579
    }
}

npc1(579) { GeneralStore.open(plr, "Drogo's Mining Emporium.") }
