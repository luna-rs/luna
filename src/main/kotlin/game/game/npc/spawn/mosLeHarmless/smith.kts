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

// Lost City inventory: smithsshop.
ShopHandler.create("Smithing Smith's Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3689, 2954, 0), 64) }

    sell {
        1321 x 5 every 100 // Bronze scimitar
        1323 x 3 every 200 // Iron scimitar
        1325 x 2 every 600 // Steel scimitar
        1329 x 1 every 4000 // Mithril scimitar
        2347 x 1 every 100 // Hammer
    }

    open {
        npc2 += 3162
    }
}

npc1(3162) { GeneralStore.open(plr, "Smithing Smith's Shop") }
