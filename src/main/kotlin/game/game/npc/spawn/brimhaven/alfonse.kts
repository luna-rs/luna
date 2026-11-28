/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.brimhaven

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.heroesQuest.HeroesGang
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

val restaurant = "The Shrimp and Parrot"

// Lost City inventory: fishrestaurant.
ShopHandler.create(restaurant) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    stockPricing = Shop.StockPricing(0.75, 0.03, 1.3)

    sell {
        347 x 5 every 900 // Herring
        339 x 5 every 1100 // Cod
        361 x 5 every 2300 // Tuna
        379 x 3 every 2600 // Lobster
        373 x 2 every 2900 // Swordfish
        3144 x 3 every 900 // Cooked karambwan
    }

    open {
        npc2 += HeroesGang.ALFONSE
    }
}

npc1(HeroesGang.ALFONSE) {
    if (!HeroesGang.alfonse(plr, targetNpc) { GeneralStore.open(it, restaurant) }) {
        GeneralStore.open(plr, restaurant)
    }
}
