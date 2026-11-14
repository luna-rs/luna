/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.combatTrainingCamp

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: lathastrainingstore.
ShopHandler.create("Armoury") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.5, 0.01, 1.5)
    botAccess = { position.isWithinDistance(Position(2514, 3385, 0), 64) }

    sell {
        882 x 200 every 10 // Bronze arrow
        877 x 200 every 10 // Bolts
        841 x 4 every 100 // Shortbow
        839 x 2 every 200 // Longbow
        837 x 2 every 400 // Crossbow
        39 x 800 every 10 // Bronze arrowtips
        40 x 800 every 20 // Iron arrowtips
        41 x 800 every 40 // Steel arrowtips
        42 x 800 every 80 // Mithril arrowtips
        1349 x 5 every 200 // Iron axe
        1353 x 3 every 400 // Steel axe
        1363 x 5 every 100 // Iron battleaxe
        1365 x 2 every 200 // Steel battleaxe
        1369 x 1 every 3000 // Mithril battleaxe
        1307 x 4 every 200 // Bronze 2h sword
        1309 x 3 every 300 // Iron 2h sword
        1311 x 2 every 500 // Steel 2h sword
        1313 x 1 every 700 // Black 2h sword
        1315 x 1 every 1000 // Mithril 2h sword
        1317 x 1 every 15000 // Adamant 2h sword
    }

    open {
        npc2 += 561
    }
}

npc1(561) { GeneralStore.open(plr, "Armoury") }
