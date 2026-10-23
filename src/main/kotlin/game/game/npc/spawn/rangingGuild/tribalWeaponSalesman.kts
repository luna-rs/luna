package game.npc.spawn.rangingGuild

import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Authentic Throwing Weapons in the Ranging Guild.
 */
ShopHandler.create("Authentic Throwing Weapons.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze javelin" x 900
        "Iron javelin" x 800
        "Steel javelin" x 700
        "Mithril javelin" x 600
        "Adamant javelin" x 500
        "Rune javelin" x 400
        "Bronze thrownaxe" x 900
        "Iron thrownaxe" x 800
        "Steel thrownaxe" x 700
        "Mithril thrownaxe" x 600
        "Adamnt thrownaxe" x 500
        "Rune thrownaxe" x 400
    }

    open {
        npc2 += 692
    }
}
