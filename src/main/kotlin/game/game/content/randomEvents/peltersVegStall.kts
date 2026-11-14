/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.content.randomEvents

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: pillory_rottenfruitshop; opened through the vegetable crate.
ShopHandler.create("Pelter's Veg Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.55, 0.001, 1.0)
    botAccess = { false } // A punishment-event supply, rather than an ordinary bot shopping destination.

    sell {
        2518 x 100 every 100 // Rotten tomato; no item-specific restock interval is recorded.
    }

    open {
        object1 += 6839
    }
}
