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

// Lost City inventory: keldagrim_crafting_stall.
ShopHandler.create("Carefree Crafting Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1755 x 2 every 100 // Chisel
        1592 x 4 every 100 // Ring mould
        1597 x 2 every 100 // Necklace mould
        1733 x 3 every 100 // Needle
        1734 x 100 every 5 // Thread
        1759 x 100 every 5 // Ball of wool
    }

    open {
        npc2 += 2158
    }
}

npc1(2158) { GeneralStore.open(plr, "Carefree Crafting Stall") }
