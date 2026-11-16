/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.nardah

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: armourshopnardah.
ShopHandler.create("Seddu's Adventurer's Store") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1093 x 1 every 12000 // Rune plateskirt
        1079 x 1 every 11500 // Rune platelegs
        1113 x 1 every 15000 // Rune chainbody
        1099 x 1 every 500 // Green d'hide chaps
        1065 x 1 every 500 // Green d'hide vamb
        1193 x 1 every 500 // Steel kiteshield
        1151 x 1 every 400 // Black med helm
    }

    open {
        npc2 += 3038
    }
}

npc1(3038) { GeneralStore.open(plr, "Seddu's Adventurer's Store") }
