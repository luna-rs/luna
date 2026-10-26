package game.npc.spawn.catherby

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Harry.
 */
val HARRY_ID = 576

/**
 * The name of the shop.
 */
val SHOP_NAME = "Harrys Fishing Shop."

/*
 * Registers Harry's fishing shop in Catherby.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Small fishing net" x 10
        "Fishing rod" x 10
        "Harpoon" x 1000
        "Lobster pot" x 10
        "Fishing bait" x 1000
        "Big fishing net" x 10
        "Raw shrimps" x 0
        "Raw sardine" x 0
        "Raw herring" x 0
        "Raw mackerel" x 0
        "Raw cod" x 0
        "Raw anchovies" x 0
        "Raw tuna" x 0
        "Raw lobster" x 0
        "Raw bass" x 0
        "Raw swordfish" x 0
        "Raw shark" x 0
    }

    open {
        npc2 += HARRY_ID
    }
}

/*
 * Handles Harry's first npc option dialogue.
 *
 * The player can either open the fishing shop or decline it.
 */
npc1(HARRY_ID) {
    plr.newDialogue()
        .npc(
            targetNpc.id,
            Expression.HAPPY,
            "Welcome, you can buy fishing equipment at my store.",
            "We'll also give you a good price for any fish that",
            "you catch."
        )
        .options(
            "Let's see what you've got then.",
            {
                plr.newDialogue()
                    .player("Let's see what you've got then.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "Sorry, I'm not interested.",
            {
                plr.newDialogue()
                    .player("Sorry, I'm not interested.")
                    .open()
            }
        ).open()
}
