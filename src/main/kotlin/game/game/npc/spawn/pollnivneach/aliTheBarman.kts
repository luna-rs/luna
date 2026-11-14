/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.pollnivneach

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: feud_alispub.
ShopHandler.create("Ali the Barman's Bar") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1917 x 83 every 10 // Beer
        2017 x 10 every 100 // Whisky
        1993 x 13 every 150 // Jug of wine
        2015 x 5 every 5 // Vodka
        2021 x 4 every 1500 // Brandy
        1915 x 12 every 2500 // Grog
    }

    open {
        npc1 += 1864
    }
}
