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

// Lost City inventory: keldagrim_quality_weapons_shop.
ShopHandler.create("Quality Weapons Shop") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1365 x 2 every 200 // Steel battleaxe
        1369 x 1 every 3000 // Mithril battleaxe
        1285 x 3 every 3000 // Mithril sword
        1287 x 2 every 12000 // Adamant sword
        1325 x 2 every 600 // Steel scimitar
        1297 x 2 every 1500 // Black longsword
        1303 x 1 every 4000 // Rune longsword
        853 x 1 every 100 // Maple shortbow
        851 x 1 every 100 // Maple longbow
        888 x 800 every 15 // Mithril arrow
        890 x 600 every 20 // Adamant arrow
    }

    open {
        npc2 += 2152
    }
}

npc1(2152) { GeneralStore.open(plr, "Quality Weapons Shop") }
