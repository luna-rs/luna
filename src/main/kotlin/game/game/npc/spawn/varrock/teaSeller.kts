/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.varrock

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.StallMerchant
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: teashop.
ShopHandler.create("Ye Olde Tea Shoppe") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.6, 0.02, 1.0)
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.TEA, 1000) }

    sell {
        1978 x 20 every 50 // Cup of tea
    }
}

npc1(595) { StallMerchant.open(plr, 595, "Ye Olde Tea Shoppe", ThievingStallType.TEA, 1000) }
npc2(595) { StallMerchant.open(plr, 595, "Ye Olde Tea Shoppe", ThievingStallType.TEA, 1000) }
