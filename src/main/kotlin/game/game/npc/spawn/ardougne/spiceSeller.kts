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

// Lost City inventory: spicestall.
ShopHandler.create("Ardougne Spice Stall.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SPICE, 600) }

    sell {
        2007 x 1 every 100 // Spice
        946 x 1 every 2 // Knife
        1550 x 2 every 5 // Garlic
    }
}

npc1(572) { StallMerchant.open(plr, 572, "Ardougne Spice Stall.", ThievingStallType.SPICE, 600) }
npc2(572) { StallMerchant.open(plr, 572, "Ardougne Spice Stall.", ThievingStallType.SPICE, 600) }
