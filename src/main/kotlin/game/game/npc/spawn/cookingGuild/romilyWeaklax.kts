/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.cookingGuild

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: shop_pies.
ShopHandler.create("Pie Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        7162 x 50 every 50 // Pie recipe book
        2325 x 5 every 100 // Redberry pie
        2327 x 4 every 100 // Meat pie
        7170 x 0 every 100 // Mud pie
        2323 x 3 every 100 // Apple pie
        7178 x 2 every 100 // Garden pie
        7188 x 1 every 100 // Fish pie
        7198 x 0 every 100 // Admiral pie
        7208 x 0 every 100 // Wild pie
        7218 x 0 every 100 // Summer pie
    }

    open {
        npc2 += 3205
    }
}

npc1(3205) { GeneralStore.open(plr, "Pie Shop") }
