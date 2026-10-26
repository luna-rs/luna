package game.npc.spawn.alKharid

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Dommik.
 */
val DOMMIK_ID = 545

/**
 * The name of the shop.
 */
val SHOP_NAME = "Dommiks Crafting Store."

/*
 * Registers Dommik's crafting shop in Al-Kharid.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    currency = Currency.COINS

    sell {
        "Chisel" x 10
        "Ring mould" x 10
        "Necklace mould" x 10
        "Amulet mould" x 10
        "Needle" x 10
        "Thread" x 1000
        "Holy mould" x 10
        "Sickle mould" x 10
        "Tiara mould" x 10
    }

    open {
        npc2 += DOMMIK_ID
    }
}

/*
 * Handles Dommik's first npc option dialogue.
 *
 * The player can either decline or open the crafting shop.
 */
npc1(DOMMIK_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, Expression.HAPPY, "Would you like to buy some crafting equipment?")
        .options(
            "No thanks, I've got all the Crafting equipment I need.",
            {
                plr.newDialogue()
                    .player("No thanks; I've got all the Crafting equipment I need.")
                    .npc(targetNpc.id, Expression.HAPPY, "Okay. Fare well on your travels.")
                    .open()
            },
            "Let's see what you've got, then.",
            {
                plr.newDialogue()
                    .player("Let's see what you've got, then.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            }
        ).open()
}
