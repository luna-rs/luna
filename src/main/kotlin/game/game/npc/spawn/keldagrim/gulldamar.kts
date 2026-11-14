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

// Lost City inventory: keldagrim_silver_stall.
ShopHandler.create("Silver Cog Silver Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { !StallMerchant.recentTheft(this, ThievingStallType.SILVER, 1000) }

    sell {
        1714 x 2 every 100 // Unstrung symbol
        442 x 1 every 2000 // Silver ore
        2355 x 1 every 1000 // Silver bar
    }
}

npc1(2159) { StallMerchant.open(plr, 2159, "Silver Cog Silver Stall", ThievingStallType.SILVER, 1000) }
npc2(2159) { StallMerchant.open(plr, 2159, "Silver Cog Silver Stall", ThievingStallType.SILVER, 1000) }
