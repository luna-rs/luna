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

// Lost City inventory: charleyshop.
ShopHandler.create("Charley's Fishing Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3673, 2970, 0), 64) }

    sell {
        317 x 10 every 300 // Raw shrimps
        327 x 10 every 600 // Raw sardine
        345 x 7 every 900 // Raw herring
        353 x 7 every 1000 // Raw mackerel
        341 x 6 every 1100 // Raw cod
        321 x 5 every 1200 // Raw anchovies
        359 x 4 every 2300 // Raw tuna
        377 x 2 every 2600 // Raw lobster
        363 x 2 every 2700 // Raw bass
        371 x 1 every 2900 // Raw swordfish
    }

    open {
        npc2 += 3161
    }
}

npc1(3161) { GeneralStore.open(plr, "Charley's Fishing Shop") }
