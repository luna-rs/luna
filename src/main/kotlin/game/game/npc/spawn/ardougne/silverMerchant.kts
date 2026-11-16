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

// Lost City inventory: silverstall.
ShopHandler.create("Ardougne Silver Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.7, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SILVER, 1000) }

    sell {
        1714 x 2 every 100 // Unstrung symbol
        442 x 1 every 2000 // Silver ore
        2355 x 1 every 1000 // Silver bar
    }
}

npc1(569) { StallMerchant.open(plr, 569, "Ardougne Silver Stall.", ThievingStallType.SILVER, 1000) }
npc2(569) { StallMerchant.open(plr, 569, "Ardougne Silver Stall.", ThievingStallType.SILVER, 1000) }
