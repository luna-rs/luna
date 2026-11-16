/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.wilderness

import api.bot.zone.Zone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: pizzabaseshop.
ShopHandler.create("Tony's Pizza Bases") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.03, 1.0)
    botAccess = { this in Zone.WILDERNESS }

    sell {
        2283 x 30 every 100 // Pizza base
    }

    open {
        npc2 += 596
    }
}

npc1(596) { GeneralStore.open(plr, "Tony's Pizza Bases") }
