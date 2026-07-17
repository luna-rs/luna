package game.npc.spawn.fishingGuild

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Fishing Guild Shop in the Fishing Guild.
 */
ShopHandler.create("Fishing Guild Shop.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { fishing.level >= 68 }

    sell {
        "Small fishing net" x 50
        "Big fishing net" x 50
        "Fishing rod" x 50
        "Fly fishing rod" x 50
        "Harpoon" x 20
        "Lobster pot" x 20
        "Fishing bait" x 20_000
        "Feather" x 15_000
        "Raw cod" x 100
        "Raw mackerel" x 100
        "Raw bass" x 100
        "Raw tuna" x 100
        "Raw lobster" x 100
        "Raw swordfish" x 100
        "Cod" x 100
        "Mackerel" x 100
        "Bass" x 100
        "Tuna" x 100
        "Lobster" x 100
        "Swordfish" x 100
    }

    open {
        npc2 += 592
    }
}
