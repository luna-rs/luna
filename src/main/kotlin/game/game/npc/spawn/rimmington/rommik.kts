/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.rimmington

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: craftingshop.
ShopHandler.create("Rommiks Crafty Supplies.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.65, 0.02, 1.0)
    botAccess = { true }

    sell {
        1755 x 2 every 100 // Chisel
        1592 x 4 every 100 // Ring mould
        1597 x 2 every 100 // Necklace mould
        1595 x 2 every 100 // Amulet mould
        1733 x 3 every 100 // Needle
        1734 x 100 every 5 // Thread
        1599 x 3 every 100 // Holy mould
        2976 x 6 every 15 // Sickle mould
        5523 x 10 every 10 // Tiara mould
    }

    open {
        npc2 += 585
    }
}

npc1(585) { GeneralStore.open(plr, "Rommiks Crafty Supplies.") }
