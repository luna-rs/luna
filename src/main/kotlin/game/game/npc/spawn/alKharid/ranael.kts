package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

/**
 * The [Npc] id for Ranael.
 */
val RANAEL_ID = 544

/**
 * The name of the shop.
 */
val SHOP_NAME = "Ranael's Super Skirt Store."

/**
 * Ranael's Super Skirt Store in Al-Kharid.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bronze plateskirt" x 10
        "Iron plateskirt" x 10
        "Steel plateskirt" x 10
        "Black plateskirt" x 10
        "Mithril plateskirt" x 10
        "Adamant plateskirt" x 10
    }

    open {
        npc2 += RANAEL_ID
    }
}

/*
 * Handles Ranael's first npc option dialogue.
 *
 * The player can either open the skirt shop or decline it.
 */
npc1(RANAEL_ID) {
    plr.newDialogue()
        .npc(
            targetNpc.id,
            "Do you want to buy any armoured skirts?",
            "Designed especially for ladies who like to fight."
        )
        .options(
            "Yes please.",
            {
                plr.newDialogue()
                    .player("Yes please.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No thank you, that's not my scene.",
            {
                plr.newDialogue()
                    .player("No thank you, that's not my scene.")
                    .open()
            }
        ).open()
}
