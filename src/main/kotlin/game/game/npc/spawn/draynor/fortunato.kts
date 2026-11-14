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

// Lost City inventory: wine_merchant.
ShopHandler.create("Wine Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1993 x 5 every 1500 // Jug of wine
        1935 x 3 every 100 // Jug
        7919 x 2 every 1500 // Bottle of wine
    }

    open {
        npc2 += 3671
    }
}

npc1(3671) { GeneralStore.open(plr, "Wine Shop") }
