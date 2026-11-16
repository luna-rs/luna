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

// Lost City inventory: keldagrim_gem_stall.
ShopHandler.create("Green Gemstone Gems") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly(strict = true) { !StallMerchant.recentTheft(this, ThievingStallType.GEM, 1000) }

    sell {
        1607 x 3 every 15000 // Sapphire
        1605 x 1 every 1000 // Emerald
        1603 x 1 every 2000 // Ruby
        1601 x 0 every 4000 // Diamond
    }
}

npc1(2157) { StallMerchant.open(plr, 2157, "Green Gemstone Gems", ThievingStallType.GEM, 1000) }
npc2(2157) { StallMerchant.open(plr, 2157, "Green Gemstone Gems", ThievingStallType.GEM, 1000) }
