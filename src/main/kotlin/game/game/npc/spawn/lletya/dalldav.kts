/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.lletya

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: lletyaarcheryshop1.
ShopHandler.create("Lletya Archery Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        884 x 2000 every 20 // Iron arrow
        886 x 500 every 40 // Steel arrow
        888 x 500 every 60 // Mithril arrow
        890 x 450 every 120 // Adamant arrow
        892 x 400 every 130 // Rune arrow
        877 x 1500 every 10 // Bolts
        843 x 5 every 200 // Oak shortbow
        845 x 5 every 200 // Oak longbow
        837 x 5 every 400 // Crossbow
        849 x 5 every 70 // Willow shortbow
        847 x 5 every 70 // Willow longbow
    }

    open {
        npc2 += 2356
    }
}

npc1(2356) { GeneralStore.open(plr, "Lletya Archery Shop") }
