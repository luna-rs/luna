/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.taverley

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: 2handedshop.
ShopHandler.create("Gaius' Two Handed Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { true }

    sell {
        1307 x 4 every 200 // Bronze 2h sword
        1309 x 3 every 300 // Iron 2h sword
        1311 x 2 every 500 // Steel 2h sword
        1313 x 1 every 700 // Black 2h sword
        1315 x 1 every 1000 // Mithril 2h sword
        1317 x 1 every 15000 // Adamant 2h sword
    }

    open {
        npc2 += 586
    }
}

npc1(586) { GeneralStore.open(plr, "Gaius' Two Handed Shop.") }
