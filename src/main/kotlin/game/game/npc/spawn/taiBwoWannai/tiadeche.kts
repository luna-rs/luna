package game.npc.spawn.taiBwoWannai

import api.predef.*
import api.shop.dsl.ShopHandler
import game.skill.fishing.catchFish.Fish
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Tiadeche's Karambwan Stall in Tai Bwo Wannai.
 */
ShopHandler.create("Tiadeche's Karambwan Stall") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true) { fishing.level >= Fish.KARAMBWAN.level }

    sell {
        "Raw karambwan" x 1000
        "Raw karambwanji" x 5000
        "Karambwan vessel" x 20
    }

    open {
        npc2 += 1164
    }
}
