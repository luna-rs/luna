/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.miscellania

import api.bot.zone.Zone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: misc_fishmonger.
ShopHandler.create("Miscellanian Fish") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.MISCELLANIA_AND_ETCETERIA }

    sell {
        303 x 5 every 100 // Small fishing net
        307 x 5 every 100 // Fishing rod
        309 x 5 every 100 // Fly fishing rod
        311 x 2 every 400 // Harpoon
        301 x 2 every 400 // Lobster pot
        313 x 1500 every 1 // Fishing bait
        314 x 1000 every 1 // Feather
        305 x 5 every 100 // Big fishing net
        317 x 0 every 300 // Raw shrimps
        327 x 200 every 1 // Raw sardine
        345 x 0 every 900 // Raw herring
        353 x 0 every 1000 // Raw mackerel
        341 x 0 every 1100 // Raw cod
        321 x 0 every 1200 // Raw anchovies
        335 x 0 every 1500 // Raw trout
        349 x 0 every 1800 // Raw pike
        331 x 0 every 2100 // Raw salmon
        359 x 0 every 2300 // Raw tuna
        377 x 0 every 2600 // Raw lobster
        363 x 0 every 2700 // Raw bass
        371 x 0 every 2900 // Raw swordfish
        383 x 0 every 3500 // Raw shark
    }

    open {
        npc2 += 1369
    }
}

npc1(1369) { GeneralStore.open(plr, "Miscellanian Fish") }
