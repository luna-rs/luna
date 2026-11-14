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

// Lost City inventory: lletyaseamstressshop1.
ShopHandler.create("Oronwen's General Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1734 x 8 every 100 // Thread
        1733 x 3 every 100 // Needle
        1759 x 5 every 100 // Ball of wool
        1763 x 10 every 100 // Red dye
        1765 x 10 every 100 // Yellow dye
        1767 x 10 every 100 // Blue dye
        1769 x 10 every 100 // Orange dye
        1771 x 10 every 100 // Green dye
        1773 x 10 every 100 // Purple dye
    }

    open {
        npc2 += 2353
    }
}

npc1(2353) { GeneralStore.open(plr, "Oronwen's General Store") }
