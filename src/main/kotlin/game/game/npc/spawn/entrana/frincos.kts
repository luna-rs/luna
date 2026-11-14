/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.entrana

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.sailing.Entrana
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: herbloreshop2.
ShopHandler.create("Frincos Fabulous Herb Store.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.02, 1.0)
    botAccess = { !Entrana.isCarryingForbidden(this) }

    sell {
        229 x 50 every 100 // Vial
        233 x 3 every 100 // Pestle and mortar
        221 x 50 every 20 // Eye of newt
    }

    open {
        npc2 += 578
    }
}

npc1(578) { GeneralStore.open(plr, "Frincos Fabulous Herb Store.") }
