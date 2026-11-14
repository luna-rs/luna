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

// Lost City inventory: aprilfoolshorseshop.
ShopHandler.create("Diango's Toy Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.5, 0.02, 1.5)
    botAccess = { true }

    sell {
        2520 x 5 every 20 // Toy horsey
        2522 x 5 every 20 // Toy horsey
        2524 x 5 every 20 // Toy horsey
        2526 x 5 every 20 // Toy horsey
        4613 x 250 every 20 // Spinning plate
    }

    open {
        npc2 += 970
    }
}

npc1(970) { GeneralStore.open(plr, "Diango's Toy Store") }
