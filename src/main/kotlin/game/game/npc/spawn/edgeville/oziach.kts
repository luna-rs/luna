/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.edgeville

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: runeplateshop.
ShopHandler.create("Oziach's Armour") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.4, 0.03, 1.3)
    botAccess = { true }

    sell {
        1127 x 2 every 500 // Rune platebody
        1135 x 2 every 500 // Green d'hide body
    }

    open {
        npc2 += 747
    }
}

npc1(747) { GeneralStore.open(plr, "Oziach's Armour") }
