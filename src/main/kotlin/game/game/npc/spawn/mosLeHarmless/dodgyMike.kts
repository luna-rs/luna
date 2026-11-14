/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.mosLeHarmless

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: mikeshop.
ShopHandler.create("Dodgy Mike's Clothing") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { position.isWithinDistance(Position(3695, 2978, 0), 64) }

    sell {
        7114 x 15 every 100 // Pirate boots
        7110 x 10 every 100 // Stripy pirate shirt
        7112 x 20 every 100 // Pirate bandanna
        7116 x 10 every 100 // Pirate leggings
        7122 x 10 every 100 // Stripy pirate shirt
        7124 x 20 every 100 // Pirate bandanna
        7126 x 10 every 100 // Pirate leggings
        7128 x 10 every 100 // Stripy pirate shirt
        7130 x 20 every 100 // Pirate bandanna
        7132 x 10 every 100 // Pirate leggings
        7134 x 10 every 100 // Stripy pirate shirt
        7136 x 20 every 100 // Pirate bandanna
        7138 x 10 every 100 // Pirate leggings
    }

    open {
        npc2 += 3166
    }
}

npc1(3166) { GeneralStore.open(plr, "Dodgy Mike's Clothing") }
