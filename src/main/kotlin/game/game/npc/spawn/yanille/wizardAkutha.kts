package game.npc.spawn.yanille

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy

/**
 * Magic Guild Store (Runes and Staves) in the Wizards' Guild.
 */
ShopHandler.create("Magic Guild Store (Runes and Staves)") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    botAccess = { magic.level >= 66 }

    sell {
        "Air rune" x 5000
        "Water rune" x 5000
        "Earth rune" x 5000
        "Fire rune" x 5000
        "Mind rune" x 5000
        "Body rune" x 5000
        "Chaos rune" x 250
        "Nature rune" x 250
        "Death rune" x 250
        "Law rune" x 250
        "Blood rune" x 250
        "Soul rune" x 250
        "Battlestaff" x 50
        "Staff of fire" x 20
        "Staff of water" x 20
        "Staff of air" x 20
        "Staff of earth" x 20
    }

    // TODO: Resolve and bind the Wizard Akutha NPC ID; it is not identifiable in the 377 NPC cache.
}
