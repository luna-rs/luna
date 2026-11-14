/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.draynor

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: seed_stall.
ShopHandler.create("Draynor Seed Market.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.03, 1.2)
    botAccess = { true }

    sell {
        5318 x 20 every 100 // Potato seed
        5319 x 10 every 100 // Onion seed
        5324 x 5 every 100 // Cabbage seed
        5322 x 0 every 100 // Tomato seed
        5320 x 0 every 100 // Sweetcorn seed
        5323 x 0 every 100 // Strawberry seed
        5321 x 0 every 100 // Watermelon seed
        5305 x 20 every 100 // Barley seed
        5306 x 5 every 100 // Jute seed
        5097 x 20 every 100 // Rosemary seed
        5096 x 20 every 100 // Marigold seed
        5307 x 20 every 100 // Hammerstone seed
        5308 x 10 every 100 // Asgarnian seed
        5309 x 0 every 100 // Yanillian seed
        5310 x 0 every 100 // Krandorian seed
        5311 x 0 every 100 // Wildblood seed
    }

    open {
        npc2 += 2233
    }
}

npc1(2233) { GeneralStore.open(plr, "Draynor Seed Market.") }
