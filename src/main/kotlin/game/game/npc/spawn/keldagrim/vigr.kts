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

// Lost City inventory: keldagrim_warhammer_shop.
ShopHandler.create("Vigr's Warhammers") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1337 x 5 every 100 // Bronze warhammer
        1335 x 4 every 200 // Iron warhammer
        1339 x 3 every 400 // Steel warhammer
        1341 x 3 every 600 // Black warhammer
        1343 x 2 every 800 // Mithril warhammer
        1345 x 1 every 1000 // Addy warhammer
    }

    open {
        npc2 += 2151
    }
}

npc1(2151) { GeneralStore.open(plr, "Vigr's Warhammers") }
