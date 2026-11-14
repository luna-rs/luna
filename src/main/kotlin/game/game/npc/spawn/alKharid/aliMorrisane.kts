/*
 * Stock and restock data adapted from LostCityRS/Content, revision
 * 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50, scripts/_unpack/727/all.inv
 * and scripts/areas/area_burthorpe/configs/burthrope.inv.
 */
package game.npc.spawn.alKharid

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

// Lost City inventory: feud_morrisanes.
ShopHandler.create("Ali's Discount Wares") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    botAccess = { true }

    sell {
        1931 x 3 every 100 // Pot
        1935 x 2 every 100 // Jug
        1825 x 10 every 300 // Waterskin(3)
        1833 x 3 every 12 // Desert shirt
        1837 x 2 every 100 // Desert boots
        1925 x 19 every 400 // Bucket
        4593 x 11 every 10 // Fake beard
        4591 x 12 every 10 // Karidian headpiece
        970 x 50 every 10 // Papyrus
        946 x 5 every 100 // Knife
        590 x 11 every 100 // Tinderbox
        1265 x 23 every 100 // Bronze pickaxe
        2138 x 15 every 100 // Raw chicken
    }

    open {
        npc1 += 1862
    }
}
