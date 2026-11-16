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

// Lost City inventory: keldagrim_clothes_stall.
ShopHandler.create("Vermundi's Clothes Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        5048 x 3 every 40 // Skirt
        5036 x 3 every 30 // Trousers
        5042 x 3 every 25 // Shorts
        5024 x 3 every 45 // Woven top
        5030 x 3 every 40 // Shirt
        950 x 5 every 100 // Silk
    }

    open {
        npc2 += 2162
    }
}

npc1(2162) { GeneralStore.open(plr, "Vermundi's Clothes Stall") }
