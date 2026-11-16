/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.burthorpe

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: death_pub.
ShopHandler.create("The Toad and Chicken") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.0, 0.01, 1.0)
    botAccess = { true }

    sell {
        1905 x 12 every 10 // Asgarnian ale
        1907 x 12 every 10 // Wizard's mind bomb
        1913 x 12 every 10 // Dwarven stout
    }

    open {
        npc2 += 1357
        npc2 += 1358
        npc2 += 1079
    }
}

npc1(1357) { GeneralStore.open(plr, "The Toad and Chicken") }
npc1(1358) { GeneralStore.open(plr, "The Toad and Chicken") }
npc1(1079) { GeneralStore.open(plr, "The Toad and Chicken") }
