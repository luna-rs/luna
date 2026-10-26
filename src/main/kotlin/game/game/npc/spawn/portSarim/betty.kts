package game.npc.spawn.portSarim

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Betty.
 */
val BETTY_ID = 583

/**
 * The name of the shop.
 */
val SHOP_NAME = "Betty's Magic Emporium."

/**
 * Betty's magic shop in Port Sarim.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Fire rune" x 5000
        "Water rune" x 5000
        "Air rune" x 5000
        "Earth rune" x 5000
        "Mind rune" x 5000
        "Body rune" x 5000
        "Chaos rune" x 250
        "Death rune" x 250
        "Eye of newt" x 300
        579 x 10 // Blue wizard hat.
    }

    open {
        npc2 += BETTY_ID
    }
}

/*
 * Handles Betty's first npc option dialogue.
 *
 * The player can either open the magic shop or decline it.
 */
npc1(BETTY_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, Expression.HAPPY, "Welcome to the magic emporium.")
        .options(
            "Can I see your wares?",
            {
                plr.newDialogue()
                    .player(Expression.QUIZZICAL, "Can I see your wares?")
                    .npc(targetNpc.id, Expression.HAPPY, "Yes.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "Sorry I'm not into magic.",
            {
                plr.newDialogue()
                    .player("Sorry I'm not into magic.")
                    .npc(targetNpc.id, Expression.HAPPY, "Send anyone my way who is.")
                    .open()
            }
        ).open()
}
