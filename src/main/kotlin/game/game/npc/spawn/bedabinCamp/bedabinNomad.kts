/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.bedabinCamp

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: bedabincampshop.
ShopHandler.create("Bedabin Village Bartering") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.8, 0.03, 1.2)
    botAccess = { true }

    sell {
        1823 x 5 every 100 // Waterskin(4)
        1831 x 5 every 100 // Waterskin(0)
        1937 x 5 every 100 // Jug of water
        1921 x 5 every 100 // Bowl of water
        1929 x 5 every 100 // Bucket of water
        946 x 5 every 100 // Knife
        2347 x 5 every 100 // Hammer
    }

    open {
        npc2 += 833
    }
}

npc1(833) { GeneralStore.open(plr, "Bedabin Village Bartering") }
