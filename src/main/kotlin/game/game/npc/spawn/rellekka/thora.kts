/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.rellekka

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: viking_bar.
ShopHandler.create("Thora's Bar") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { Fremennik.isFremennik(this) }

    sell {
        1917 x 50 every 10 // Beer
        3803 x 50 every 10 // Beer
        3801 x 20 every 10 // Keg of beer
    }

    open {
        npc2 += 1300
    }
}

npc1(1300) { GeneralStore.open(plr, "Thora's Bar") }
