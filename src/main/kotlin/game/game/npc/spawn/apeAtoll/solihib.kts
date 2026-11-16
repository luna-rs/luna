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

// Lost City inventory: mm_food_shop.
ShopHandler.create("Solihib's Food Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.APE_ATOLL }

    sell {
        4012 x 200 every 100 // Monkey nuts
        1963 x 1000 every 1000 // Banana
        4016 x 10 every 100 // Banana stew
        4014 x 20 every 100 // Monkey bar
    }

    open {
        npc2 += 1433
    }
}

npc1(1433) { GeneralStore.open(plr, "Solihib's Food Stall") }
