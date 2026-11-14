/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.guTanoth

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: herbloreshopogre.
ShopHandler.create("Grud's Herblore Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.4, 0.03, 1.3)
    botAccess = { true }

    sell {
        229 x 50 every 100 // Vial
        233 x 3 every 100 // Pestle and mortar
        221 x 50 every 20 // Eye of newt
    }

    open {
        npc2 += 874
    }
}

npc1(874) { GeneralStore.open(plr, "Grud's Herblore Stall.") }
