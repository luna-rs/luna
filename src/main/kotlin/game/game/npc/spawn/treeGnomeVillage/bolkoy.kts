/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.treeGnomeVillage

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

// Lost City inventory: gnomeshop.
ShopHandler.create("Bolkoy's Village Shop") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.4, 0.03, 1.3)
    botAccess = { true }

    sell {
        1931 x 3 every 100 // Pot
        1265 x 5 every 100 // Bronze pickaxe
        1935 x 2 every 100 // Jug
        1735 x 2 every 100 // Shears
        1925 x 2 every 100 // Bucket
        590 x 2 every 100 // Tinderbox
        1755 x 2 every 100 // Chisel
        2347 x 5 every 100 // Hammer
        882 x 30 every 100 // Bronze arrow
        2142 x 2 every 100 // Cooked meat
    }

    open {
        npc2 += 471
    }
}

npc1(471) { GeneralStore.open(plr, "Bolkoy's Village Shop") }
