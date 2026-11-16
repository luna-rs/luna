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

// Lost City inventory: keldagrim_clothes_shop.
ShopHandler.create("Agmundi's Quality Clothes") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        5050 x 3 every 50 // Skirt
        5052 x 3 every 50 // Skirt
        5038 x 3 every 40 // Trousers
        5040 x 3 every 40 // Trousers
        5044 x 3 every 35 // Shorts
        5046 x 3 every 35 // Shorts
        5026 x 3 every 55 // Woven top
        5028 x 3 every 55 // Woven top
        5032 x 3 every 50 // Shirt
        5034 x 3 every 50 // Shirt
    }

    open {
        npc2 += 2161
    }
}

npc1(2161) { GeneralStore.open(plr, "Agmundi's Quality Clothes") }
