/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.keldagrim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: smithing_guild_ore_seller.
ShopHandler.create("Ore Seller") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        436 x 100 every 200 // Copper ore
        438 x 100 every 200 // Tin ore
        440 x 100 every 200 // Iron ore
        447 x 100 every 200 // Mithril ore
        442 x 100 every 200 // Silver ore
        444 x 100 every 200 // Gold ore
        453 x 100 every 200 // Coal
    }

    open {
        npc2 += 2564
    }
}

npc1(2564) { GeneralStore.open(plr, "Ore Seller") }
