/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: keldagrim_pickaxe_shop.
ShopHandler.create("Pickaxe-Is-Mine") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1265 x 6 every 50 // Bronze pickaxe
        1269 x 4 every 200 // Steel pickaxe
        1273 x 3 every 300 // Mithril pickaxe
        1271 x 2 every 500 // Adamant pickaxe
        1275 x 1 every 600 // Rune pickaxe
    }

    open {
        npc2 += 2160
    }
}

npc1(2160) { GeneralStore.open(plr, "Pickaxe-Is-Mine") }
