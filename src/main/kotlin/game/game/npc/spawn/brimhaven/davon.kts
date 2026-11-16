/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.brimhaven

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: amuletshop.
ShopHandler.create("Davon's Amulet Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.9, 0.02, 1.2)
    botAccess = { true }

    sell {
        1718 x 0 every 200 // Holy symbol
        1727 x 0 every 200 // Amulet of magic
        1729 x 0 every 200 // Amulet of defence
        1725 x 0 every 200 // Amulet of strength
        1731 x 0 every 200 // Amulet of power
    }

    open {
        npc2 += 588
    }
}

npc1(588) { GeneralStore.open(plr, "Davon's Amulet Store") }
