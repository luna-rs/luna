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

// Lost City inventory: furshop.
ShopHandler.create("Ardougne Fur Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.95, 0.02, 1.2)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.FUR, 1000) }

    sell {
        948 x 3 every 100 // Bear fur
        958 x 3 every 100 // Grey wolf fur
    }
}

npc1(573) { StallMerchant.open(plr, 573, "Ardougne Fur Stall.", ThievingStallType.FUR, 1000) }
npc2(573) { StallMerchant.open(plr, 573, "Ardougne Fur Stall.", ThievingStallType.FUR, 1000) }
