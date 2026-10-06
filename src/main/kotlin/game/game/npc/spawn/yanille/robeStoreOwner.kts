package game.npc.spawn.yanille

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Magic Guild Store (Mystic Robes) in the Wizards' Guild.
 */
ShopHandler.create("Magic Guild Store (Mystic Robes)") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { magic.level >= 66 }

    sell {
        "Mystic hat" x 1000
        "Mystic robe top" x 1000
        "Mystic robe bottom" x 1000
        "Mystic gloves" x 1000
        "Mystic boots" x 1000
    }

    open {
        npc2 += 1658
    }
}
