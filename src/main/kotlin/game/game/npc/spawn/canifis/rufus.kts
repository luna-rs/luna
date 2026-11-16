/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.canifis

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: werewolfstore1.
ShopHandler.create("Rufus' Meat Emporium") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.03, 1.3)
    botAccess = { true }

    sell {
        2132 x 10 every 100 // Raw beef
        2138 x 10 every 100 // Raw chicken
        2134 x 10 every 100 // Raw rat meat
        2136 x 10 every 100 // Raw bear meat
        335 x 5 every 500 // Raw trout
        349 x 5 every 500 // Raw pike
        331 x 5 every 500 // Raw salmon
        383 x 1 every 1000 // Raw shark
    }

    open {
        npc2 += 1038
    }
}

npc1(1038) { GeneralStore.open(plr, "Rufus' Meat Emporium") }
