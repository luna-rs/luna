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

// Lost City inventory: keldagrim_quality_armour_shop.
ShopHandler.create("Quality Armour Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1105 x 3 every 400 // Steel chainbody
        1109 x 1 every 3000 // Mithril chainbody
        1107 x 1 every 2500 // Black chainbody
        1111 x 1 every 9000 // Adamant chainbody
        1141 x 3 every 400 // Steel med helm
        1143 x 1 every 3000 // Mithril med helm
        1145 x 1 every 12000 // Adamant med helm
        1177 x 0 every 1500 // Steel sq shield
        1195 x 0 every 1500 // Black kiteshield
    }

    open {
        npc2 += 2153
    }
}

npc1(2153) { GeneralStore.open(plr, "Quality Armour Shop") }
