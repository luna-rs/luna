/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.burghDeRott

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: burgh_general_store.
ShopHandler.create("Aurel's Supplies") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3517, 3241, 0), 64) }

    sell {
        1351 x 10 every 100 // Bronze axe
        590 x 10 every 100 // Tinderbox
        3363 x 10 every 100 // Thin snail
        353 x 10 every 100 // Raw mackerel
    }

    open {
        npc2 += 3541
    }
}

npc1(3541) { GeneralStore.open(plr, "Aurel's Supplies") }
