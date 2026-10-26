package game.npc.spawn.catherby

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

/**
 * The [Npc] id for Hickton.
 */
val HICKTON_ID = 575

/**
 * The name of the shop.
 */
val SHOP_NAME = "Hicktons Archery Emporium."

/*
 * Registers Hickton's archery shop in Catherby.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Bolts" x 300
        "Bronze arrow" x 1000
        "Iron arrow" x 100
        "Steel arrow" x 0
        "Mithril arrow" x 0
        "Adamant arrow" x 0
        "Rune arrow" x 0
        "Bronze brutal" x 0
        "Iron brutal" x 0
        "Steel brutal" x 0
        "Black brutal" x 0
        "Mithril brutal" x 0
        "Adamant brutal" x 0
        "Rune brutal" x 0
        "Bronze arrowtips" x 100
        "Iron arrowtips" x 100
        "Steel arrowtips" x 100
        "Mithril arrowtips" x 100
        "Adamant arrowtips" x 30
        "Rune arrowtips" x 10
        "Shortbow" x 10
        "Longbow" x 10
        "Crossbow" x 10
        "Oak shortbow" x 10
        "Oak longbow" x 10
        "Comp ogre bow" x 10
        "Studded body" x 10
        "Studded chaps" x 10
    }

    open {
        npc2 += HICKTON_ID
    }
}

/*
 * Handles Hickton's first npc option dialogue.
 *
 * The player can either open the archery shop or decline it.
 */
npc1(HICKTON_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, "Welcome to Hickton's Archery Store.", "Do you want to see my wares?")
        .options(
            "Yes, please.",
            {
                plr.newDialogue()
                    .player("Yes, please.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No, I prefer to bash things close up.",
            {
                plr.newDialogue()
                    .player("No, I prefer to bash things close up.")
                    .open()
            }
        ).open()
}
