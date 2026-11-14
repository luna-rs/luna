/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.StallMerchant
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: keldagrim_bread_stall.
ShopHandler.create("Randivor's Bakery") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.BAKERY, 1500) }

    sell {
        2309 x 10 every 100 // Bread
        1891 x 3 every 800 // Cake
        1901 x 8 every 200 // Chocolate slice
    }
}

npc1(2156) { StallMerchant.open(plr, 2156, "Randivor's Bakery", ThievingStallType.BAKERY, 1500) }
npc2(2156) { StallMerchant.open(plr, 2156, "Randivor's Bakery", ThievingStallType.BAKERY, 1500) }
