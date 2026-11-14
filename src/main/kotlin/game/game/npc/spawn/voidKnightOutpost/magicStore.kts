/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.voidKnightOutpost

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: pest_rune_store.
ShopHandler.create("Void Knight Magic Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        554 x 5000 every 10 // Fire rune
        555 x 5000 every 10 // Water rune
        556 x 5000 every 10 // Air rune
        557 x 5000 every 10 // Earth rune
        558 x 5000 every 10 // Mind rune
        559 x 5000 every 10 // Body rune
        562 x 250 every 10 // Chaos rune
        560 x 250 every 15 // Death rune
    }

    open {
        npc2 += 3798
    }
}

npc1(3798) { GeneralStore.open(plr, "Void Knight Magic Store") }
