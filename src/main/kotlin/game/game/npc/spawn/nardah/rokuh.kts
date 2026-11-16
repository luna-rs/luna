/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.nardah

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: chociceshopnardah.
ShopHandler.create("Rokuh's Choc Ice Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        6794 x 30 every 50 // Choc-ice
        1973 x 25 every 20 // Chocolate bar
    }

    open {
        npc2 += 3045
    }
}

npc1(3045) { GeneralStore.open(plr, "Rokuh's Choc Ice Shop") }
