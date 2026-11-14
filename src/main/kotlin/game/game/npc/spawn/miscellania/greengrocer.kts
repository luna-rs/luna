/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.miscellania

import api.bot.zone.Zone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: misc_greengrocer.
ShopHandler.create("Island Greengrocer") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { this in Zone.MISCELLANIA_AND_ETCETERIA }

    sell {
        1965 x 10 every 100 // Cabbage
        1942 x 10 every 100 // Potato
        1957 x 10 every 100 // Onion
        1982 x 10 every 100 // Tomato
        1550 x 2 every 400 // Garlic
    }

    open {
        npc2 += 1370
    }
}

npc1(1370) { GeneralStore.open(plr, "Island Greengrocer") }
