/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.duelArena

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: duel_rottenfruitshop.
ShopHandler.create("Shop of Distaste") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.55, 0.01, 1.0)
    botAccess = { true }

    sell {
        2518 x 2000 every 10 // Rotten tomato
    }

    open {
        npc3 += 958
        object1 += 3195
    }
}
