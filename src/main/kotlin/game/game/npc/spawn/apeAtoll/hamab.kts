/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.apeAtoll

import api.bot.zone.Zone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: mm_crafting_shop.
ShopHandler.create("Hamab's Crafting Emporium") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.APE_ATOLL }

    sell {
        1755 x 2 every 100 // Chisel
        1592 x 4 every 100 // Ring mould
        1597 x 2 every 100 // Necklace mould
        4020 x 2 every 100 // M'amulet mould
        1733 x 3 every 5 // Needle
        1734 x 100 every 5 // Thread
        1759 x 100 every 100 // Ball of wool
    }

    open {
        npc2 += 1437
    }
}

npc1(1437) { GeneralStore.open(plr, "Hamab's Crafting Emporium") }
