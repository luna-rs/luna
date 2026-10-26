package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

/**
 * The [Npc] id for the gem trader.
 */
val GEM_TRADER_ID = 540

/**
 * The name of the shop.
 */
val SHOP_NAME = "Gem Trader."

/**
 * Gem Trader in Al-Kharid.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly(true)

    sell {
        "Uncut sapphire" x 0
        "Uncut emerald" x 0
        "Uncut ruby" x 0
        "Uncut diamond" x 0
        "Sapphire" x 0
        "Emerald" x 0
        "Ruby" x 0
        "Diamond" x 0
    }

    open {
        npc2 += GEM_TRADER_ID
    }
}

/*
 * Handles the gem trader's first npc option dialogue.
 *
 * The player can either open the gem shop or decline it.
 */
npc1(GEM_TRADER_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, "Good day to you traveller.", "Would you be interested in buying some gems?")
        .options(
            "Yes please.",
            {
                plr.newDialogue()
                    .player("Yes please.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No thank you.",
            {
                plr.newDialogue()
                    .player("No thank you.")
                    .npc(targetNpc.id, "Eh, suit yourself.")
                    .open()
            }
        ).open()
}
