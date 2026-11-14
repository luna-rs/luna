/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.zanaris

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: cheapringshop.
ShopHandler.create("Irksol's Ruby Rings") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.3, 0.01, 0.5)
    merchantsAccessOnly(strict = true) { x in 2465..2505 && y in 4435..4478 && z == 0 }

    sell {
        1641 x 5 every 500 // Ruby ring
    }

    open {
        npc2 += 566
    }
}

npc1(566) { GeneralStore.open(plr, "Irksol's Ruby Rings") }
