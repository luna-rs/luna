/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.mosLeHarmless

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: joeshop.
ShopHandler.create("Harpoon Joe's House of Rum") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3666, 2990, 0), 64) }

    sell {
        1917 x 3 every 100 // Beer
        7157 x 3 every 100 // Braindeath 'rum'
        1993 x 1 every 15000 // Jug of wine
        2003 x 5 every 150 // Stew
    }

    open {
        npc2 += 3163
    }
}

npc1(3163) { GeneralStore.open(plr, "Harpoon Joe's House of Rum") }
