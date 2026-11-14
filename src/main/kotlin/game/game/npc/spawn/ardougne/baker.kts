/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.ardougne

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.StallMerchant
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: bakery.
ShopHandler.create("Ardougne Baker's Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.8, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.BAKERY, 1500) }

    sell {
        2309 x 10 every 100 // Bread
        1891 x 3 every 800 // Cake
        1901 x 8 every 200 // Chocolate slice
        1973 x 7 every 200 // Chocolate bar
    }
}

npc1(571) { StallMerchant.open(plr, 571, "Ardougne Baker's Stall.", ThievingStallType.BAKERY, 1500) }
npc2(571) { StallMerchant.open(plr, 571, "Ardougne Baker's Stall.", ThievingStallType.BAKERY, 1500) }
