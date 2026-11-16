/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.ardougne

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: topshop.
ShopHandler.create("Zenesha's Plate Mail Body Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { true }

    sell {
        1117 x 3 every 100 // Bronze platebody
        1115 x 1 every 200 // Iron platebody
        1119 x 1 every 400 // Steel platebody
        1125 x 1 every 3000 // Black platebody
        1121 x 1 every 3000 // Mithril platebody
    }

    open {
        npc2 += 589
    }
}

npc1(589) { GeneralStore.open(plr, "Zenesha's Plate Mail Body Shop.") }
