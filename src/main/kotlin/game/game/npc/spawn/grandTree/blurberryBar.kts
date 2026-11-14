/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.grandTree

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: blurberrybar.
ShopHandler.create("Blurberry Bar") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.25, 0.01, 1.0)
    botAccess = { true }

    sell {
        2028 x 10 every 100 // Blurberry special
        2030 x 10 every 100 // Choc saturday
        2032 x 10 every 100 // Drunk dragon
        2034 x 10 every 100 // Fruit blast
        2036 x 10 every 100 // Pineapple punch
        2038 x 10 every 100 // Short green guy
        2040 x 10 every 100 // Wizard blizzard
    }

    open {
        npc2 += 849
    }
}

npc1(849) { GeneralStore.open(plr, "Blurberry Bar") }
