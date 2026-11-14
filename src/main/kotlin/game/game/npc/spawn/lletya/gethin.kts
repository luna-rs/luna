/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.lletya

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: lletyafoodshop1.
ShopHandler.create("Lletya Food Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        2309 x 10 every 200 // Bread
        379 x 15 every 200 // Lobster
        1993 x 3 every 500 // Jug of wine
        1985 x 10 every 200 // Cheese
        1891 x 5 every 200 // Cake
    }

    open {
        npc2 += 2357
    }
}

npc1(2357) { GeneralStore.open(plr, "Lletya Food Store") }
